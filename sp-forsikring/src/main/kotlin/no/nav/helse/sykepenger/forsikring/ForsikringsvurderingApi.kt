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
import java.net.URI
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource
import no.nav.helse.sykepenger.forsikring.kalkulator.Dekning
import no.nav.helse.sykepenger.forsikring.kalkulator.ForsikringsvurderingKalkulator
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.replikabase.tilRåForsikringer
import org.slf4j.event.Level

data class ForsikringsvurderingRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate,
)

data class ForsikringsvurderingResponse(
    val forsikret: Boolean,
    val dekning: Dekning?,
    val erBetaltForSkjæringstidspunkt: Boolean,
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
    val replikabaseDao = ReplikabaseDao(replikabaseDataSource)
    val kalkulator = ForsikringsvurderingKalkulator()

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

                val rawRader = replikabaseDao.hentIfVedfrivt10Rader(request.identitetsnummer)
                val råForsikringer = rawRader.tilRåForsikringer()
                val resultat = kalkulator.kalkuler(råForsikringer, request.skjæringstidspunkt)

                val erBetaltForSkjæringstidspunkt = resultat.inkluderteRåForsikringer
                    .any { it.erBetaltForSkjæringstidspunkt(request.skjæringstidspunkt) }

                call.respond(
                    ForsikringsvurderingResponse(
                        forsikret = resultat.forsikret,
                        dekning = resultat.dekning,
                        erBetaltForSkjæringstidspunkt = erBetaltForSkjæringstidspunkt,
                    )
                )
            }
        }
    }
}

