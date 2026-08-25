package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FordelingAvBeløpPåUtbetalingsdagTest {
    @Test
    fun `uten forsikring tilskrives hele utbetalingen den ordinære dekningen`() {
        val fordeling = fordeling(dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80))

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = "1000")
    }

    @Test
    fun `individuell forsikring med samme grad som den ordinære dekningen bidrar bare i ventetiden`() {
        val individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)

        val iVentetiden =
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 80),
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(fordeling = iVentetiden, uavhengigAvForsikring = "0", påGrunnAvIndividuellForsikring = "100")

        val utenomVentetiden =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80),
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(fordeling = utenomVentetiden, uavhengigAvForsikring = "1000", påGrunnAvIndividuellForsikring = "0")
    }

    @Test
    fun `individuell forsikring med høyere grad bidrar med differansen mot den ordinære dekningen`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )

        // (100 - 80) % av 1000
        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = "800", påGrunnAvIndividuellForsikring = "200")
    }

    @Test
    fun `kollektiv forsikring bidrar med differansen mot den ordinære dekningen`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = "800", påGrunnAvKollektivForsikring = "200")
    }

    @Test
    fun `individuell tilleggsforsikring bidrar bare med det den gir utover den kollektive forsikringen`() {
        val kollektivForsikring = KollektivForsikring.JORDBRUKER
        val individuellForsikring =
            gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)

        // I ventetiden gir den kollektive forsikringen ingenting, siden den først gjelder fra dag 17
        val iVentetiden =
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 100),
                kollektivForsikring = kollektivForsikring,
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(
            fordeling = iVentetiden,
            uavhengigAvForsikring = "0",
            påGrunnAvKollektivForsikring = "0",
            påGrunnAvIndividuellForsikring = "100",
        )

        // Fra dag 17 har den kollektive forsikringen tatt over, og tilleggsforsikringen gir ikke noe ekstra
        val utenomVentetiden =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                kollektivForsikring = kollektivForsikring,
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(
            fordeling = utenomVentetiden,
            uavhengigAvForsikring = "800",
            påGrunnAvKollektivForsikring = "200",
            påGrunnAvIndividuellForsikring = "0",
        )
    }

    @Test
    fun `individuell forsikring bidrar ikke etter opphørsdato`() {
        val individuellForsikring =
            gyldigIndividuellForsikring(
                type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                opphørsdato = LocalDate.parse("2026-04-22"),
            )

        val påOpphørsdatoen =
            fordeling(
                dag = navdag(dato = LocalDate.parse("2026-04-22"), beløpTilBruker = 1000, dekningsgrad = 100),
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(fordeling = påOpphørsdatoen, uavhengigAvForsikring = "800", påGrunnAvIndividuellForsikring = "200")

        // Etter opphør faller dagen tilbake til ordinær dekning, og dekningsgraden er da 80
        val etterOpphørsdatoen =
            fordeling(
                dag = navdag(dato = LocalDate.parse("2026-04-23"), beløpTilBruker = 1000, dekningsgrad = 80),
                individuellForsikring = individuellForsikring,
            )
        assertFordeling(fordeling = etterOpphørsdatoen, uavhengigAvForsikring = "1000", påGrunnAvIndividuellForsikring = "0")
    }

    @Test
    fun `beholder desimalene i fordelingen`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1003, dekningsgrad = 100),
                individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )

        // (100 - 80) % av 1003 er 200,6
        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = "802.4", påGrunnAvIndividuellForsikring = "200.6")
    }

    @Test
    fun `fordelingen summerer seg opp til hele beløpet som er utbetalt til bruker`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1003, dekningsgrad = 100),
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
                individuellForsikring =
                    gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1),
            )

        val sum =
            fordeling.uavhengigAvForsikring +
                fordeling.påGrunnAvKollektivForsikring +
                fordeling.påGrunnAvIndividuellForsikring
        assertEquals(0, BigDecimal(1003).compareTo(sum), "Summen av fordelingen var $sum")
    }

    @Test
    fun `dager uten utbetaling fordeles ikke, uavhengig av dekningsgraden på dagen`() {
        val fordeling = fordeling(dag = navdag(beløpTilBruker = 0, dekningsgrad = 100))

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = "0")
    }

    @Test
    fun `feiler når det er utbetalt mer enn dekningene skulle gitt`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1),
            )
        }
    }

    @Test
    fun `feiler når det er utbetalt mindre enn dekningene skulle gitt`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80),
                individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )
        }
    }

    @Test
    fun `feiler når det er utbetalt i ventetiden for en forsikring som ikke dekker ventetiden`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 100),
                individuellForsikring = gyldigIndividuellForsikring(type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17),
            )
        }
    }

    @Test
    fun `feiler når det er utbetalt i ventetiden uten at brukeren har noen dekning der`() {
        assertThrows<IllegalStateException> {
            fordeling(dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 80))
        }
    }

    private fun fordeling(
        dag: Utbetalingsdag,
        yrkesaktivitetstype: Yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        kollektivForsikring: KollektivForsikring? = null,
        individuellForsikring: VurdertIndividuellForsikring? = null,
    ) = FordelingAvBeløpPåUtbetalingsdag.finnFordeling(
        dag = dag,
        yrkesaktivitetstype = yrkesaktivitetstype,
        kollektivForsikring = kollektivForsikring,
        individuellForsikring = individuellForsikring,
    )

    private fun assertFordeling(
        fordeling: FordelingAvBeløpPåUtbetalingsdag,
        uavhengigAvForsikring: String,
        påGrunnAvKollektivForsikring: String = "0",
        påGrunnAvIndividuellForsikring: String = "0",
    ) {
        assertBeløp(uavhengigAvForsikring, fordeling.uavhengigAvForsikring, "Beløp uavhengig av forsikring")
        assertBeløp(
            påGrunnAvKollektivForsikring,
            fordeling.påGrunnAvKollektivForsikring,
            "Beløp på grunn av kollektiv forsikring",
        )
        assertBeløp(
            påGrunnAvIndividuellForsikring,
            fordeling.påGrunnAvIndividuellForsikring,
            "Beløp på grunn av individuell forsikring",
        )
    }

    private fun assertBeløp(
        forventet: String,
        faktisk: BigDecimal,
        beskrivelse: String,
    ) {
        assertEquals(
            0,
            BigDecimal(forventet).compareTo(faktisk),
            "$beskrivelse: forventet $forventet, men var ${faktisk.stripTrailingZeros().toPlainString()}",
        )
    }

    private fun ventetidsdag(
        beløpTilBruker: Int,
        dekningsgrad: Int,
        dato: LocalDate = LocalDate.parse("2026-04-06"),
    ) = Utbetalingsdag(
        dato = dato,
        beløpTilBruker = beløpTilBruker,
        dekningsgrad = dekningsgrad,
        erIVentetid = true,
    )

    private fun navdag(
        beløpTilBruker: Int,
        dekningsgrad: Int,
        dato: LocalDate = LocalDate.parse("2026-04-22"),
    ) = Utbetalingsdag(
        dato = dato,
        beløpTilBruker = beløpTilBruker,
        dekningsgrad = dekningsgrad,
        erIVentetid = false,
    )

    private fun gyldigIndividuellForsikring(
        type: IndividuellForsikringType,
        opphørsdato: LocalDate? = null,
    ) = VurdertIndividuellForsikring.fraLagring(
        råkopiIfVedfrivt10Id = RåkopiIfVedfrivt10.Id.ny(),
        type = type,
        virkningsdato = LocalDate.parse("2026-01-01"),
        opphører = opphørsdato != null,
        opphørsdato = opphørsdato,
        premiegrunnlag = 0,
        erBetaltNoenGang = true,
        konklusjon = VurdertIndividuellForsikring.Konklusjon.GYLDIG,
    )
}
