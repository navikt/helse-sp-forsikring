package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.ServerSocket
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

private const val CLIENT_ID = "sp-forsikring-junit"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForsikringsvurderingApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val replikadatabase = TestcontainersReplikadatabase()

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                replikabaseDataSource = replikadatabase.dataSource,
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString()
            )
        }.start(wait = false)

    @BeforeEach
    fun reset() {
        replikadatabase.reset()
    }

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
        replikadatabase.shutdown()
    }

    @Test
    fun `returnerer forsikret=true med dekning når aktiv betalt forsikring finnes`() {
        replikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '3') // 100% fra dag 1
        replikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
            IF12_FOM = 20260101,
            IF12_TOM = 20261231,
        )

        val (statusCode, body) = postForsikringsvurdering(bearerToken())

        assertEquals(200, statusCode)
        val response = objectMapper.readTree(body)
        assertTrue(response["forsikret"].asBoolean())
        assertEquals(100, response["dekning"]["grad"].asInt())
        assertEquals(1, response["dekning"]["fraDag"].asInt())
        assertTrue(response["erBetaltForSkjæringstidspunkt"].asBoolean())
    }

    @Test
    fun `returnerer forsikret=false når ingen forsikring finnes`() {
        val (statusCode, body) = postForsikringsvurdering(bearerToken())

        assertEquals(200, statusCode)
        val response = objectMapper.readTree(body)
        assertFalse(response["forsikret"].asBoolean())
        assertTrue(response["dekning"].isNull)
        assertFalse(response["erBetaltForSkjæringstidspunkt"].asBoolean())
    }

    @Test
    fun `returnerer erBetaltForSkjæringstidspunkt=false når forsikring er betalt men ikke for skjæringstidspunktet`() {
        replikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '1')
        replikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20250101, // betalt en gang, men perioden dekker ikke skjæringstidspunktet
            IF12_FOM = 20250101,
            IF12_TOM = 20251231,
        )

        val (statusCode, body) = postForsikringsvurdering(bearerToken())

        assertEquals(200, statusCode)
        val response = objectMapper.readTree(body)
        assertTrue(response["forsikret"].asBoolean())
        assertFalse(response["erBetaltForSkjæringstidspunkt"].asBoolean())
    }

    @Test
    fun `returnerer 400 når identitetsnummer ikke er 11 siffer`() {
        val (statusCode, body) = postForsikringsvurdering(bearerToken(), identitetsnummer = "1234")

        assertEquals(400, statusCode)
        assertTrue(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
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

    private fun postForsikringsvurdering(
        token: String?,
        identitetsnummer: String = "01020312345",
        skjæringstidspunkt: String = "2026-01-01"
    ): Pair<Int, String> =
        Request
            .post("$serverUrl/api/forsikringsvurdering")
            .bodyString(
                """{ "identitetsnummer": "$identitetsnummer", "skjæringstidspunkt": "$skjæringstidspunkt" }""",
                ContentType.APPLICATION_JSON
            )
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}

