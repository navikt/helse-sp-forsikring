package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.OppslagDao
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.mapTilRåNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.teamLogs
import no.nav.helse.sykepenger.forsikring.shared.util.withSession
import org.slf4j.event.Level
import java.net.URI
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

private val jsonMapper = ObjectMapper()

data class ForsikringsvurderingRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate,
)

data class ForsikringsvurderingResponse(
    val harForsikringMedDekningIVentetid: Boolean,
)

open class SpesialistForsikringResponse(
    val virkningsdato: LocalDate,
    val opphørsdato: LocalDate?,
    val dekningsgrad: Int,
    val dekningIVentetid: Boolean,
)

enum class SpesialistEkskluderingsårsakResponse {
    SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO,
    SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO,
    OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT,
    ALDRI_BETALT,
}

class SpesialistEkskludertForsikringResponse(
    virkningsdato: LocalDate,
    opphørsdato: LocalDate?,
    dekningsgrad: Int,
    dekningIVentetid: Boolean,
    val ekskluderingsårsak: SpesialistEkskluderingsårsakResponse,
) : SpesialistForsikringResponse(
        virkningsdato,
        opphørsdato,
        dekningsgrad,
        dekningIVentetid,
    )

data class SpesialistForsikringsvurderingResponse(
    val identitetsnummer: String,
    val harForsikring: Boolean,
    val forsikringskategori: String?,
    val dekning: SpesialistDekningResponse?,
    val ekskluderteForsikringer: List<SpesialistEkskludertForsikringResponse>,
    val gjeldendeForsikring: SpesialistForsikringResponse?,
)

data class SpesialistDekningResponse(
    val grad: Int,
    val fraDag: Int,
)

data class ProblemResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
)

fun Application.forsikringsvurderingApi(
    replikabaseDataSource: DataSource,
    spForsikringDataSource: DataSource,
    forsikringsvurderingRepository: ForsikringsvurderingRepository,
    clientId: String,
    issuerUrl: String,
    jwkProviderUri: String,
) {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        disableDefaultColors()
        logger = teamLogs
        level = Level.INFO
        callIdMdc("callId")
        filter { call -> call.request.path() !in setOf("/metrics", "/isalive", "/isready") }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ProblemResponse(
                    title = "Ugyldig forespørsel",
                    status = HttpStatusCode.BadRequest.value,
                    detail = cause.message ?: "Validering feilet",
                    instance = call.request.uri,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            teamLogs.error("Uventet feil ved kall til ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ProblemResponse(
                    title = "Intern serverfeil",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "En uventet feil oppstod",
                    instance = call.request.uri,
                ),
            )
        }
    }
    authentication {
        jwt("oidc") {
            verifier(
                jwkProvider = JwkProviderBuilder(URI(jwkProviderUri).toURL()).build(),
                issuer = issuerUrl,
            ) {
                withAudience(clientId)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }
    routing {
        authenticate("oidc") {
            get("/forsikringsvurderinger/{forsikringsvurderingId}") {
                val rawId = call.parameters["forsikringsvurderingId"]
                val id =
                    rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ProblemResponse(
                                title = "Ugyldig forsikringsvurderingId",
                                status = HttpStatusCode.BadRequest.value,
                                detail = "forsikringsvurderingId må være en gyldig UUID",
                                instance = call.request.uri,
                            ),
                        )
                loggInfo("Mottok kall til GET /forsikringsvurderinger/$id")

                val forsikringsvurdering =
                    forsikringsvurderingRepository.hent(ForsikringsvurderingId(id))
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ProblemResponse(
                                title = "Forsikringsvurdering ikke funnet",
                                status = HttpStatusCode.NotFound.value,
                                detail = "Fant ingen forsikringsvurdering med id $id",
                                instance = call.request.uri,
                            ),
                        )

                val oppslag =
                    spForsikringDataSource.withSession { session ->
                        session.transaction { transaction ->
                            OppslagDao().hentOppslag(
                                oppslagId = forsikringsvurdering.oppslagId,
                                session = transaction,
                            )
                        }
                    }

                val identitetsnummer = jsonMapper.readTree(forsikringsvurdering.behovJson)["fødselsnummer"].asText()

                val mapIdTilEkskluderingsårsak = forsikringsvurdering.ekskluderinger.associate { it.oppslagIfVedfrivt10Id to it.ekskluderingsårsak }
                val response =
                    SpesialistForsikringsvurderingResponse(
                        identitetsnummer = identitetsnummer,
                        harForsikring = forsikringsvurdering.harForsikring,
                        dekning =
                            forsikringsvurdering.dekning?.let { dekning ->
                                SpesialistDekningResponse(
                                    grad = dekning.grad,
                                    fraDag = if (dekning.iVentetid) 1 else 17,
                                )
                            },
                        forsikringskategori =
                            when (forsikringsvurdering.forsikringskategori) {
                                is KollektivForsikring -> "Kollektiv"
                                is NavKjøptForsikring -> "Individuell"
                                null -> null
                            },
                        ekskluderteForsikringer =
                            oppslag.navKjøpteForsikringer
                                .filter {
                                    mapIdTilEkskluderingsårsak.containsKey(it.id)
                                }.map {
                                    it to mapIdTilEkskluderingsårsak[it.id]!!
                                }.map {
                                    SpesialistEkskludertForsikringResponse(
                                        virkningsdato = it.first.virkningsdato,
                                        opphørsdato = it.first.opphørsdato,
                                        dekningsgrad = it.first.dekningGrad(),
                                        dekningIVentetid = it.first.dekningFraDag() == 1,
                                        ekskluderingsårsak =
                                            when (it.second) {
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO -> SpesialistEkskluderingsårsakResponse.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO -> SpesialistEkskluderingsårsakResponse.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT -> SpesialistEkskluderingsårsakResponse.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT -> SpesialistEkskluderingsårsakResponse.ALDRI_BETALT
                                            },
                                    )
                                },
                        gjeldendeForsikring =
                            oppslag.navKjøpteForsikringer
                                .filterNot {
                                    mapIdTilEkskluderingsårsak.containsKey(it.id)
                                }.minByOrNull { it.dekningFraDag() }
                                ?.let {
                                    SpesialistForsikringResponse(
                                        virkningsdato = it.virkningsdato,
                                        opphørsdato = it.opphørsdato,
                                        dekningsgrad = it.dekningGrad(),
                                        dekningIVentetid = it.dekningFraDag() == 1,
                                    )
                                },
                    )

                loggInfo("Svarer på GET /forsikringsvurderinger/$id", "response" to response)

                call.respond(response)
            }

            post("/api/forsikringsvurdering") {
                val request = call.receive<ForsikringsvurderingRequest>()
                require(request.identitetsnummer.matches(Regex("\\d{11}"))) {
                    "identitetsnummer må bestå av nøyaktig 11 siffer"
                }
                loggInfo("Mottok kall til POST /api/forsikringsvurdering", "request" to request)

                val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)

                val forsikringer =
                    replikabaseDao
                        .hentIfVedfrivt10Rader(
                            fødselsnummer = request.identitetsnummer,
                        ).map { it.mapTilRåNavKjøptForsikring(skjæringstidspunkt = request.skjæringstidspunkt) }

                val aktuelleForsikringer =
                    forsikringer.filter {
                        it.harVirkningPå(dato = request.skjæringstidspunkt) && !it.erOpphørtPå(dato = request.skjæringstidspunkt)
                    }

                val harDekningIVentetid = aktuelleForsikringer.any { it.dekningFraDag() == 1 }

                val response = ForsikringsvurderingResponse(harForsikringMedDekningIVentetid = harDekningIVentetid)

                loggInfo("Svarer på POST /api/forsikringsvurdering", "response" to response)

                call.respond(response)
            }
        }
    }
}
