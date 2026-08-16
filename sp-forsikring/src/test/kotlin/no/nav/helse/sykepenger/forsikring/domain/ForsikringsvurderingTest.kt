package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ForsikringsvurderingTest {
    @Test
    fun `gyldig nav-kjøpt forsikring gir forsikring med dekning`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer = listOf(navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        assertTrue(vurdering.harForsikring())
        assertTrue(vurdering.harNavKjøptForsikring())
        assertFalse(vurdering.harKollektivForsikring())
        assertEquals(Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_17, vurdering.dekning())
        assertNull(vurdering.opphørsdato())
    }

    @Test
    fun `opphørsdato blir med i vurderingen når forsikringen opphører etter skjæringstidspunktet`() {
        val opphørsdato = LocalDate.of(2026, 3, 31)
        val vurdering =
            vurdering(
                navKjøpteForsikringer =
                    listOf(
                        navKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            opphørsdato = opphørsdato,
                        ),
                    ),
            )

        assertTrue(vurdering.harForsikring())
        assertEquals(opphørsdato, vurdering.opphørsdato())
    }

    @Test
    fun `kollektiv forsikring gir forsikring uten nav-kjøpt forsikring`() {
        val vurdering =
            vurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
                kollektiveForsikringer = setOf(KollektivForsikring.FISKER_BLAD_B),
            )

        assertTrue(vurdering.harForsikring())
        assertTrue(vurdering.harKollektivForsikring())
        assertFalse(vurdering.harNavKjøptForsikring())
        assertEquals(Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1, vurdering.dekning())
    }

    @Test
    fun `nav-kjøpt tilleggsforsikring for jordbruker gir dekning fra første dag`() {
        val vurdering =
            vurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                kollektiveForsikringer = setOf(KollektivForsikring.JORDBRUKER),
                navKjøpteForsikringer =
                    listOf(navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)),
            )

        assertTrue(vurdering.harNavKjøptForsikring())
        assertTrue(vurdering.harKollektivForsikring())
        assertEquals(Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1, vurdering.dekning())
    }

    @Test
    fun `ugyldig kombinasjon av kollektiv og nav-kjøpt forsikring feiler`() {
        assertThrows<IllegalStateException> {
            vurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
                kollektiveForsikringer = setOf(KollektivForsikring.FISKER_BLAD_B),
                navKjøpteForsikringer =
                    listOf(navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1)),
            )
        }
    }

    @Test
    fun `mer enn én kollektiv forsikring feiler`() {
        assertThrows<IllegalStateException> {
            vurdering(
                spesielleYrkesgrupper =
                    setOf(SpesiellYrkesgruppe.FISKER_BLAD_B, SpesiellYrkesgruppe.JORDBRUKER),
                kollektiveForsikringer = setOf(KollektivForsikring.FISKER_BLAD_B, KollektivForsikring.JORDBRUKER),
            )
        }
    }

    @Test
    fun `mer enn én gyldig nav-kjøpt forsikring feiler`() {
        assertThrows<IllegalStateException> {
            vurdering(
                navKjøpteForsikringer =
                    listOf(
                        navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1),
                        navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17),
                    ),
            )
        }
    }

    @ParameterizedTest(name = "{0} med forsikringstype {1}", quoteTextArguments = false)
    @CsvSource(
        "ARBEIDSTAKER, SELVSTENDIG_80_PROSENT_FRA_DAG_1",
        "ARBEIDSTAKER, SELVSTENDIG_100_PROSENT_FRA_DAG_17",
        "ARBEIDSTAKER, SELVSTENDIG_100_PROSENT_FRA_DAG_1",
        "ARBEIDSTAKER, FRILANSER_100_PROSENT_FRA_DAG_1",
        "FRILANS, SELVSTENDIG_80_PROSENT_FRA_DAG_1",
        "FRILANS, SELVSTENDIG_100_PROSENT_FRA_DAG_17",
        "FRILANS, SELVSTENDIG_100_PROSENT_FRA_DAG_1",
        "ARBEIDSLEDIG, SELVSTENDIG_100_PROSENT_FRA_DAG_1",
    )
    fun `nav-kjøpt forsikring som ikke passer yrkesaktivitetstypen feiler`(
        yrkesaktivitetstype: Yrkesaktivitetstype,
        type: NavKjøptForsikringType,
    ) {
        assertThrows<NavKjøptForsikringType.Valideringsfeil> {
            vurdering(
                yrkesaktivitetstype = yrkesaktivitetstype,
                navKjøpteForsikringer = listOf(navKjøptForsikring(type = type)),
            )
        }
    }

    @Test
    fun `jordbrukerforsikring uten jordbruker som spesiell yrkesgruppe feiler`() {
        assertThrows<NavKjøptForsikringType.Valideringsfeil> {
            vurdering(
                navKjøpteForsikringer =
                    listOf(navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)),
            )
        }
    }

    @Test
    fun `forsikring med virkningsdato innen 28 dager etter skjæringstidspunktet er i opptjeningstiden`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer = listOf(navKjøptForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT.plusDays(1))),
            )

        assertFalse(vurdering.harForsikring())
        assertEquals(
            VurdertNavKjøptForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO,
            vurdering.navKjøpteForsikringer.single().konklusjon,
        )
    }

    @Test
    fun `forsikring med virkningsdato mer enn 28 dager etter skjæringstidspunktet er ikke virksom`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer = listOf(navKjøptForsikring(virkningsdato = SKJÆRINGSTIDSPUNKT.plusDays(29))),
            )

        assertFalse(vurdering.harForsikring())
        assertEquals(
            VurdertNavKjøptForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO,
            vurdering.navKjøpteForsikringer.single().konklusjon,
        )
    }

    @Test
    fun `forsikring som opphørte før skjæringstidspunktet gir ikke forsikring`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer =
                    listOf(
                        navKjøptForsikring(
                            virkningsdato = SKJÆRINGSTIDSPUNKT.minusYears(1),
                            opphørsdato = SKJÆRINGSTIDSPUNKT.minusDays(1),
                        ),
                    ),
            )

        assertFalse(vurdering.harForsikring())
        assertEquals(
            VurdertNavKjøptForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT,
            vurdering.navKjøpteForsikringer.single().konklusjon,
        )
    }

    @Test
    fun `forsikring som aldri er betalt gir ikke forsikring, men ville gitt det om den var betalt`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer = listOf(navKjøptForsikring(erBetaltNoenGang = false)),
            )

        assertFalse(vurdering.harForsikring())
        assertTrue(vurdering.villeHattForsikringOmDenVarBetalt())
        assertEquals(
            VurdertNavKjøptForsikring.Konklusjon.ALDRI_BETALT,
            vurdering.navKjøpteForsikringer.single().konklusjon,
        )
    }

    @Test
    fun `ubetalt forsikring fra dag 1 gir dekning i ventetiden uavhengig av betaling`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer =
                    listOf(
                        navKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            erBetaltNoenGang = false,
                        ),
                    ),
            )

        assertTrue(vurdering.harDekningIVentetidUavhengigAvBetaling())
    }

    @Test
    fun `forsikring fra dag 17 gir ikke dekning i ventetiden`() {
        val vurdering =
            vurdering(
                navKjøpteForsikringer = listOf(navKjøptForsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        assertFalse(vurdering.harDekningIVentetidUavhengigAvBetaling())
    }

    private fun vurdering(
        yrkesaktivitetstype: Yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe> = emptySet(),
        skjæringstidspunkt: LocalDate = SKJÆRINGSTIDSPUNKT,
        kollektiveForsikringer: Set<KollektivForsikring> = emptySet(),
        navKjøpteForsikringer: List<NavKjøptForsikring> = emptyList(),
    ): Forsikringsvurdering =
        Forsikringsvurdering.utførVurdering(
            identitetsnummer = Identitetsnummer.fraString(FØDSELSNUMMER),
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            skjæringstidspunkt = skjæringstidspunkt,
            råkopiId = Råkopi.Id.ny(),
            kollektiveForsikringer = kollektiveForsikringer,
            navKjøpteForsikringer = navKjøpteForsikringer,
        )

    private fun navKjøptForsikring(
        type: NavKjøptForsikringType = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
        virkningsdato: LocalDate = SKJÆRINGSTIDSPUNKT,
        opphørsdato: LocalDate? = null,
        premiegrunnlag: Int = 200000,
        erBetaltNoenGang: Boolean = true,
    ): NavKjøptForsikring =
        NavKjøptForsikring.ny(
            råkopiIfVedfrivt10Id = RåkopiIfVedfrivt10.Id.ny(),
            type = type,
            virkningsdato = virkningsdato,
            opphører = opphørsdato != null,
            opphørsdato = opphørsdato,
            premiegrunnlag = premiegrunnlag,
            erBetaltNoenGang = erBetaltNoenGang,
        )

    private companion object {
        const val FØDSELSNUMMER = "01020312345"
        val SKJÆRINGSTIDSPUNKT: LocalDate = LocalDate.parse("2026-01-01")
    }
}
