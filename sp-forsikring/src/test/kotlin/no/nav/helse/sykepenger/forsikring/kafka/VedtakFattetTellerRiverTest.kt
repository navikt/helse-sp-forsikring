package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.shared.testsupport.Infotrygdforsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreForsikringsvurdering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.*

class VedtakFattetTellerRiverTest {
    private val testRapid = TestRapid()
    private val dataSource = TestcontainersSpForsikringDatabase.dataSource
    private val objectMapper = jacksonObjectMapper()

    init {
        VedtakFattetTellerRiver(
            rapidsConnection = testRapid,
            spForsikringDataSource = dataSource,
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
        testRapid.reset()
    }

    @Test
    fun `lagrer melding og utbetaling for navkjøpt forsikring med 80 prosent fra dag 1`() {
        val meldingId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                behandlingId = behandlingId,
                dager = dager(dekningsgrad = 80),
            ),
        )

        val melding = assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(forsikringsvurderingId.value, melding.forsikringsvurderingId)
        assertEquals(TESTFØDSELSNUMMER, melding.identitetsnummer)
        assertEquals(behandlingId, melding.behandlingId)
        assertEquals(
            LocalDateTime.parse(VEDTAK_FATTET_TIDSPUNKT).atZone(OSLO).toInstant(),
            melding.vedtakFattetTidspunkt,
        )
        val lagretJson = objectMapper.readTree(melding.json)
        assertEquals("vedtak_fattet", lagretJson["@event_name"].asText())
        assertEquals(meldingId.toString(), lagretJson["@id"].asText())
        assertEquals(3, lagretJson["utbetalingsdager"].size())

        val utbetalinger = hentUtbetalingerPerForsikringstype(meldingId)
        assertEquals(1, utbetalinger.size)
        val utbetaling = utbetalinger.single()
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.name, utbetaling.navkjøptForsikringType)
        assertNull(utbetaling.kollektivForsikringType)
        assertEquals(VENTETIDSBELØP, utbetaling.utbetaltIVentetid)
        // 80 % dekning gir ingen utbetaling utover det ordinære for selvstendige (80 %)
        assertEquals(0, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `regner ut merutbetaling utenom ventetid for navkjøpt forsikring med 100 prosent fra dag 1`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningsgrad = 100),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1.name, utbetaling.navkjøptForsikringType)
        assertEquals(VENTETIDSBELØP, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 2000
        assertEquals(400, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller ikke dager etter opphørsdato for navkjøpt forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            opphørsdato = LocalDate.parse("2026-04-22"),
                        ),
                    ),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager =
                    listOf(
                        Testdag(
                            dato = "2026-04-06",
                            type = "Ventetidsdag",
                            dekningsgrad = 100,
                            beløpTilBruker = VENTETIDSBELØP,
                        ),
                        Testdag(dato = "2026-04-22", type = "NavDag", dekningsgrad = 100, beløpTilBruker = 1000),
                        // Etter opphør: skal hverken telles med eller feile på avvikende dekningsgrad
                        Testdag(dato = "2026-04-23", type = "NavDag", dekningsgrad = 80, beløpTilBruker = 1000),
                    ),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(VENTETIDSBELØP, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 1000
        assertEquals(200, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer utbetaling for kollektiv forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B))

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningsgrad = 100),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(KollektivForsikring.FISKER_BLAD_B.name, utbetaling.kollektivForsikringType)
        assertNull(utbetaling.navkjøptForsikringType)
        assertEquals(VENTETIDSBELØP, utbetaling.utbetaltIVentetid)
        assertEquals(400, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer én rad per forsikringstype når bruker har både kollektiv og navkjøpt tilleggsforsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                forsikringer =
                    listOf(
                        Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1),
                    ),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningsgrad = 100),
            ),
        )

        val utbetalinger = hentUtbetalingerPerForsikringstype(meldingId)
        assertEquals(2, utbetalinger.size)
        val navkjøpt =
            assertNotNull(
                utbetalinger.singleOrNull {
                    it.navkjøptForsikringType == NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1.name
                },
            )
        assertEquals(VENTETIDSBELØP, navkjøpt.utbetaltIVentetid)
        assertEquals(0, navkjøpt.utbetaltUtenomVentetid)

        val kollektiv =
            assertNotNull(utbetalinger.singleOrNull { it.kollektivForsikringType == KollektivForsikring.JORDBRUKER.name })
        assertEquals(0, kollektiv.utbetaltIVentetid)
        assertEquals(400, kollektiv.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer melding men ingen utbetaling når vurderingen ikke har forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId = lagreForsikringsvurdering()

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
            ),
        )

        assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når utbetalingsdager har annen dekningsgrad enn forsikringen`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                vedtakFattetMelding(
                    forsikringsvurderingId = forsikringsvurderingId,
                    meldingId = meldingId,
                    dager = dager(dekningsgrad = 100),
                ),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når det er utbetalt i ventetiden for forsikring som ikke dekker ventetiden`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                vedtakFattetMelding(
                    forsikringsvurderingId = forsikringsvurderingId,
                    meldingId = meldingId,
                    dager = dager(dekningsgrad = 100, ventetidsbeløp = 100),
                ),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `lagrer utbetaling for forsikring fra dag 17 når ingenting er utbetalt i ventetiden`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningsgrad = 100, ventetidsbeløp = 0),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17.name, utbetaling.navkjøptForsikringType)
        assertEquals(0, utbetaling.utbetaltIVentetid)
        assertEquals(400, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `feiler og lagrer ingenting for ukjent forsikringsvurderingId`() {
        val meldingId = UUID.randomUUID()

        assertFails {
            testRapid.sendTestMessage(
                vedtakFattetMelding(forsikringsvurderingId = Forsikringsvurdering.Id.ny(), meldingId = meldingId),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(0, antallMeldinger())
    }

    @Test
    fun `feiler når samme melding leses to ganger`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )
        val melding =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningsgrad = 80),
            )

        testRapid.sendTestMessage(melding)
        assertFails { testRapid.sendTestMessage(melding) }

        assertEquals(1, antallMeldinger())
        assertEquals(1, hentUtbetalingerPerForsikringstype(meldingId).size)
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
              "behandlingId": "${UUID.randomUUID()}",
              "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
              "utbetalingsdager": []
            }
            """.trimIndent(),
        )

        assertEquals(0, antallMeldinger())
    }

    @Test
    fun `ignorerer melding for annen yrkesaktivitetstype`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                yrkesaktivitetstype = "ARBEIDSTAKER",
            ),
        )

        assertEquals(0, antallMeldinger())
    }

    @Test
    fun `ignorerer melding med annet event_name`() {
        val forsikringsvurderingId = lagreForsikringsvurdering()

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "vedtak_fattet_annullert",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$TESTFØDSELSNUMMER",
              "yrkesaktivitetstype": "SELVSTENDIG",
              "behandlingId": "${UUID.randomUUID()}",
              "forsikringsvurderingId": "${forsikringsvurderingId.value}",
              "vedtakFattetTidspunkt": "$VEDTAK_FATTET_TIDSPUNKT",
              "utbetalingsdager": []
            }
            """.trimIndent(),
        )

        assertEquals(0, antallMeldinger())
    }

    private data class Testdag(
        val dato: String,
        val type: String,
        val dekningsgrad: Int,
        val beløpTilBruker: Int,
    )

    private fun dager(
        dekningsgrad: Int,
        ventetidsbeløp: Int = VENTETIDSBELØP,
        navdagsbeløp: List<Int> = listOf(1000, 1000),
    ): List<Testdag> =
        listOf(
            Testdag(
                dato = "2026-04-06",
                type = "Ventetidsdag",
                dekningsgrad = dekningsgrad,
                beløpTilBruker = ventetidsbeløp,
            ),
        ) +
            navdagsbeløp.mapIndexed { index, beløp ->
                Testdag(
                    dato = LocalDate.parse("2026-04-22").plusDays(index.toLong()).toString(),
                    type = "NavDag",
                    dekningsgrad = dekningsgrad,
                    beløpTilBruker = beløp,
                )
            }

    private fun vedtakFattetMelding(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        meldingId: UUID = UUID.randomUUID(),
        behandlingId: UUID = UUID.randomUUID(),
        vedtakFattetTidspunkt: String = VEDTAK_FATTET_TIDSPUNKT,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        dager: List<Testdag> = dager(dekningsgrad = 80),
    ) = """
        {
          "@event_name": "vedtak_fattet",
          "@id": "$meldingId",
          "fødselsnummer": "$TESTFØDSELSNUMMER",
          "aktørId": "2465656615746",
          "yrkesaktivitetstype": "$yrkesaktivitetstype",
          "vedtaksperiodeId": "231585ae-93a9-46ea-8613-d5c73173d684",
          "behandlingId": "$behandlingId",
          "organisasjonsnummer": "SELVSTENDIG",
          "fom": "2026-04-01",
          "tom": "2026-04-26",
          "skjæringstidspunkt": "2026-04-06",
          "sykepengegrunnlag": 531709.0,
          "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
          "tags": ["Førstegangsbehandling"],
          "forsikringsvurderingId": "${forsikringsvurderingId.value}",
          "utbetalingsdager": [
            ${dager.joinToString(",") { it.tilJson() }}
          ]
        }
        """.trimIndent()

    private fun Testdag.tilJson() =
        """
        {
          "dato": "$dato",
          "type": "$type",
          "sykdomsgrad": 100,
          "begrunnelser": [],
          "dekningsgrad": $dekningsgrad,
          "beløpTilBruker": $beløpTilBruker,
          "beløpTilArbeidsgiver": 0
        }
        """.trimIndent()

    private data class VedtakFattetMeldingRad(
        val forsikringsvurderingId: UUID?,
        val identitetsnummer: String,
        val behandlingId: UUID,
        val vedtakFattetTidspunkt: Instant,
        val json: String,
    )

    private data class UtbetalingRad(
        val utbetaltIVentetid: Int,
        val utbetaltUtenomVentetid: Int,
        val kollektivForsikringType: String?,
        val navkjøptForsikringType: String?,
    )

    private fun hentVedtakFattetMelding(id: UUID): VedtakFattetMeldingRad? =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT forsikringsvurdering_id, identitetsnummer, behandling_id, vedtak_fattet_tidspunkt, json
                    FROM vedtak_fattet_melding
                    WHERE id = ?
                    """.trimIndent(),
                    id,
                ).map { row ->
                    VedtakFattetMeldingRad(
                        forsikringsvurderingId = row.uuidOrNull("forsikringsvurdering_id"),
                        identitetsnummer = row.string("identitetsnummer"),
                        behandlingId = row.uuid("behandling_id"),
                        vedtakFattetTidspunkt = row.instant("vedtak_fattet_tidspunkt"),
                        json = row.string("json"),
                    )
                }.asSingle,
            )
        }

    private fun hentUtbetalingerPerForsikringstype(vedtakFattetMeldingId: UUID): List<UtbetalingRad> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT utbetalt_i_ventetid, utbetalt_utenom_ventetid, kollektiv_forsikring_type, navkjøpt_forsikring_type
                    FROM utbetaling_per_forsikringstype
                    WHERE vedtak_fattet_melding_id = ?
                    """.trimIndent(),
                    vedtakFattetMeldingId,
                ).map { row ->
                    UtbetalingRad(
                        utbetaltIVentetid = row.int("utbetalt_i_ventetid"),
                        utbetaltUtenomVentetid = row.int("utbetalt_utenom_ventetid"),
                        kollektivForsikringType = row.stringOrNull("kollektiv_forsikring_type"),
                        navkjøptForsikringType = row.stringOrNull("navkjøpt_forsikring_type"),
                    )
                }.asList,
            )
        }

    private fun antallMeldinger(): Int =
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(*) FROM vedtak_fattet_melding").map { it.int(1) }.asSingle)!!
        }

    private companion object {
        const val VEDTAK_FATTET_TIDSPUNKT = "2026-07-01T14:51:09.553706"
        const val VENTETIDSBELØP = 100
        val OSLO: ZoneId = ZoneId.of("Europe/Oslo")
    }
}
