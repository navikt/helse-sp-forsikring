package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
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
    val navn: String,
    val folketrygdlovenreferanse: Folketrygdlovenreferanse,
)

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    val ledd: Int?,
    val bokstav: Char?,
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
    navnSomSaksbehandlerSkjønnerOgSetterPrisPå: String,
    folketrygdlovenreferanse: Folketrygdlovenreferanse,
    val ekskluderingsbegrunnelse: Ekskluderingsbegrunnelse,
) : SpesialistForsikringResponse(
        virkningsdato,
        opphørsdato,
        dekningsgrad,
        dekningIVentetid,
        navn = navnSomSaksbehandlerSkjønnerOgSetterPrisPå,
        folketrygdlovenreferanse = folketrygdlovenreferanse,
    )

data class Ekskluderingsbegrunnelse(
    val forklaring: String,
    val folketrygdlovenreferanse: Folketrygdlovenreferanse?,
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
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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

                val mapIdTilEkskluderingsårsak =
                    forsikringsvurdering.ekskluderinger.associate { it.oppslagIfVedfrivt10Id to it.ekskluderingsårsak }
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
                                }.map { (forsikring, ekskluderingsårsak) ->
                                    SpesialistEkskludertForsikringResponse(
                                        virkningsdato = forsikring.virkningsdato,
                                        opphørsdato = forsikring.opphørsdato,
                                        dekningsgrad = forsikring.dekningGrad(),
                                        dekningIVentetid = forsikring.dekningFraDag() == 1,
                                        ekskluderingsårsak =
                                            when (ekskluderingsårsak) {
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO -> SpesialistEkskluderingsårsakResponse.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO -> SpesialistEkskluderingsårsakResponse.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT -> SpesialistEkskluderingsårsakResponse.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT -> SpesialistEkskluderingsårsakResponse.ALDRI_BETALT
                                            },
                                        ekskluderingsbegrunnelse =
                                            when (ekskluderingsårsak) {
                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO ->
                                                    Ekskluderingsbegrunnelse(
                                                        forklaring = "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
                                                        folketrygdlovenreferanse = null,
                                                    )

                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO ->
                                                    Ekskluderingsbegrunnelse(
                                                        forklaring = "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
                                                        folketrygdlovenreferanse = null,
                                                    )

                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT ->
                                                    Ekskluderingsbegrunnelse(
                                                        forklaring = "Forsikringen opphørte før skjæringstidspunktet",
                                                        folketrygdlovenreferanse =
                                                            Folketrygdlovenreferanse(
                                                                kapittel = 8,
                                                                paragrafIKapittel = 37,
                                                                ledd = null,
                                                                bokstav = null,
                                                            ),
                                                    )

                                                AbstractNavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT ->
                                                    Ekskluderingsbegrunnelse(
                                                        forklaring = "Forsikringen er innvilget, men ikke betalt ennå",
                                                        folketrygdlovenreferanse = null,
                                                    )
                                            },
                                        navnSomSaksbehandlerSkjønnerOgSetterPrisPå = forsikring.tilNavnSomSaksbehandlerSkjønnerOgSetterPrisPå(),
                                        folketrygdlovenreferanse = forsikring.tilRettsreferanse(),
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
                                        navn = it.tilNavnSomSaksbehandlerSkjønnerOgSetterPrisPå(),
                                        folketrygdlovenreferanse = it.tilRettsreferanse(),
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

private fun AbstractNavKjøptForsikring.tilNavnSomSaksbehandlerSkjønnerOgSetterPrisPå(): String =
    when (type) {
        AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> "80 % fra 1. dag (Nav-kjøpt)"
        AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> "100 % fra 17. dag (Nav-kjøpt)"
        AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> "100 % fra 1. dag (Nav-kjøpt)"
        AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> "100 % fra 1. dag (Nav-kjøpt)"
        AbstractNavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1 -> "100 % fra 1. dag (Nav-kjøpt)"
    }

private fun AbstractNavKjøptForsikring.tilRettsreferanse(): Folketrygdlovenreferanse =
    when (type) {
        AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 ->
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'a',
            )

        AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 ->
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'b',
            )

        AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 ->
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'c',
            )

        AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 ->
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'c',
            )

        AbstractNavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1 ->
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 39,
                ledd = 1,
                bokstav = null,
            )
    }
