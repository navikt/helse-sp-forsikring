package no.nav.helse.sykepenger.forsikring.kalkulator

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.nav.helse.sykepenger.forsikring.Betaling
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring.Ekskluderingsårsak.VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT
import no.nav.helse.sykepenger.forsikring.RåForsikring
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private val SKJÆRINGSTIDSPUNKT = LocalDate.of(2026, 1, 1)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ForsikringsvurderingKalkulatorTest {
    private val kalkulator = ForsikringsvurderingKalkulator()

    // -----------------------------------------------------------------------
    // Grunnleggende resultater
    // -----------------------------------------------------------------------

    @Test
    fun `gir uten forsikring for tom liste`() {
        val resultat = kalkulator.kalkuler(emptyList(), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertNull(resultat.dekning)
        assertTrue(resultat.inkluderteRåForsikringer.isEmpty())
        assertTrue(resultat.ekskluderinger.isEmpty())
    }

    @ParameterizedTest(name = "type {0} -> grad {1} fraDag {2}")
    @CsvSource(
        "SELVSTENDIG_80_PROSENT_FRA_DAG_1,             80,  1",
        "SELVSTENDIG_100_PROSENT_FRA_DAG_17,           100, 17",
        "SELVSTENDIG_100_PROSENT_FRA_DAG_1,            100, 1",
        "SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1, 100, 1",
        "FRILANSER_100_PROSENT_FRA_DAG_1,              100, 1",
    )
    fun `gir forsikring med korrekt dekning for alle typer`(
        type: NavKjøptForsikring.Type,
        forventetGrad: Int,
        forventetFraDag: Int,
    ) {
        val resultat = kalkulator.kalkuler(listOf(aktivBetaltForsikring(type = type)), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
        assertEquals(Dekning(grad = forventetGrad, fraDag = forventetFraDag), resultat.dekning)
        assertEquals(1, resultat.inkluderteRåForsikringer.size)
        assertTrue(resultat.ekskluderinger.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Filter 1 — opptjeningstid
    // -----------------------------------------------------------------------

    @Test
    fun `ekskluderer forsikring der skjæringstidspunkt er i opptjeningstid`() {
        val forsikring = aktivBetaltForsikring(forsikringFom = LocalDate.of(2025, 12, 1), virkningsdato = LocalDate.of(2026, 2, 1))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID)
    }

    @Test
    fun `ekskluderer forsikring der skjæringstidspunkt er lik start av opptjeningstid`() {
        val forsikring = aktivBetaltForsikring(forsikringFom = SKJÆRINGSTIDSPUNKT, virkningsdato = LocalDate.of(2026, 2, 1))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID)
    }

    @Test
    fun `beholder forsikring der skjæringstidspunkt er lik virkningsdato (slutten av opptjeningstid)`() {
        val forsikring = aktivBetaltForsikring(forsikringFom = LocalDate.of(2025, 12, 1), virkningsdato = SKJÆRINGSTIDSPUNKT)

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    @Test
    fun `beholder forsikring uten opptjeningstid (forsikringFom er null)`() {
        val forsikring = aktivBetaltForsikring(forsikringFom = null, virkningsdato = SKJÆRINGSTIDSPUNKT)

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    // -----------------------------------------------------------------------
    // Filter 2 — virkningsdato
    // -----------------------------------------------------------------------

    @Test
    fun `ekskluderer forsikring der virkningsdato er etter skjæringstidspunkt`() {
        val forsikring = aktivBetaltForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT.plusDays(1))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT)
    }

    @Test
    fun `beholder forsikring der virkningsdato er lik skjæringstidspunkt`() {
        val forsikring = aktivBetaltForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT)

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    @Test
    fun `beholder forsikring der virkningsdato er før skjæringstidspunkt`() {
        val forsikring = aktivBetaltForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT.minusDays(1))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    // -----------------------------------------------------------------------
    // Filter 3 — opphørsdato
    // -----------------------------------------------------------------------

    @Test
    fun `ekskluderer forsikring der skjæringstidspunkt er etter opphørsdato`() {
        val forsikring = aktivBetaltForsikring(opphørsdato = SKJÆRINGSTIDSPUNKT.minusDays(1))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT)
    }

    @Test
    fun `beholder forsikring der skjæringstidspunkt er lik opphørsdato`() {
        val forsikring = aktivBetaltForsikring(opphørsdato = SKJÆRINGSTIDSPUNKT)

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    @Test
    fun `beholder forsikring uten opphørsdato`() {
        val forsikring = aktivBetaltForsikring(opphørsdato = null)

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    // -----------------------------------------------------------------------
    // Filter 4 — betaling
    // -----------------------------------------------------------------------

    @Test
    fun `ekskluderer forsikring uten betalinger`() {
        val forsikring = aktivBetaltForsikring(betalinger = emptyList())

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, ALDRI_BETALT)
    }

    @Test
    fun `ekskluderer forsikring der alle betalinger har betdato null`() {
        val forsikring = aktivBetaltForsikring(betalinger = listOf(Betaling(fom = null, tom = null, betdato = null)))

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertFalse(resultat.forsikret)
        assertEkskludering(resultat, forsikring, ALDRI_BETALT)
    }

    @Test
    fun `beholder forsikring der minst én betaling har betdato satt`() {
        val forsikring = aktivBetaltForsikring(
            betalinger = listOf(
                Betaling(fom = null, tom = null, betdato = null),
                Betaling(fom = null, tom = null, betdato = LocalDate.of(2026, 1, 1)),
            )
        )

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertTrue(resultat.forsikret)
    }

    // -----------------------------------------------------------------------
    // Dekning — valg av beste dekning
    // -----------------------------------------------------------------------

    @Test
    fun `velger dekning med lavest fraDag blant forsikringer med samme grad`() {
        val forsikring1 = aktivBetaltForsikring(type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17)
        val forsikring2 = aktivBetaltForsikring(type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1)

        val resultat = kalkulator.kalkuler(listOf(forsikring1, forsikring2), SKJÆRINGSTIDSPUNKT)

        assertEquals(Dekning(grad = 100, fraDag = 1), resultat.dekning)
        assertEquals(2, resultat.inkluderteRåForsikringer.size)
    }

    @Test
    fun `returnerer alle inkluderte forsikringer selv om bare én bestemmer dekning`() {
        val forsikring1 = aktivBetaltForsikring(type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17)
        val forsikring2 = aktivBetaltForsikring(type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1)

        val resultat = kalkulator.kalkuler(listOf(forsikring1, forsikring2), SKJÆRINGSTIDSPUNKT)

        assertTrue(forsikring1 in resultat.inkluderteRåForsikringer)
        assertTrue(forsikring2 in resultat.inkluderteRåForsikringer)
    }

    // -----------------------------------------------------------------------
    // Ekskluderinger — flere forsikringer, blandede resultater
    // -----------------------------------------------------------------------

    @Test
    fun `returnerer korrekte ekskluderingsårsaker for flere ekskluderte forsikringer`() {
        val iOpptjeningstid = aktivBetaltForsikring(
            forsikringFom = LocalDate.of(2025, 12, 1),
            virkningsdato = LocalDate.of(2026, 2, 1)
        )
        val virkningsdatoEtter = aktivBetaltForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT.plusDays(1))
        val opphørt = aktivBetaltForsikring(opphørsdato = SKJÆRINGSTIDSPUNKT.minusDays(1))
        val aldriDetalt = aktivBetaltForsikring(betalinger = emptyList())
        val aktiv = aktivBetaltForsikring(type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1)

        val resultat = kalkulator.kalkuler(
            listOf(iOpptjeningstid, virkningsdatoEtter, opphørt, aldriDetalt, aktiv),
            SKJÆRINGSTIDSPUNKT
        )

        assertTrue(resultat.forsikret)
        assertEquals(4, resultat.ekskluderinger.size)
        assertEkskludering(resultat, iOpptjeningstid, SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID)
        assertEkskludering(resultat, virkningsdatoEtter, VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT)
        assertEkskludering(resultat, opphørt, OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT)
        assertEkskludering(resultat, aldriDetalt, ALDRI_BETALT)
        assertTrue(aktiv in resultat.inkluderteRåForsikringer)
    }

    @Test
    fun `ekskluderingsrekkefølgen følger filter-prioritet (opptjeningstid vinner over virkningsdato)`() {
        // forsikringFom < skjæringstidspunkt < virkningsdato → både opptjeningstid- og virkningsdato-kriteriene treffer,
        // men opptjeningstid evalueres først og forsikringen tas ut av kandidatlisten
        val forsikring = aktivBetaltForsikring(
            forsikringFom = LocalDate.of(2025, 12, 1),
            virkningsdato = LocalDate.of(2026, 2, 1)
        )

        val resultat = kalkulator.kalkuler(listOf(forsikring), SKJÆRINGSTIDSPUNKT)

        assertEquals(1, resultat.ekskluderinger.size)
        assertEquals(SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID, resultat.ekskluderinger.single().årsak)
    }

    // -----------------------------------------------------------------------
    // Hjelpefunksjoner
    // -----------------------------------------------------------------------

    private fun aktivBetaltForsikring(
        id: Int = 1,
        type: NavKjøptForsikring.Type = NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
        forsikringFom: LocalDate? = null,
        virkningsdato: LocalDate = SKJÆRINGSTIDSPUNKT,
        opphørsdato: LocalDate? = null,
        betalinger: List<Betaling> = listOf(Betaling(fom = null, tom = null, betdato = LocalDate.of(2026, 1, 1))),
    ) = RåForsikring(
        id = id,
        type = type,
        forsikringFom = forsikringFom,
        virkningsdato = virkningsdato,
        opphørsdato = opphørsdato,
        betalinger = betalinger,
    )

    private fun assertEkskludering(
        resultat: KalkulatorResultat,
        forsikring: RåForsikring,
        forventetÅrsak: NavKjøptForsikring.Ekskluderingsårsak,
    ) {
        val ekskludering = resultat.ekskluderinger.find { it.råForsikring === forsikring }
        assertTrue(ekskludering != null, "Fant ingen ekskludering for forsikring $forsikring")
        assertEquals(forventetÅrsak, ekskludering.årsak)
    }
}
