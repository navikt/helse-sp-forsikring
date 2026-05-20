package no.nav.helse.sykepenger.forsikring

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
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
class SykepengeforsikringApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)
    private val sykepengeforsikringService = mockk<SykepengeforsikringService>()

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            sykepengeforsikringApi(
                sykepengeforsikringService = sykepengeforsikringService,
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString()
            )
        }.start(wait = false)

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
        mockOAuth2Server.shutdown()
    }

    @BeforeEach
    fun resetMocks() {
        clearAllMocks()
    }

    @Test
    fun `returnerer 200 med svar når forsikring finnes`() {
        every { sykepengeforsikringService.hentSykepengeforsikring(any(), any()) } returns SykepengeforsikringResultat(forsikret = true)

        val (statusCode, _) = postSykepengeforsikring(bearerToken())

        assertEquals(200, statusCode)
    }

    @Test
    fun `returnerer 404 når forsikring ikke finnes`() {
        every { sykepengeforsikringService.hentSykepengeforsikring(any(), any()) } returns null

        val (statusCode, _) = postSykepengeforsikring(bearerToken())

        assertEquals(404, statusCode)
    }

    @Test
    fun `returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = postSykepengeforsikring(token = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token med feil audience`() {
        val (statusCode, _) = postSykepengeforsikring(bearerToken(audience = "feil-audience"))

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token fra feil issuer`() {
        val (statusCode, _) = postSykepengeforsikring(bearerToken(issuerId = "feil-issuer"))

        assertEquals(401, statusCode)
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun postSykepengeforsikring(token: String?): Pair<Int, String> =
        Request
            .post("$serverUrl/api/sykepengeforsikring")
            .bodyString("""{ "identitetsnummer": "12345678901" }""", ContentType.APPLICATION_JSON)
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
