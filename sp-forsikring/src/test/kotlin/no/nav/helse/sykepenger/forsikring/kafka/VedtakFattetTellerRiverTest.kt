package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test.assertJsonEquals
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VedtakFattetTellerRiverTest {
    private val testRapid = TestRapid()

    init {
        VedtakFattetTellerRiver(
            rapidsConnection = testRapid,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
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
        val identitetsnummer = lagIdentitetsnummer()
        val skjæringstidspunkt = LocalDate.parse("2026-04-06")
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = skjæringstidspunkt,
                identitetsnummer = identitetsnummer,
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)

        val testmelding =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurdering.id,
                meldingId = meldingId,
                behandlingId = behandlingId,
                fødselsnummer = identitetsnummer.value,
                dager =
                    utbetalingsdagerForPeriode(
                        fom = "2026-04-06",
                        tom = "2026-04-30",
                        skjæringstidspunkt = skjæringstidspunkt,
                        dekningsgrad = 80,
                        beløpPerUkedag = 100,
                    ),
                vedtakFattetTidspunkt = "2026-07-08T12:34:56.789101112",
            )
        testRapid.sendTestMessage(testmelding)

        val melding = assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(forsikringsvurdering.id.value, melding.forsikringsvurderingId)
        assertEquals(identitetsnummer.value, melding.identitetsnummer)
        assertEquals(behandlingId, melding.behandlingId)
        assertEquals(Instant.parse("2026-07-08T10:34:56.789101Z"), melding.vedtakFattetTidspunkt)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJson = melding.json,
            bortsettFraStier = TestRapid.GENERERTE_JSONSTIER,
        )

        val utbetalinger = hentUtbetalingerPerForsikringstype(meldingId)
        assertEquals(1, utbetalinger.size)
        val utbetaling = utbetalinger.single()
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.name, utbetaling.navkjøptForsikringType)
        assertNull(utbetaling.kollektivForsikringType)
        assertEquals(1200, utbetaling.utbetaltIVentetid)
        assertEquals(0, utbetaling.utbetaltUtenomVentetid)
    }

    private fun utbetalingsdagerForPeriode(
        fom: String,
        tom: String,
        skjæringstidspunkt: LocalDate,
        dekningsgrad: Int,
        beløpPerUkedag: Int,
    ): List<String> {
        val fomDate = LocalDate.parse(fom)
        val tomDate = LocalDate.parse(tom)
        val numDaysBeyondFirst = tomDate.toEpochDay() - fomDate.toEpochDay()
        return (0..numDaysBeyondFirst)
            .map { fomDate.plusDays(it) }
            .map { localDate ->
                val erHelg = localDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                val erVentetid = (localDate.toEpochDay() - skjæringstidspunkt.toEpochDay()) < 16
                lagUtbetalingsdagJson(
                    dato = localDate.toString(),
                    type =
                        if (erVentetid) {
                            "Ventetidsdag"
                        } else if (erHelg) {
                            "NavHelgDag"
                        } else {
                            "NavDag"
                        },
                    dekningsgrad = dekningsgrad,
                    beløpTilBruker = if (erHelg) 0 else beløpPerUkedag,
                )
            }
    }

    @Test
    fun `regner ut merutbetaling utenom ventetid for navkjøpt forsikring med 100 prosent fra dag 1`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurdering.id,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 100, beløpIVentetid = 100),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1.name, utbetaling.navkjøptForsikringType)
        assertEquals(100, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 2000
        assertEquals(400, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller ikke dager etter opphørsdato for navkjøpt forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                            opphører = true,
                            opphørsdato = LocalDate.parse("2026-04-22"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager =
                    listOf(
                        lagUtbetalingsdagJson(
                            dato = "2026-04-06",
                            type = "Ventetidsdag",
                            dekningsgrad = 100,
                            beløpTilBruker = 100,
                        ),
                        lagUtbetalingsdagJson(
                            dato = "2026-04-22",
                            type = "NavDag",
                            dekningsgrad = 100,
                            beløpTilBruker = 1000,
                        ),
                        // Etter opphør: skal hverken telles med eller feile på avvikende dekningsgrad
                        lagUtbetalingsdagJson(
                            dato = "2026-04-23",
                            type = "NavDag",
                            dekningsgrad = 80,
                            beløpTilBruker = 1000,
                        ),
                    ),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(100, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 1000
        assertEquals(200, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer utbetaling for kollektiv forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 100, beløpIVentetid = 100),
            ),
        )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(KollektivForsikring.FISKER_BLAD_B.name, utbetaling.kollektivForsikringType)
        assertNull(utbetaling.navkjøptForsikringType)
        assertEquals(100, utbetaling.utbetaltIVentetid)
        assertEquals(400, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer én rad per forsikringstype når bruker har både kollektiv og navkjøpt tilleggsforsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 100, beløpIVentetid = 100),
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
        assertEquals(100, navkjøpt.utbetaltIVentetid)
        assertEquals(0, navkjøpt.utbetaltUtenomVentetid)

        val kollektiv =
            assertNotNull(utbetalinger.singleOrNull { it.kollektivForsikringType == KollektivForsikring.JORDBRUKER.name })
        assertEquals(0, kollektiv.utbetaltIVentetid)
        assertEquals(400, kollektiv.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer melding men ingen utbetaling når vurderingen ikke har forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-04-06"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 80, beløpIVentetid = 0),
            ),
        )

        assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når det er utbetalt i ventetiden uten at brukeren har forsikring`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-04-06"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                vedtakFattetMelding(
                    forsikringsvurderingId = forsikringsvurderingId,
                    meldingId = meldingId,
                    dager = dager(dekningIVentetid = 80, beløpIVentetid = 100),
                ),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når utbetalingsdager har annen dekningsgrad enn forsikringen`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                vedtakFattetMelding(
                    forsikringsvurderingId = forsikringsvurderingId,
                    meldingId = meldingId,
                    dager = dager(dekningIVentetid = 100, beløpIVentetid = 100),
                ),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når det er utbetalt i ventetiden for forsikring som ikke dekker ventetiden`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(
                vedtakFattetMelding(
                    forsikringsvurderingId = forsikringsvurderingId,
                    meldingId = meldingId,
                    dager = dager(dekningIVentetid = 100, beløpIVentetid = 100),
                ),
            )
        }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `lagrer utbetaling for forsikring fra dag 17 når ingenting er utbetalt i ventetiden`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 100, beløpIVentetid = 0),
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
        assertEquals(0, antallLagredeVedtakFattetMelding())
    }

    @Test
    fun `hopper over melding som allerede er lagret ned`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id
        val melding =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                dager = dager(dekningIVentetid = 80, beløpIVentetid = 100),
            )

        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(melding)

        assertEquals(1, antallLagredeVedtakFattetMelding())
        assertEquals(1, hentUtbetalingerPerForsikringstype(meldingId).size)
    }

    @Test
    fun `lagrer melding men ingen utbetaling når melding mangler forsikringsvurderingId`() {
        val meldingId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val identitetsnummer = lagIdentitetsnummer()

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = null,
                meldingId = meldingId,
                behandlingId = behandlingId,
                fødselsnummer = identitetsnummer.value,
                dager = dager(dekningIVentetid = 80, beløpIVentetid = 100),
            ),
        )

        val melding = assertNotNull(hentVedtakFattetMelding(meldingId))
        assertNull(melding.forsikringsvurderingId)
        assertEquals(identitetsnummer.value, melding.identitetsnummer)
        assertEquals(behandlingId, melding.behandlingId)
        assertEquals(1, antallLagredeVedtakFattetMelding())
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `ignorerer melding for annen yrkesaktivitetstype`() {
        val meldingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-04-06"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2026-01-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                meldingId = meldingId,
                yrkesaktivitetstype = "ARBEIDSTAKER",
            ),
        )

        assertEquals(0, antallLagredeVedtakFattetMelding())
    }

    @Test
    fun `ignorerer melding med annet event_name`() {
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-04-06"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "vedtak_fattet_annullert",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "${lagIdentitetsnummer().value}",
              "yrkesaktivitetstype": "SELVSTENDIG",
              "behandlingId": "${UUID.randomUUID()}",
              "forsikringsvurderingId": "${forsikringsvurderingId.value}",
              "vedtakFattetTidspunkt": "2021-02-03T12:34:56.789101112",
              "utbetalingsdager": []
            }
            """.trimIndent(),
        )

        assertEquals(0, antallLagredeVedtakFattetMelding())
    }

    private fun dager(
        dekningIVentetid: Int,
        dekningUtenforVentetid: Int = dekningIVentetid,
        beløpIVentetid: Int,
        beløpUtenforVentetid: Int = 1000,
    ): List<String> =
        listOf(
            lagUtbetalingsdagJson(
                dato = "2026-04-06",
                type = "Ventetidsdag",
                dekningsgrad = dekningIVentetid,
                beløpTilBruker = beløpIVentetid,
            ),
            lagUtbetalingsdagJson(
                dato = "2026-04-23",
                type = "NavDag",
                dekningsgrad = dekningUtenforVentetid,
                beløpTilBruker = beløpUtenforVentetid,
            ),
            lagUtbetalingsdagJson(
                dato = "2026-04-24",
                type = "NavDag",
                dekningsgrad = dekningUtenforVentetid,
                beløpTilBruker = beløpUtenforVentetid,
            ),
        )

    private fun lagUtbetalingsdagJson(
        dato: String,
        type: String,
        dekningsgrad: Int,
        beløpTilBruker: Int,
    ): String =
        """
        {
          "dato": "$dato",
          "type": "$type",
          "dekningsgrad": $dekningsgrad,
          "beløpTilBruker": $beløpTilBruker
        }
        """.trimIndent()

    private fun vedtakFattetMelding(
        forsikringsvurderingId: Forsikringsvurdering.Id?,
        meldingId: UUID = UUID.randomUUID(),
        behandlingId: UUID = UUID.randomUUID(),
        vedtakFattetTidspunkt: String = LocalDateTime.now().format(ISO_LOCAL_DATE_TIME),
        yrkesaktivitetstype: String = "SELVSTENDIG",
        fødselsnummer: String = lagIdentitetsnummer().value,
        dager: List<String> =
            listOf(
                lagUtbetalingsdagJson(
                    dato = "2026-04-06",
                    type = "Ventetidsdag",
                    dekningsgrad = 80,
                    beløpTilBruker = 100,
                ),
                lagUtbetalingsdagJson(
                    dato = "2026-04-23",
                    type = "NavDag",
                    dekningsgrad = 80,
                    beløpTilBruker = 1000,
                ),
                lagUtbetalingsdagJson(
                    dato = "2026-04-24",
                    type = "NavDag",
                    dekningsgrad = 80,
                    beløpTilBruker = 1000,
                ),
            ),
    ) = """
        {
          "@event_name": "vedtak_fattet",
          "@id": "$meldingId",
          "fødselsnummer": "$fødselsnummer",
          "yrkesaktivitetstype": "$yrkesaktivitetstype",
          "behandlingId": "$behandlingId",
          "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
          ${forsikringsvurderingId?.let { """"forsikringsvurderingId": "${it.value}",""" } ?: ""}
          "utbetalingsdager": [
            ${dager.joinToString(",")}
          ]
        }
        """.trimIndent()

    private data class VedtakFattetMeldingDto(
        val forsikringsvurderingId: UUID?,
        val identitetsnummer: String,
        val behandlingId: UUID,
        val vedtakFattetTidspunkt: Instant,
        val json: String,
    )

    private fun hentVedtakFattetMelding(id: UUID): VedtakFattetMeldingDto? =
        sessionOf(TestcontainersSpForsikringDatabase.dataSource).use { session ->
            session.run(
                queryOf(
                    // language=postgresql
                    "SELECT * FROM vedtak_fattet_melding WHERE id = :id",
                    mapOf("id" to id),
                ).map { row ->
                    VedtakFattetMeldingDto(
                        forsikringsvurderingId = row.uuidOrNull("forsikringsvurdering_id"),
                        identitetsnummer = row.string("identitetsnummer"),
                        behandlingId = row.uuid("behandling_id"),
                        vedtakFattetTidspunkt = row.instant("vedtak_fattet_tidspunkt"),
                        json = row.string("json"),
                    )
                }.asSingle,
            )
        }

    private data class UtbetalingDto(
        val utbetaltIVentetid: Int,
        val utbetaltUtenomVentetid: Int,
        val kollektivForsikringType: String?,
        val navkjøptForsikringType: String?,
    )

    private fun hentUtbetalingerPerForsikringstype(vedtakFattetMeldingId: UUID): List<UtbetalingDto> =
        sessionOf(TestcontainersSpForsikringDatabase.dataSource).use { session ->
            session.run(
                queryOf(
                    // language=postgresql
                    "SELECT * FROM utbetaling_per_forsikringstype WHERE vedtak_fattet_melding_id = :vedtak_fattet_melding_id",
                    mapOf("vedtak_fattet_melding_id" to vedtakFattetMeldingId),
                ).map { row ->
                    UtbetalingDto(
                        utbetaltIVentetid = row.int("utbetalt_i_ventetid"),
                        utbetaltUtenomVentetid = row.int("utbetalt_utenom_ventetid"),
                        kollektivForsikringType = row.stringOrNull("kollektiv_forsikring_type"),
                        navkjøptForsikringType = row.stringOrNull("navkjøpt_forsikring_type"),
                    )
                }.asList,
            )
        }

    private fun antallLagredeVedtakFattetMelding(): Int =
        sessionOf(TestcontainersSpForsikringDatabase.dataSource).use { session ->
            session.run(
                queryOf(
                    // language=postgresql
                    "SELECT COUNT(*) FROM vedtak_fattet_melding",
                ).map { it.int(1) }.asSingle,
            )!!
        }
}
