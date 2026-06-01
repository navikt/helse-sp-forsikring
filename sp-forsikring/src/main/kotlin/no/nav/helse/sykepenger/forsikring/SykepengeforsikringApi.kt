package no.nav.helse.sykepenger.forsikring

import com.auth0.jwk.JwkProviderBuilder
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
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.URI
import java.util.UUID
import org.slf4j.event.Level

data class SykepengeforsikringRequest(
    val identitetsnummer: String
)

data class SykepengeforsikringResponse(
    // TODO: Legg til felter etter behov — speil forventer disse
    val forsikret: Boolean
)

data class ProblemResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
)

fun Application.sykepengeforsikringApi(
    sykepengeforsikringService: SykepengeforsikringService,
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
        jackson()
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
            post("/api/sykepengeforsikring") {
                val request = call.receive<SykepengeforsikringRequest>()
                require(request.identitetsnummer.matches(Regex("\\d{11}"))) {
                    "identitetsnummer må bestå av nøyaktig 11 siffer"
                }
                val callId = call.callId ?: UUID.randomUUID().toString()
                val resultat =
                    sykepengeforsikringService.hentSykepengeforsikring(
                        fødselsnummer = request.identitetsnummer,
                        callId = callId
                    )
                if (resultat == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ProblemResponse(
                            title = "Forsikring ikke funnet",
                            status = HttpStatusCode.NotFound.value,
                            detail = "Ingen sykepengeforsikring funnet for oppgitt identitetsnummer",
                            instance = call.request.uri,
                        ),
                    )
                } else {
                    call.respond(SykepengeforsikringResponse(forsikret = resultat.forsikret))
                }
            }
        }
    }
}
