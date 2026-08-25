package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import java.time.LocalDate

class VurdertIndividuellForsikring private constructor(
    val råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
    val type: IndividuellForsikringType,
    val virkningsdato: LocalDate,
    val opphører: Boolean,
    val opphørsdato: LocalDate?,
    val premiegrunnlag: Int,
    val erBetaltNoenGang: Boolean,
    val konklusjon: Konklusjon,
) {
    fun erGyldig() = konklusjon == Konklusjon.GYLDIG

    fun erOpphørtPå(dato: LocalDate) = opphørsdato != null && dato > opphørsdato

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
        fun fraIndividuellForsikringMedKonklusjon(
            individuellForsikring: IndividuellForsikring,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            konklusjon: Konklusjon,
        ): VurdertIndividuellForsikring {
            if (konklusjon == Konklusjon.GYLDIG) {
                individuellForsikring.type.validerMot(
                    yrkesaktivitetstype = yrkesaktivitetstype,
                    spesielleYrkesgrupper = spesielleYrkesgrupper,
                )
            }
            return VurdertIndividuellForsikring(
                råkopiIfVedfrivt10Id = individuellForsikring.råkopiIfVedfrivt10Id,
                type = individuellForsikring.type,
                virkningsdato = individuellForsikring.virkningsdato,
                opphører = individuellForsikring.opphører,
                opphørsdato = individuellForsikring.opphørsdato,
                premiegrunnlag = individuellForsikring.premiegrunnlag,
                erBetaltNoenGang = individuellForsikring.erBetaltNoenGang,
                konklusjon = konklusjon,
            )
        }

        fun fraLagring(
            råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
            type: IndividuellForsikringType,
            virkningsdato: LocalDate,
            opphører: Boolean,
            opphørsdato: LocalDate?,
            premiegrunnlag: Int,
            erBetaltNoenGang: Boolean,
            konklusjon: Konklusjon,
        ): VurdertIndividuellForsikring =
            VurdertIndividuellForsikring(
                råkopiIfVedfrivt10Id = råkopiIfVedfrivt10Id,
                type = type,
                virkningsdato = virkningsdato,
                opphører = opphører,
                opphørsdato = opphørsdato,
                premiegrunnlag = premiegrunnlag,
                erBetaltNoenGang = erBetaltNoenGang,
                konklusjon = konklusjon,
            )
    }
}
