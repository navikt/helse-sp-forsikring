package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FordelingAvBeløpPåUtbetalingsdagTest {
    @Test
    fun `uten forsikring tilskrives hele utbetalingen den ordinære dekningen`() {
        val fordeling = fordeling(dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80))

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = 1000)
    }

    @Test
    fun `nav-kjøpt forsikring med samme grad som den ordinære dekningen bidrar bare i ventetiden`() {
        val navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)

        val iVentetiden =
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 80),
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(fordeling = iVentetiden, uavhengigAvForsikring = 0, påGrunnAvNavKjøptForsikring = 100)

        val utenomVentetiden =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80),
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(fordeling = utenomVentetiden, uavhengigAvForsikring = 1000, påGrunnAvNavKjøptForsikring = 0)
    }

    @Test
    fun `nav-kjøpt forsikring med høyere grad bidrar med differansen mot den ordinære dekningen`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )

        // (100 - 80) % av 1000
        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = 800, påGrunnAvNavKjøptForsikring = 200)
    }

    @Test
    fun `kollektiv forsikring bidrar med differansen mot den ordinære dekningen`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = 800, påGrunnAvKollektivForsikring = 200)
    }

    @Test
    fun `nav-kjøpt tilleggsforsikring bidrar bare med det den gir utover den kollektive forsikringen`() {
        val kollektivForsikring = KollektivForsikring.JORDBRUKER
        val navKjøptForsikring =
            gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)

        // I ventetiden gir den kollektive forsikringen ingenting, siden den først gjelder fra dag 17
        val iVentetiden =
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 100),
                kollektivForsikring = kollektivForsikring,
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(
            fordeling = iVentetiden,
            uavhengigAvForsikring = 0,
            påGrunnAvKollektivForsikring = 0,
            påGrunnAvNavKjøptForsikring = 100,
        )

        // Fra dag 17 har den kollektive forsikringen tatt over, og tilleggsforsikringen gir ikke noe ekstra
        val utenomVentetiden =
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                kollektivForsikring = kollektivForsikring,
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(
            fordeling = utenomVentetiden,
            uavhengigAvForsikring = 800,
            påGrunnAvKollektivForsikring = 200,
            påGrunnAvNavKjøptForsikring = 0,
        )
    }

    @Test
    fun `nav-kjøpt forsikring bidrar ikke etter opphørsdato`() {
        val navKjøptForsikring =
            gyldigNavKjøptForsikring(
                type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                opphørsdato = LocalDate.parse("2026-04-22"),
            )

        val påOpphørsdatoen =
            fordeling(
                dag = navdag(dato = LocalDate.parse("2026-04-22"), beløpTilBruker = 1000, dekningsgrad = 100),
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(fordeling = påOpphørsdatoen, uavhengigAvForsikring = 800, påGrunnAvNavKjøptForsikring = 200)

        // Etter opphør faller dagen tilbake til ordinær dekning, og dekningsgraden er da 80
        val etterOpphørsdatoen =
            fordeling(
                dag = navdag(dato = LocalDate.parse("2026-04-23"), beløpTilBruker = 1000, dekningsgrad = 80),
                navKjøptForsikring = navKjøptForsikring,
            )
        assertFordeling(fordeling = etterOpphørsdatoen, uavhengigAvForsikring = 1000, påGrunnAvNavKjøptForsikring = 0)
    }

    @Test
    fun `runder av til nærmeste krone`() {
        val fordeling =
            fordeling(
                dag = navdag(beløpTilBruker = 1003, dekningsgrad = 100),
                navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )

        // (100 - 80) % av 1003 er 200,6, som rundes til 201
        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = 802, påGrunnAvNavKjøptForsikring = 201)
    }

    @Test
    fun `dager uten utbetaling fordeles ikke, uavhengig av dekningsgraden på dagen`() {
        val fordeling = fordeling(dag = navdag(beløpTilBruker = 0, dekningsgrad = 100))

        assertFordeling(fordeling = fordeling, uavhengigAvForsikring = 0)
    }

    @Test
    fun `feiler når det er utbetalt mer enn dekningene skulle gitt`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 100),
                navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1),
            )
        }
    }

    @Test
    fun `feiler når det er utbetalt mindre enn dekningene skulle gitt`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = navdag(beløpTilBruker = 1000, dekningsgrad = 80),
                navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1),
            )
        }
    }

    @Test
    fun `feiler når det er utbetalt i ventetiden for en forsikring som ikke dekker ventetiden`() {
        assertThrows<IllegalStateException> {
            fordeling(
                dag = ventetidsdag(beløpTilBruker = 100, dekningsgrad = 100),
                navKjøptForsikring = gyldigNavKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17),
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
        navKjøptForsikring: VurdertNavKjøptForsikring? = null,
    ) = FordelingAvBeløpPåUtbetalingsdag.finnFordeling(
        dag = dag,
        yrkesaktivitetstype = yrkesaktivitetstype,
        kollektivForsikring = kollektivForsikring,
        navKjøptForsikring = navKjøptForsikring,
    )

    private fun assertFordeling(
        fordeling: FordelingAvBeløpPåUtbetalingsdag,
        uavhengigAvForsikring: Int,
        påGrunnAvKollektivForsikring: Int = 0,
        påGrunnAvNavKjøptForsikring: Int = 0,
    ) {
        assertEquals(uavhengigAvForsikring, fordeling.uavhengigAvForsikring, "Beløp uavhengig av forsikring")
        assertEquals(
            påGrunnAvKollektivForsikring,
            fordeling.påGrunnAvKollektivForsikring,
            "Beløp på grunn av kollektiv forsikring",
        )
        assertEquals(
            påGrunnAvNavKjøptForsikring,
            fordeling.påGrunnAvNavKjøptForsikring,
            "Beløp på grunn av nav-kjøpt forsikring",
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

    private fun gyldigNavKjøptForsikring(
        type: NavKjøptForsikringType,
        opphørsdato: LocalDate? = null,
    ) = VurdertNavKjøptForsikring.fraLagring(
        råkopiIfVedfrivt10Id = RåkopiIfVedfrivt10.Id.ny(),
        type = type,
        virkningsdato = LocalDate.parse("2026-01-01"),
        opphører = opphørsdato != null,
        opphørsdato = opphørsdato,
        premiegrunnlag = 0,
        erBetaltNoenGang = true,
        konklusjon = VurdertNavKjøptForsikring.Konklusjon.GYLDIG,
    )
}
