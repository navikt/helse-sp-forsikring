package no.nav.helse.sykepenger.forsikring

import com.auth0.jwk.JwkProviderBuilder
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
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.replikabase.mapTilRåNavKjøptForsikring
import org.slf4j.event.Level
import java.net.URI
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

data class ForsikringsvurderingRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate
)

data class ForsikringsvurderingResponse(
    val harForsikringMedDekningIVentetid: Boolean
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
    clientId: String,
    issuerUrl: String,
    jwkProviderUri: String
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
                issuer = issuerUrl
            ) {
                withAudience(clientId)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }
    routing {
        authenticate("oidc") {
            post("/api/forsikringsvurdering") {
                val request = call.receive<ForsikringsvurderingRequest>()
                require(request.identitetsnummer.matches(Regex("\\d{11}"))) {
                    "identitetsnummer må bestå av nøyaktig 11 siffer"
                }
                val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)

                val forsikringer = replikabaseDao.hentIfVedfrivt10Rader(
                    fødselsnummer = request.identitetsnummer
                ).map { it.mapTilRåNavKjøptForsikring(skjæringstidspunkt = request.skjæringstidspunkt) }

                val aktuelleForsikringer = forsikringer.filter {
                    it.harVirkningPå(dato = request.skjæringstidspunkt) && !it.erOpphørtPå(dato = request.skjæringstidspunkt)
                }

                val harDekningIVentetid = aktuelleForsikringer.any { it.dekningFraDag() == 1 }

                call.respond(ForsikringsvurderingResponse(harForsikringMedDekningIVentetid = harDekningIVentetid))
            }
        }
    }
}
