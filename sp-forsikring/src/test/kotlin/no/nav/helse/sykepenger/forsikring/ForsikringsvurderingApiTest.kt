package no.nav.helse.sykepenger.forsikring

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.ServerSocket
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

private const val CLIENT_ID = "sp-forsikring-junit"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForsikringsvurderingApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)
    private var mocketResultat: ForsikringsvurderingResultat? = null
    private val forsikringsvurderingService = object : ForsikringsvurderingService {
        override fun hentForsikringsvurdering(fødselsnummer: String, callId: String): ForsikringsvurderingResultat? {
            return mocketResultat
        }
    }

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                forsikringsvurderingService = forsikringsvurderingService,
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString()
            )
        }.start(wait = false)

    @BeforeEach
    fun reset() {
        mocketResultat = null
    }

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
    }

    @Test
    fun `returnerer 200 med svar når forsikring finnes`() {
        mocketResultat = ForsikringsvurderingResultat(forsikret = true)

        val (statusCode, _) = postForsikringsvurdering(bearerToken())

        assertEquals(200, statusCode)
    }

    @Test
    fun `returnerer 404 når forsikring ikke finnes`() {
        val (statusCode, body) = postForsikringsvurdering(bearerToken())

        assertEquals(404, statusCode)
        assert(body.contains("\"status\":404")) { "Forventet ProblemDetail-body med status 404, fikk: $body" }
    }

    @Test
    fun `returnerer 400 når identitetsnummer ikke er 11 siffer`() {
        val (statusCode, body) = postForsikringsvurdering(bearerToken(), identitetsnummer = "1234")

        assertEquals(400, statusCode)
        assert(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
    }

    @Test
    fun `returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = postForsikringsvurdering(token = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token med feil audience`() {
        val (statusCode, _) = postForsikringsvurdering(bearerToken(audience = "feil-audience"))

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token fra feil issuer`() {
        val (statusCode, _) = postForsikringsvurdering(bearerToken(issuerId = "feil-issuer"))

        assertEquals(401, statusCode)
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun postForsikringsvurdering(token: String?, identitetsnummer: String = "12345678901"): Pair<Int, String> =
        Request
            .post("$serverUrl/api/forsikringsvurdering")
            .bodyString("""{ "identitetsnummer": "$identitetsnummer" }""", ContentType.APPLICATION_JSON)
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
