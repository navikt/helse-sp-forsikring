package no.nav.helse.sykepenger.forsikring.api

import com.auth0.jwk.JwkProviderBuilder
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
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
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.EndringssjekkService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.kafka.EndretForsikringsvurderingPubliserer
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import no.nav.sykepenger.libs.logging.loggError
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import java.net.URI
import java.util.*
import javax.sql.DataSource

fun Application.api(
    spForsikringDataSource: DataSource,
    forsikringsvurderingService: ForsikringsvurderingService,
    clientId: String,
    issuerUrl: String,
    jwkProviderUri: String,
    subsumsjonspubliserer: Subsumsjonspubliserer,
    endretForsikringsvurderingPubliserer: EndretForsikringsvurderingPubliserer,
    populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
) {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        disableDefaultColors()
        callIdMdc("callId")
        filter { call -> call.request.path() !in setOf("/metrics", "/isalive", "/isready") }
    }
    install(ContentNegotiation) {
        jackson {
            accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
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
            loggError("Uventet feil ved kall til ${call.request.uri}", cause)
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
        jwt("oidc-m2m") {
            verifier(
                jwkProvider = JwkProviderBuilder(URI(jwkProviderUri).toURL()).build(),
                issuer = issuerUrl,
            ) {
                withAudience(clientId)
            }
            validate { credentials ->
                // Entra ID setter idtyp=app kun for maskin-til-maskin-token. Token som er
                // utstedt på vegne av en innlogget bruker mangler claimet, og avvises her.
                if (credentials.payload.getClaim("idtyp").asString() != "app") {
                    return@validate null
                }
                JWTPrincipal(credentials.payload)
            }
        }
    }
    routing {
        authenticate("oidc-m2m") {
            flexApi(forsikringsvurderingService)
            hentForsikringsvurderingApi(spForsikringDataSource = spForsikringDataSource)
        }
        authenticate("oidc") {
            endringssjekkApi(
                endringssjekkService =
                    EndringssjekkService(
                        spForsikringDataSource = spForsikringDataSource,
                        forsikringsvurderingService = forsikringsvurderingService,
                        subsumsjonspubliserer = subsumsjonspubliserer,
                        endretForsikringsvurderingPubliserer = endretForsikringsvurderingPubliserer,
                    ),
                populasjonstilgangskontrollProvider = populasjonstilgangskontrollProvider,
            )
            utbetalingsstatistikkApi(spForsikringDataSource)
        }
    }
}
