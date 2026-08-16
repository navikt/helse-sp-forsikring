package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import java.time.LocalDate

class VurdertNavKjøptForsikring private constructor(
    val råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
    val type: NavKjøptForsikringType,
    val virkningsdato: LocalDate,
    val opphører: Boolean,
    val opphørsdato: LocalDate?,
    val premiegrunnlag: Int,
    val erBetaltNoenGang: Boolean,
    val konklusjon: Konklusjon,
) {
    fun erGyldig() = konklusjon == Konklusjon.GYLDIG

    enum class Konklusjon(
        val folketrygdlovenReferanse: Folketrygdlovenreferanse?,
    ) {
        SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO(folketrygdlovenReferanse = null),
        SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO(folketrygdlovenReferanse = null),
        OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT(
            folketrygdlovenReferanse =
                Folketrygdlovenreferanse(
                    kapittel = 8,
                    paragrafIKapittel = 37,
                    ledd = null,
                    bokstav = null,
                ),
        ),
        ALDRI_BETALT(folketrygdlovenReferanse = null),
        GYLDIG(folketrygdlovenReferanse = null),
    }

    companion object {
        fun fraNavKjøptForsikringMedKonklusjon(
            navKjøptForsikring: NavKjøptForsikring,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            konklusjon: Konklusjon,
        ): VurdertNavKjøptForsikring {
            if (konklusjon == Konklusjon.GYLDIG) {
                navKjøptForsikring.type.validerMot(
                    yrkesaktivitetstype = yrkesaktivitetstype,
                    spesielleYrkesgrupper = spesielleYrkesgrupper,
                )
            }
            return VurdertNavKjøptForsikring(
                råkopiIfVedfrivt10Id = navKjøptForsikring.råkopiIfVedfrivt10Id,
                type = navKjøptForsikring.type,
                virkningsdato = navKjøptForsikring.virkningsdato,
                opphører = navKjøptForsikring.opphører,
                opphørsdato = navKjøptForsikring.opphørsdato,
                premiegrunnlag = navKjøptForsikring.premiegrunnlag,
                erBetaltNoenGang = navKjøptForsikring.erBetaltNoenGang,
                konklusjon = konklusjon,
            )
        }
    }
}
