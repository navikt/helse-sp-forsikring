package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.shared.testsupport.Infotrygdforsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.telling.infrastruktur.TellingDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class VedtakFattetTellerRiverTest {
    private val testRapid = TestRapid()
    private val objectMapper = jacksonObjectMapper()
    private val tellingDao = mockk<TellingDao>(relaxed = true)

    init {
        VedtakFattetTellerRiver(
            rapidsConnection = testRapid,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
            tellingDao = tellingDao,
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
        testRapid.reset()
    }

    @Test
    fun `lagrer utbetalinger fra vedtak_fattet for navkjøpt forsikring`() {
        val expectedMeldingId = UUID.randomUUID()
        val expectedVedtaksperiodeId = UUID.fromString("231585ae-93a9-46ea-8613-d5c73173d684")
        val expectedVedtakFattetTidspunktFraEvent = "2026-07-01T14:51:09.553706732"
        val expectedVedtakFattetTidspunkt =
            LocalDateTime
                .parse(expectedVedtakFattetTidspunktFraEvent)
                .atZone(ZoneId.of("Europe/Oslo"))
                .toInstant()
        val expectedDekningsgrad = 80
        val ventetidsBeløp = 100
        val navDagBeløp = listOf(1636, 1636)

        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )
        val lagretFødselsnummer = slot<String>()
        val lagretMeldingId = slot<UUID>()
        val lagretVedtaksperiodeId = slot<UUID>()
        val lagretVedtakFattetTidspunkt = slot<Instant>()
        val lagretDekningsgrad = slot<Int>()
        val lagretHarDekningIVentetid = slot<Boolean>()
        val lagretUtbetaltIVentetid = slot<Int>()
        val lagretUtbetaltUtenomVentetid = slot<Int>()
        val lagretJson = slot<String>()

        testRapid.sendTestMessage(
            event(
                forsikringsvurderingId = forsikringsvurderingId,
                vedtaksperiodeId = expectedVedtaksperiodeId,
                vedtakFattetTidspunkt = expectedVedtakFattetTidspunktFraEvent,
                ventetidsBeløp = ventetidsBeløp,
                navDagBeløp = navDagBeløp,
                meldingId = expectedMeldingId,
            ),
        )

        verify(exactly = 1) {
            tellingDao.lagre(
                id = capture(lagretMeldingId),
                fødselsnummer = capture(lagretFødselsnummer),
                vedtaksperiodeId = capture(lagretVedtaksperiodeId),
                vedtakFattetTidspunkt = capture(lagretVedtakFattetTidspunkt),
                dekningsgrad = capture(lagretDekningsgrad),
                harDekningIVentetid = capture(lagretHarDekningIVentetid),
                utbetaltIVentetid = capture(lagretUtbetaltIVentetid),
                utbetaltUtenomVentetid = capture(lagretUtbetaltUtenomVentetid),
                json = capture(lagretJson),
            )
        }
        assertEquals(expectedMeldingId, lagretMeldingId.captured)
        assertEquals(TESTFØDSELSNUMMER, lagretFødselsnummer.captured)
        assertEquals(expectedVedtaksperiodeId, lagretVedtaksperiodeId.captured)
        assertEquals(expectedVedtakFattetTidspunkt, lagretVedtakFattetTidspunkt.captured)
        assertEquals(expectedDekningsgrad, lagretDekningsgrad.captured)
        assertEquals(true, lagretHarDekningIVentetid.captured)
        assertEquals(ventetidsBeløp, lagretUtbetaltIVentetid.captured)
        assertEquals(navDagBeløp.sum(), lagretUtbetaltUtenomVentetid.captured)
        val lagretJsonNode = objectMapper.readTree(lagretJson.captured)
        assertEquals("vedtak_fattet", lagretJsonNode["@event_name"].asText())
        assertEquals(expectedMeldingId.toString(), lagretJsonNode["@id"].asText())
        assertEquals(TESTFØDSELSNUMMER, lagretJsonNode["fødselsnummer"].asText())
        assertEquals(forsikringsvurderingId.value.toString(), lagretJsonNode["forsikringsvurderingId"].asText())
        assertEquals(expectedVedtaksperiodeId.toString(), lagretJsonNode["vedtaksperiodeId"].asText())
        assertEquals(expectedVedtakFattetTidspunktFraEvent, lagretJsonNode["vedtakFattetTidspunkt"].asText())
        assertEquals(3, lagretJsonNode["utbetalingsdager"].size())
        assertEquals(ventetidsBeløp, lagretJsonNode["utbetalingsdager"][0]["beløpTilBruker"].asInt())
        assertEquals(navDagBeløp[0], lagretJsonNode["utbetalingsdager"][1]["beløpTilBruker"].asInt())
        assertEquals(navDagBeløp[1], lagretJsonNode["utbetalingsdager"][2]["beløpTilBruker"].asInt())
        confirmVerified(tellingDao)
    }

    @Test
    fun `lagrer ikke når vurderingen ikke har forsikring`() {
        val forsikringsvurderingId = lagreForsikringsvurdering()

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `lagrer ikke når vurderingen bare har kollektiv forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `kaster exception ved ukjent forsikringsvurderingId`() {
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(event(Forsikringsvurdering.Id.ny()))
        }

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ignorerer melding uten forsikringsvurderingId`() {
        testRapid.sendTestMessage(
            """
            {
              "@event_name": "vedtak_fattet",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$TESTFØDSELSNUMMER",
              "yrkesaktivitetstype": "SELVSTENDIG",
              "vedtaksperiodeId": "231585ae-93a9-46ea-8613-d5c73173d684",
              "vedtakFattetTidspunkt": "2026-07-01T14:51:09.553706732",
              "utbetalingsdager": []
            }
            """.trimIndent(),
        )

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun event(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        vedtaksperiodeId: UUID = UUID.fromString("231585ae-93a9-46ea-8613-d5c73173d684"),
        vedtakFattetTidspunkt: String = "2026-07-01T14:51:09.553706732",
        ventetidsBeløp: Int = 100,
        navDagBeløp: List<Int> = listOf(1636, 1636),
        meldingId: UUID = UUID.randomUUID(),
    ) = """
        {
          "@event_name": "vedtak_fattet",
          "fødselsnummer": "$TESTFØDSELSNUMMER",
          "aktørId": "2465656615746",
          "yrkesaktivitetstype": "SELVSTENDIG",
          "vedtaksperiodeId": "$vedtaksperiodeId",
          "behandlingId": "fa5e148c-3906-4311-8066-d2a776c5066c",
          "organisasjonsnummer": "SELVSTENDIG",
          "fom": "2026-04-01",
          "tom": "2026-04-26",
          "skjæringstidspunkt": "2026-04-06",
          "sykepengegrunnlag": 531709.0,
          "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
          "tags": ["Førstegangsbehandling", "Revurdering"],
          "forsikringsvurderingId": "${forsikringsvurderingId.value}",
          "utbetalingsdager": [
            {
              "dato": "2026-04-06",
              "type": "Ventetidsdag",
              "sykdomsgrad": 100,
              "begrunnelser": [],
              "dekningsgrad": 80,
              "beløpTilBruker": $ventetidsBeløp,
              "beløpTilArbeidsgiver": 0
            },
            {
              "dato": "2026-04-22",
              "type": "NavDag",
              "sykdomsgrad": 100,
              "begrunnelser": [],
              "dekningsgrad": 80,
              "beløpTilBruker": ${navDagBeløp[0]},
              "beløpTilArbeidsgiver": 0
            },
            {
              "dato": "2026-04-23",
              "type": "NavDag",
              "sykdomsgrad": 100,
              "begrunnelser": [],
              "dekningsgrad": 80,
              "beløpTilBruker": ${navDagBeløp[1]},
              "beløpTilArbeidsgiver": 0
            }
          ],
          "@id": "$meldingId"
        }
        """.trimIndent()
}
