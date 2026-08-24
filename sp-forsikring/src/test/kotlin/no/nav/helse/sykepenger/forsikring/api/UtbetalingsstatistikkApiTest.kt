package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.domain.Forsikringstype
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.net.ServerSocket
import java.time.Instant
import java.util.*
import kotlin.test.assertNotNull

private const val CLIENT_ID = "sp-forsikring-junit"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UtbetalingsstatistikkApiTest {
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                forsikringsvurderingService = ForsikringsvurderingService(TestcontainersReplikadatabase.dataSource),
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString(),
            )
        }.start(wait = false)

    @BeforeEach
    fun reset() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `summerer utbetalinger per forsikringstype for perioden`() {
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-02T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("100" to "200"),
            NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1 to ("10" to "20"),
        )
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-03T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("1" to "2"),
        )

        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = objectMapper.readTree(body)
        assertEquals("2026-07-02", json["fom"].asText())
        assertEquals("2026-07-03", json["tom"].asText())

        val perForsikringstype = json["perForsikringstype"]
        assertEquals(7, perForsikringstype.size())

        val kollektiv = assertNotNull(perForsikringstype.finn(KollektivForsikring.JORDBRUKER))
        assertEquals("KOLLEKTIV", kollektiv["kategori"].asText())
        assertBeløp("101", kollektiv["utbetaltIVentetid"])
        assertBeløp("202", kollektiv["utbetaltUtenomVentetid"])
        assertBeløp("303", kollektiv["totalt"])

        val navKjøpt = assertNotNull(perForsikringstype.finn(NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1))
        assertEquals("NAV_KJØPT", navKjøpt["kategori"].asText())
        assertBeløp("10", navKjøpt["utbetaltIVentetid"])
        assertBeløp("20", navKjøpt["utbetaltUtenomVentetid"])
        assertBeløp("30", navKjøpt["totalt"])

        assertNullsummer(perForsikringstype, unntatt = setOf(KollektivForsikring.JORDBRUKER, NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1))
    }

    @Test
    fun `svarer med beløp med to desimaler`() {
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-02T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("0.10" to "1200.55"),
        )
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-03T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("0.15" to "0.45"),
        )

        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val perForsikringstype = objectMapper.readTree(body)["perForsikringstype"]
        val kollektiv = assertNotNull(perForsikringstype.finn(KollektivForsikring.JORDBRUKER))
        assertBeløp("0.25", kollektiv["utbetaltIVentetid"])
        assertBeløp("1201.00", kollektiv["utbetaltUtenomVentetid"])
        assertBeløp("1201.25", kollektiv["totalt"])
        assertNullsummer(perForsikringstype, unntatt = setOf(KollektivForsikring.JORDBRUKER))
    }

    @Test
    fun `lister ut alle sju forsikringstypene med riktig kategori`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val perForsikringstype = objectMapper.readTree(body)["perForsikringstype"]

        assertEquals(
            (KollektivForsikring.entries.map { it.name } + NavKjøptForsikringType.entries.map { it.name }),
            perForsikringstype.map { it["forsikringstype"].asText() },
        )
        assertEquals(
            KollektivForsikring.entries.map { "KOLLEKTIV" } + NavKjøptForsikringType.entries.map { "NAV_KJØPT" },
            perForsikringstype.map { it["kategori"].asText() },
        )
    }

    @Test
    fun `tar ikke med utbetalinger for vedtak fattet utenfor perioden`() {
        lagreUtbetaling(
            // 2026-07-01T23:59:59 norsk tid, altså dagen før fom
            vedtakFattetTidspunkt = Instant.parse("2026-07-01T21:59:59Z"),
            KollektivForsikring.JORDBRUKER to ("500" to "500"),
        )
        lagreUtbetaling(
            // 2026-07-04T00:00:00 norsk tid, altså dagen etter tom
            vedtakFattetTidspunkt = Instant.parse("2026-07-03T22:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("700" to "700"),
        )
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-01T22:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("1" to "2"),
        )

        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val perForsikringstype = objectMapper.readTree(body)["perForsikringstype"]
        assertEquals(7, perForsikringstype.size())
        val kollektiv = assertNotNull(perForsikringstype.finn(KollektivForsikring.JORDBRUKER))
        assertBeløp("1", kollektiv["utbetaltIVentetid"])
        assertBeløp("2", kollektiv["utbetaltUtenomVentetid"])
        assertBeløp("3", kollektiv["totalt"])
        assertNullsummer(perForsikringstype, unntatt = setOf(KollektivForsikring.JORDBRUKER))
    }

    @Test
    fun `returnerer alle forsikringstypene med nullsummer når det ikke finnes utbetalinger i perioden`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val perForsikringstype = objectMapper.readTree(body)["perForsikringstype"]
        assertEquals(7, perForsikringstype.size())
        assertNullsummer(perForsikringstype, unntatt = emptySet())
    }

    @Test
    fun `summerer én enkelt dag når fom og tom er like`() {
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-02T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("10" to "20"),
        )
        lagreUtbetaling(
            vedtakFattetTidspunkt = Instant.parse("2026-07-03T09:00:00Z"),
            KollektivForsikring.JORDBRUKER to ("900" to "900"),
        )

        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-02", token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val perForsikringstype = objectMapper.readTree(body)["perForsikringstype"]
        assertEquals(7, perForsikringstype.size())
        val kollektiv = assertNotNull(perForsikringstype.finn(KollektivForsikring.JORDBRUKER))
        assertBeløp("30", kollektiv["totalt"])
        assertNullsummer(perForsikringstype, unntatt = setOf(KollektivForsikring.JORDBRUKER))
    }

    @Test
    fun `svarer 400 når fom er etter tom`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-03", tom = "2026-07-02", token = bearerToken())

        assertEquals(400, statusCode) { "Body was: $body" }
        assertEquals("Ugyldig periode", objectMapper.readTree(body)["title"].asText())
    }

    @Test
    fun `svarer 400 når fom mangler`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = null, tom = "2026-07-02", token = bearerToken())

        assertEquals(400, statusCode) { "Body was: $body" }
        assertEquals("Ugyldig fom", objectMapper.readTree(body)["title"].asText())
    }

    @Test
    fun `svarer 400 når tom mangler`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = "2026-07-02", tom = null, token = bearerToken())

        assertEquals(400, statusCode) { "Body was: $body" }
        assertEquals("Ugyldig tom", objectMapper.readTree(body)["title"].asText())
    }

    @Test
    fun `svarer 400 når datoene ikke er på formatet yyyy-MM-dd`() {
        val (statusCode, body) = hentUtbetalteSummer(fom = "02.07.2026", tom = "2026-07-03", token = bearerToken())

        assertEquals(400, statusCode) { "Body was: $body" }
        assertEquals("Ugyldig fom", objectMapper.readTree(body)["title"].asText())
    }

    @Test
    fun `svarer 401 uten token`() {
        val (statusCode, _) = hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `svarer 401 med token for feil audience`() {
        val (statusCode, _) =
            hentUtbetalteSummer(fom = "2026-07-02", tom = "2026-07-03", token = bearerToken(audience = "en-annen-app"))

        assertEquals(401, statusCode)
    }

    private fun assertNullsummer(
        perForsikringstype: JsonNode,
        unntatt: Set<Forsikringstype>,
    ) {
        val unntattNavn = unntatt.map { it.navn() }.toSet()
        perForsikringstype
            .filterNot { it["forsikringstype"].asText() in unntattNavn }
            .forEach { rad ->
                val navn = rad["forsikringstype"].asText()
                assertBeløp("0", rad["utbetaltIVentetid"], "Forventet 0 i ventetid for $navn")
                assertBeløp("0", rad["utbetaltUtenomVentetid"], "Forventet 0 utenom ventetid for $navn")
                assertBeløp("0", rad["totalt"], "Forventet 0 totalt for $navn")
            }
    }

    private fun assertBeløp(
        forventet: String,
        faktisk: JsonNode,
        beskrivelse: String = "Feil beløp",
    ) = assertEquals(
        0,
        BigDecimal(forventet).compareTo(faktisk.decimalValue()),
        "$beskrivelse: forventet $forventet, men var ${faktisk.asText()}",
    )

    private fun Forsikringstype.navn(): String =
        when (this) {
            is KollektivForsikring -> name
            is NavKjøptForsikringType -> name
        }

    private fun JsonNode.finn(forsikringstype: Forsikringstype): JsonNode? = singleOrNull { it["forsikringstype"].asText() == forsikringstype.navn() }

    private fun lagreUtbetaling(
        vedtakFattetTidspunkt: Instant,
        vararg utbetalinger: Pair<Forsikringstype, Pair<String, String>>,
    ) {
        val meldingId = UUID.randomUUID()
        TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId, vedtakFattetTidspunkt)
            val dao = UtbetalingPerForsikringstypeDao(transaction)
            utbetalinger.forEach { (forsikringstype, beløp) ->
                val (iVentetid, utenomVentetid) = beløp
                dao.insert(
                    vedtakFattetMeldingId = meldingId,
                    forsikringstype = forsikringstype,
                    utbetaltIVentetid = BigDecimal(iVentetid),
                    utbetaltUtenomVentetid = BigDecimal(utenomVentetid),
                )
            }
        }
    }

    private fun lagreMelding(
        transaction: TransactionalSession,
        meldingId: UUID,
        vedtakFattetTidspunkt: Instant,
    ) {
        VedtakFattetMeldingDao(transaction).insert(
            id = meldingId,
            forsikringsvurderingId = null,
            identitetsnummer = lagIdentitetsnummer(),
            behandlingId = UUID.randomUUID(),
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            json = """{"@event_name":"vedtak_fattet"}""",
        )
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID,
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun hentUtbetalteSummer(
        fom: String?,
        tom: String?,
        token: String?,
    ): Pair<Int, String> {
        val query =
            listOfNotNull(
                fom?.let { "fom=${java.net.URLEncoder.encode(it, Charsets.UTF_8)}" },
                tom?.let { "tom=${java.net.URLEncoder.encode(it, Charsets.UTF_8)}" },
            ).joinToString("&")
        return Request
            .get("$serverUrl/api/utbetalinger/utbetaltesummer?$query")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
    }
}
