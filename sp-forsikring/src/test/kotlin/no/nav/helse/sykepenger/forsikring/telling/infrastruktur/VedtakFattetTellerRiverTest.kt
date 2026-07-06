package no.nav.helse.sykepenger.forsikring.telling.infrastruktur

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.FakeForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import org.junit.jupiter.api.assertThrows

class VedtakFattetTellerRiverTest {
    private val testRapid = TestRapid()
    private val objectMapper = jacksonObjectMapper()
    private val fødselsnummer = "26810697848"

    private val forsikringsvurderingRepository = FakeForsikringsvurderingRepository()
    private val tellingDao = mockk<TellingDao>(relaxed = true)

    init {
        VedtakFattetTellerRiver(
            rapidsConnection = testRapid,
            forsikringsvurderingRepository = forsikringsvurderingRepository,
            tellingDao = tellingDao,
        )
    }

    @Test
    fun `lagrer utbetalinger fra vedtak_fattet for navkjøpt forsikring`() {
        val expectedMeldingId = UUID.randomUUID()
        val expectedVedtaksperiodeId = UUID.fromString("231585ae-93a9-46ea-8613-d5c73173d684")
        val expectedVedtakFattetTidspunktFraEvent = "2026-07-01T14:51:09.553706732"
        val expectedVedtakFattetTidspunkt = LocalDateTime.parse(expectedVedtakFattetTidspunktFraEvent)
            .atZone(ZoneId.of("Europe/Oslo"))
            .toInstant()
        val expectedDekningsgrad = 80
        val expectedHarDekningIVentetid = true
        val ventetidsBeløp = 100
        val navDagBeløp = listOf(1636, 1636)
        val expectedUtbetaltIVentetid = ventetidsBeløp
        val expectedUtbetaltUtenomVentetid = navDagBeløp.sum()

        val forsikringsvurderingId = seedForsikringsvurdering(
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(iVentetid = expectedHarDekningIVentetid, grad = expectedDekningsgrad),
            forsikringskategori = null
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
            )
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
        assertEquals(fødselsnummer, lagretFødselsnummer.captured)
        assertEquals(expectedVedtaksperiodeId, lagretVedtaksperiodeId.captured)
        assertEquals(expectedVedtakFattetTidspunkt, lagretVedtakFattetTidspunkt.captured)
        assertEquals(expectedDekningsgrad, lagretDekningsgrad.captured)
        assertEquals(expectedHarDekningIVentetid, lagretHarDekningIVentetid.captured)
        assertEquals(expectedUtbetaltIVentetid, lagretUtbetaltIVentetid.captured)
        assertEquals(expectedUtbetaltUtenomVentetid, lagretUtbetaltUtenomVentetid.captured)
        val lagretJsonNode = objectMapper.readTree(lagretJson.captured)
        assertEquals("vedtak_fattet", lagretJsonNode["@event_name"].asText())
        assertEquals(expectedMeldingId.toString(), lagretJsonNode["@id"].asText())
        assertEquals(fødselsnummer, lagretJsonNode["fødselsnummer"].asText())
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
        val forsikringsvurderingId = seedForsikringsvurdering(
            harForsikring = false,
            dekning = null,
            forsikringskategori = null
        )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `lagrer ikke når vurderingen mangler dekning`() {
        val forsikringsvurderingId = seedForsikringsvurdering(
            harForsikring = true,
            dekning = null,
            forsikringskategori = null
        )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `lagrer ikke når vurderingen er kollektiv forsikring`() {
        val forsikringsvurderingId = seedForsikringsvurdering(
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(iVentetid = true, grad = 100),
            forsikringskategori = Forsikringskategori.KollektivForsikring
        )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `kaster exception ved ukjent forsikringsvurderingId`() {
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(event(ForsikringsvurderingId.ny()))
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
                  "fødselsnummer": "$fødselsnummer",
                  "yrkesaktivitetstype": "SELVSTENDIG",
                  "vedtaksperiodeId": "231585ae-93a9-46ea-8613-d5c73173d684",
                  "vedtakFattetTidspunkt": "2026-07-01T14:51:09.553706732",
                  "utbetalingsdager": []
                }
            """.trimIndent()
        )

        verify(exactly = 0) { tellingDao.lagre(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun seedForsikringsvurdering(
        harForsikring: Boolean,
        dekning: Forsikringsvurdering.Dekning?,
        forsikringskategori: Forsikringskategori?,
    ): ForsikringsvurderingId {
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = OppslagId.ny(),
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = harForsikring,
                dekning = dekning,
                opphørsdato = null,
                forsikringskategori = forsikringskategori,
            )
        )
        return forsikringsvurderingId
    }

    private fun event(
        forsikringsvurderingId: ForsikringsvurderingId,
        vedtaksperiodeId: UUID = UUID.fromString("231585ae-93a9-46ea-8613-d5c73173d684"),
        vedtakFattetTidspunkt: String = "2026-07-01T14:51:09.553706732",
        ventetidsBeløp: Int = 100,
        navDagBeløp: List<Int> = listOf(1636, 1636),
        meldingId: UUID = UUID.randomUUID(),
    ) = """
        {
          "@event_name": "vedtak_fattet",
          "fødselsnummer": "$fødselsnummer",
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
