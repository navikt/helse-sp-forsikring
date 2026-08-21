package no.nav.helse.sykepenger.forsikring.api

import com.auth0.jwk.JwkProviderBuilder
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
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.shared.logging.teamLogs
import org.slf4j.event.Level
import java.net.URI
import java.util.*
import javax.sql.DataSource

fun Application.forsikringsvurderingApi(
    spForsikringDataSource: DataSource,
    forsikringsvurderingService: ForsikringsvurderingService,
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
            flexApi(forsikringsvurderingService)
            spesialistApi(spForsikringDataSource)
            utbetalingsstatistikkApi(spForsikringDataSource)
        }
    }
}
