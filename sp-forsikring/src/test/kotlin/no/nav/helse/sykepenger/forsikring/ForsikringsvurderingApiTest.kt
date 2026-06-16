package no.nav.helse.sykepenger.forsikring

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.ServerSocket

private const val CLIENT_ID = "sp-forsikring-junit"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForsikringsvurderingApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString()
            )
        }.start(wait = false)

    @BeforeEach
    fun reset() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
    }

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når ingen forsikringer finnes i replikabasen`() {
        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @ParameterizedTest
    @ValueSource(chars = ['1', '3', '4', '5'])
    fun `returnerer harForsikringMedDekningIVentetid true når bruker har aktiv dag-1-forsikring`(type: Char) {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = type,
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":true")) { "Forventet harForsikringMedDekningIVentetid=true, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når bruker kun har forsikring fra dag 17`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når forsikringen er opphørt på skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
            IF10_FORSTOM = 20251231,
        )

        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når virkningsdato er etter skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260102,
        )

        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer 400 når identitetsnummer ikke er 11 siffer`() {
        val (statusCode, body) = postForsikringsvurdering(
            identitetsnummer = "1234",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken()
        )

        assertEquals(400, statusCode)
        assertTrue(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
    }

    @Test
    fun `returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = null
        )

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token med feil audience`() {
        val (statusCode, _) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken(audience = "feil-audience")
        )

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token fra feil issuer`() {
        val (statusCode, _) = postForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = "2026-01-01",
            token = bearerToken(issuerId = "feil-issuer")
        )

        assertEquals(401, statusCode)
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun postForsikringsvurdering(
        identitetsnummer: String,
        skjæringstidspunkt: String,
        token: String?
    ): Pair<Int, String> {
        return Request
            .post("$serverUrl/api/forsikringsvurdering")
            .bodyString(
                """{ "identitetsnummer": "$identitetsnummer", "skjæringstidspunkt": "$`skjæringstidspunkt`" }""",
                ContentType.APPLICATION_JSON
            )
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
    }
}
