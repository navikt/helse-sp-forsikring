package no.nav.helse.sykepenger.forsikring

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.ServerSocket
import java.time.LocalDate
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
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

private const val CLIENT_ID = "sp-forsikring-junit"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForsikringsvurderingApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val forsikringsvurderingRepository = ForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource)
    private val replikabaseDao = ReplikabaseDao(TestcontainersReplikadatabase.dataSource)
    private val oppslagService = OppslagService(replikabaseDao)
    private val forsikringsvurderingService = ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
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

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med korrekt forsikringsvurdering med dekning`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId = opprettForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
        )

        val (statusCode, body) = getForsikringsvurdering(
            forsikringsvurderingId = forsikringsvurderingId,
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"identitetsnummer\":\"12345678901\"")) { "Forventet identitetsnummer, fikk: $body" }
        assertTrue(body.contains("\"harForsikring\":true")) { "Forventet harForsikring=true, fikk: $body" }
        assertTrue(body.contains("\"grad\":80")) { "Forventet grad=80, fikk: $body" }
        assertTrue(body.contains("\"fraDag\":1")) { "Forventet fraDag=1, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med harForsikring false og ingen dekning`() {
        val forsikringsvurderingId = opprettForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
        )

        val (statusCode, body) = getForsikringsvurdering(
            forsikringsvurderingId = forsikringsvurderingId,
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikring\":false")) { "Forventet harForsikring=false, fikk: $body" }
        assertTrue(body.contains("\"dekning\":null")) { "Forventet dekning=null, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med fraDag 17 for dag-17-forsikring`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId = opprettForsikringsvurdering(
            identitetsnummer = "12345678901",
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
        )

        val (statusCode, body) = getForsikringsvurdering(
            forsikringsvurderingId = forsikringsvurderingId,
            token = bearerToken()
        )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"fraDag\":17")) { "Forventet fraDag=17, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 404 når id ikke finnes`() {
        val (statusCode, body) = getForsikringsvurdering(
            forsikringsvurderingId = java.util.UUID.randomUUID().toString(),
            token = bearerToken()
        )

        assertEquals(404, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"status\":404")) { "Forventet ProblemDetail-body med status 404, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = getForsikringsvurdering(
            forsikringsvurderingId = java.util.UUID.randomUUID().toString(),
            token = null
        )

        assertEquals(401, statusCode)
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun opprettForsikringsvurdering(
        identitetsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): String {
        val behovJson = """{"fødselsnummer":"$identitetsnummer","@behov":["Forsikringsvurdering"]}"""
        return TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
            forsikringsvurderingService.gjørVurdering(
                session = transaction,
                behovJson = behovJson,
                skjæringstidspunkt = skjæringstidspunkt,
                fødselsnummer = identitetsnummer,
                spesielleYrkesgrupper = emptySet(),
                yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
            ).id.value.toString()
        }
    }

    private fun getForsikringsvurdering(
        forsikringsvurderingId: String,
        token: String?
    ): Pair<Int, String> {
        return Request
            .get("$serverUrl/forsikringsvurderinger/$forsikringsvurderingId")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
    }

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
