package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.shared.testsupport.OppgaveOppsamler
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
import no.nav.sykepenger.libs.testing.testdata.feb
import no.nav.sykepenger.libs.testing.testdata.jan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VedtakFattetRiverTest {
    private val testRapid = TestRapid()
    private val oppgaveOppsamler = OppgaveOppsamler()
    private val identitetsnummer = lagIdentitetsnummer()

    private val skjæringstidspunkt = 1 jan 2026

    /**
     * Vedtaket dekker hele ventetiden og et stykke ut i sykefraværet. Med utbetaling hver ukedag gir det
     * 12 utbetalingsdager i ventetiden (1.–16. januar) og 11 utbetalingsdager utenom den (19. januar–2. februar).
     */
    private val utbetalingsdagerFom = skjæringstidspunkt
    private val utbetalingsdagerTom = 2 feb 2026

    init {
        VedtakFattetRiver(
            rapidsConnection = testRapid,
            gosysOppgaveClient = oppgaveOppsamler.client,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
        testRapid.reset()
    }

    @Test
    fun `lager oppgave for avvik mellom sykepengegrunnlag og premiegrunnlag`() {
        val sykepengegrunnlag = 400000
        val premiegrunnlag = 200000
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                premiegrunnlag = premiegrunnlag,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            sykepengegrunnlag = sykepengegrunnlag,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        val årsak = assertIs<Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag>(oppgave.årsak)
        assertEquals(0, årsak.sykepengegrunnlag.compareTo(BigDecimal(sykepengegrunnlag)))
        assertEquals(premiegrunnlag, årsak.premiegrunnlag)
        assertEquals(0, årsak.avviksbeløp.compareTo(BigDecimal("200000")))
        assertEquals(identitetsnummer.value, oppgave.fødselsnummer)
    }

    @Test
    fun `lager ingen oppgave når det ikke er avvik`() {
        val sykepengegrunnlag = 400000
        val premiegrunnlag = 400000
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                premiegrunnlag = premiegrunnlag,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            sykepengegrunnlag = sykepengegrunnlag,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave når avviket er mindre enn 100`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                premiegrunnlag = 7900,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            sykepengegrunnlag = 7999,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager oppgave når avviket er nøyaktig 100`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                premiegrunnlag = 8000,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            sykepengegrunnlag = 8100,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        val årsak = assertIs<Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag>(oppgave.årsak)
        assertEquals(0, årsak.sykepengegrunnlag.compareTo(BigDecimal(8100)))
        assertEquals(8000, årsak.premiegrunnlag)
        assertEquals(0, årsak.avviksbeløp.compareTo(BigDecimal("100")))
    }

    @Test
    fun `lager oppgave når premiegrunnlaget er 100 høyere enn sykepengegrunnlaget`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                premiegrunnlag = 8100,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            sykepengegrunnlag = 8000,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        val årsak = assertIs<Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag>(oppgave.årsak)
        assertEquals(0, årsak.avviksbeløp.compareTo(BigDecimal("100")))
    }

    @Test
    fun `lager ingen oppgave når det kun er kollektiv forsikring`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            dekningsgrad = 100,
            utbetalingIVentetid = true,
        )

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave når det ikke er forsikring i vurderingen`() {
        val forsikringsvurderingId = settOppForsikringsvurdering()

        sendVedtakFattet(forsikringsvurderingId = forsikringsvurderingId)

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lagrer melding og utbetaling for individuell forsikring med 80 prosent fra dag 1`() {
        val behandlingId = UUID.randomUUID()
        val vedtakFattetTidspunkt = LocalDateTime.parse("2026-07-08T12:34:56.789101112")
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
            )

        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 80,
                utbetalingIVentetid = true,
                behandlingId = behandlingId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            )
        testRapid.sendTestMessage(melding)

        val lagretMelding = assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(forsikringsvurderingId.value, lagretMelding.forsikringsvurderingId)
        assertEquals(identitetsnummer.value, lagretMelding.identitetsnummer)
        assertEquals(behandlingId, lagretMelding.behandlingId)
        assertEquals(Instant.parse("2026-07-08T10:34:56.789101Z"), lagretMelding.vedtakFattetTidspunkt)
        assertJsonEquals(
            expectedJson = melding,
            actualJson = lagretMelding.json,
            bortsettFraStier = TestRapid.GENERERTE_JSONSTIER,
        )

        val utbetalinger = hentUtbetalingerPerForsikringstype(meldingId)
        assertEquals(1, utbetalinger.size)
        val utbetaling = utbetalinger.single()
        assertEquals("SELVSTENDIG_80_PROSENT_FRA_DAG_1", utbetaling.individuellForsikringType)
        assertNull(utbetaling.kollektivForsikringType)
        assertNumericallyEqual(12000, utbetaling.utbetaltIVentetid)
        assertNumericallyEqual(0, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller opp tilleggssykepenger for individuell forsikring med 100 prosent fra dag 1`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
                dagsbeløp = 1000,
            )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals("SELVSTENDIG_100_PROSENT_FRA_DAG_1", utbetaling.individuellForsikringType)
        // 12 dager à 1000 kroner
        assertNumericallyEqual(12000, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 1000 kroner i 11 dager
        assertNumericallyEqual(2200, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller opp tilleggssykepenger med desimaler når fordelingen ikke går opp i hele kroner`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
                dagsbeløp = 1003,
            )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertNumericallyEqual(12036, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 1003 kroner er 200,60 per dag, altså 2206,60 for de 11 dagene utenom ventetiden
        assertNumericallyEqual(BigDecimal("2206.60"), utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller ikke dager etter opphørsdato for individuell forsikring`() {
        val opphørsdato = 22 jan 2026
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                opphørsdato = opphørsdato,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
                // Etter opphøret faller dekningen tilbake til den ordinære, og dagene skal hverken telles med
                // eller feile på avvikende dekningsgrad
                endretDekningsgradFraOgMed = opphørsdato.plusDays(1),
                endretDekningsgrad = 80,
            )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertNumericallyEqual(12000, utbetaling.utbetaltIVentetid)
        // (100 - 80) % av 1000 kroner for de fire utbetalingsdagene mellom ventetiden og opphøret
        assertNumericallyEqual(800, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `teller opp tilleggsykepenger for kollektiv forsikring`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
            )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals(KollektivForsikring.FISKER_BLAD_B.name, utbetaling.kollektivForsikringType)
        assertNull(utbetaling.individuellForsikringType)
        assertNumericallyEqual(12000, utbetaling.utbetaltIVentetid)
        assertNumericallyEqual(2200, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer én rad per forsikringstype når bruker har både kollektiv og individuell tilleggsforsikring`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
            )

        val utbetalinger = hentUtbetalingerPerForsikringstype(meldingId)
        assertEquals(2, utbetalinger.size)
        val individuell =
            utbetalinger.single {
                it.individuellForsikringType == "SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1"
            }
        assertNumericallyEqual(12000, individuell.utbetaltIVentetid)
        assertNumericallyEqual(0, individuell.utbetaltUtenomVentetid)

        val kollektiv = utbetalinger.single { it.kollektivForsikringType == KollektivForsikring.JORDBRUKER.name }
        assertNumericallyEqual(0, kollektiv.utbetaltIVentetid)
        assertNumericallyEqual(2200, kollektiv.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer melding men ingen utbetaling når vurderingen ikke har forsikring`() {
        val forsikringsvurderingId = settOppForsikringsvurdering()

        val meldingId = sendVedtakFattet(forsikringsvurderingId)

        assertNotNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når det er utbetalt i ventetiden uten at brukeren har forsikring`() {
        val forsikringsvurderingId = settOppForsikringsvurdering()

        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                utbetalingIVentetid = true,
            )
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(melding) }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når utbetalingsdager har annen dekningsgrad enn forsikringen`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
            )

        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
            )
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(melding) }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `feiler og lagrer ingenting når det er utbetalt i ventetiden for forsikring som ikke dekker ventetiden`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
            )

        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = true,
            )
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(melding) }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(emptyList(), hentUtbetalingerPerForsikringstype(meldingId))
    }

    @Test
    fun `lagrer utbetaling for forsikring fra dag 17 når ingenting er utbetalt i ventetiden`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
            )

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 100,
                utbetalingIVentetid = false,
            )

        val utbetaling = hentUtbetalingerPerForsikringstype(meldingId).single()
        assertEquals("SELVSTENDIG_100_PROSENT_FRA_DAG_17", utbetaling.individuellForsikringType)
        assertNumericallyEqual(0, utbetaling.utbetaltIVentetid)
        assertNumericallyEqual(2200, utbetaling.utbetaltUtenomVentetid)
    }

    @Test
    fun `feiler og lagrer ingenting for ukjent forsikringsvurderingId`() {
        val (meldingId, melding) = vedtakFattetMelding(forsikringsvurderingId = Forsikringsvurdering.Id.ny())

        assertFails { testRapid.sendTestMessage(melding) }

        assertNull(hentVedtakFattetMelding(meldingId))
        assertEquals(0, antallLagredeVedtakFattetMelding())
        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `hopper over melding som allerede er lagret ned`() {
        val forsikringsvurderingId =
            settOppForsikringsvurdering(
                individuellForsikringType = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
            )

        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                dekningsgrad = 80,
                utbetalingIVentetid = true,
            )
        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(melding)

        assertEquals(1, antallLagredeVedtakFattetMelding())
        assertEquals(1, hentUtbetalingerPerForsikringstype(meldingId).size)
    }

    @Test
    fun `lagrer melding men ingen utbetaling når melding mangler forsikringsvurderingId`() {
        val behandlingId = UUID.randomUUID()

        val meldingId =
            sendVedtakFattet(
                forsikringsvurderingId = null,
                behandlingId = behandlingId,
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
        val forsikringsvurderingId = settOppForsikringsvurdering()

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            yrkesaktivitetstype = "ARBEIDSTAKER",
        )

        assertEquals(0, antallLagredeVedtakFattetMelding())
    }

    @Test
    fun `ignorerer melding med annet event_name`() {
        val forsikringsvurderingId = settOppForsikringsvurdering()

        sendVedtakFattet(
            forsikringsvurderingId = forsikringsvurderingId,
            eventName = "vedtak_fattet_annullert",
        )

        assertEquals(0, antallLagredeVedtakFattetMelding())
    }

    private fun sendVedtakFattet(
        forsikringsvurderingId: Forsikringsvurdering.Id?,
        sykepengegrunnlag: Int = 400000,
        dekningsgrad: Int = 80,
        endretDekningsgradFraOgMed: LocalDate? = null,
        endretDekningsgrad: Int? = null,
        utbetalingIVentetid: Boolean = false,
        dagsbeløp: Int = 1000,
        førstegangsbehandling: Boolean = true,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        behandlingId: UUID = UUID.randomUUID(),
        eventName: String = "vedtak_fattet",
    ): UUID {
        val (meldingId, melding) =
            vedtakFattetMelding(
                forsikringsvurderingId = forsikringsvurderingId,
                sykepengegrunnlag = sykepengegrunnlag,
                dekningsgrad = dekningsgrad,
                endretDekningsgradFraOgMed = endretDekningsgradFraOgMed,
                endretDekningsgrad = endretDekningsgrad,
                utbetalingIVentetid = utbetalingIVentetid,
                dagsbeløp = dagsbeløp,
                førstegangsbehandling = førstegangsbehandling,
                yrkesaktivitetstype = yrkesaktivitetstype,
                behandlingId = behandlingId,
                eventName = eventName,
            )
        testRapid.sendTestMessage(melding)
        return meldingId
    }

    private fun vedtakFattetMelding(
        forsikringsvurderingId: Forsikringsvurdering.Id?,
        sykepengegrunnlag: Int = 400000,
        dekningsgrad: Int = 80,
        endretDekningsgradFraOgMed: LocalDate? = null,
        endretDekningsgrad: Int? = null,
        utbetalingIVentetid: Boolean = false,
        dagsbeløp: Int = 1000,
        førstegangsbehandling: Boolean = true,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        fødselsnummer: String = identitetsnummer.value,
        behandlingId: UUID = UUID.randomUUID(),
        vedtakFattetTidspunkt: LocalDateTime = LocalDateTime.now(),
        eventName: String = "vedtak_fattet",
    ): Pair<UUID, String> =
        Testmeldingsfabrikk
            .lagVedtakFattetMelding(
                eventName = eventName,
                yrkesaktivitetstype = yrkesaktivitetstype,
                fødselsnummer = fødselsnummer,
                behandlingId = behandlingId,
                førstegangsbehandling = førstegangsbehandling,
                sykepengegrunnlag = sykepengegrunnlag,
                skjæringstidspunkt = skjæringstidspunkt,
                forsikringsvurderingId = forsikringsvurderingId?.value?.toString(),
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                utbetalingsdagerFom = utbetalingsdagerFom,
                utbetalingsdagerTom = utbetalingsdagerTom,
                utbetalingsdagerUtbetalingIVentetid = utbetalingIVentetid,
                utbetalingsdagerDekningsgrad = dekningsgrad,
                utbetalingsdagerEndretDekningsgradFraOgMed = endretDekningsgradFraOgMed,
                utbetalingsdagerEndretDekningsgrad = endretDekningsgrad,
                utbetalingsdagerBeløpTilBruker = dagsbeløp,
            ).let { it["@id"].stringValue().let(UUID::fromString) to it.toPrettyString() }

    private fun settOppForsikringsvurdering(
        individuellForsikringType: IndividuellForsikringType? = null,
        premiegrunnlag: Int = 400000,
        opphørsdato: LocalDate? = null,
        kollektivForsikring: KollektivForsikring? = null,
    ): Forsikringsvurdering.Id =
        lagForsikringsvurdering(
            skjæringstidspunkt = skjæringstidspunkt,
            identitetsnummer = identitetsnummer,
            spesielleYrkesgrupper = kollektivForsikring?.spesielleYrkesgrupper.orEmpty(),
            individuelleForsikringer =
                listOfNotNull(
                    individuellForsikringType?.let { type ->
                        lagVurdertIndividuellForsikring(
                            type = type,
                            virkningsdato = skjæringstidspunkt,
                            premiegrunnlag = premiegrunnlag,
                            opphører = opphørsdato != null,
                            opphørsdato = opphørsdato,
                        )
                    },
                ),
            kollektivForsikring = kollektivForsikring,
        ).also(::lagreRåkopiOgForsikringsvurdering)
            .id

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
        val utbetaltIVentetid: BigDecimal,
        val utbetaltUtenomVentetid: BigDecimal,
        val kollektivForsikringType: String?,
        val individuellForsikringType: String?,
    )

    private fun assertNumericallyEqual(
        expected: Int,
        actual: BigDecimal,
    ) = assertNumericallyEqual(expected = BigDecimal(expected), actual = actual)

    private fun assertNumericallyEqual(
        expected: BigDecimal,
        actual: BigDecimal,
    ) = assertEquals(
        expected = 0,
        actual = expected.compareTo(actual),
        message = "Expected ${expected.toPlainString()}, but was ${actual.toPlainString()}",
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
                        utbetaltIVentetid = row.bigDecimal("utbetalt_i_ventetid"),
                        utbetaltUtenomVentetid = row.bigDecimal("utbetalt_utenom_ventetid"),
                        kollektivForsikringType = row.stringOrNull("kollektiv_forsikring_type"),
                        individuellForsikringType = row.stringOrNull("individuell_forsikring_type"),
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
