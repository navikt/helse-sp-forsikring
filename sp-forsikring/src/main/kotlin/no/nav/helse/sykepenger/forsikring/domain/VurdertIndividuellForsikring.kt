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

    fun passerIkkeMedSøknadstype() = konklusjon == Konklusjon.PASSER_IKKE_MED_SØKNADSTYPE

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
                    versjon = LocalDate.parse("2019-10-01"),
                    ledd = null,
                    bokstav = null,
                    punktum = null,
                ),
        ),
        ALDRI_BETALT(folketrygdlovenReferanse = null),
        PASSER_IKKE_MED_SØKNADSTYPE(folketrygdlovenReferanse = null),
        GYLDIG(folketrygdlovenReferanse = null),
    }

    companion object {
        fun fraIndividuellForsikringMedKonklusjon(
            individuellForsikring: IndividuellForsikring,
            konklusjon: Konklusjon,
        ): VurdertIndividuellForsikring =
            VurdertIndividuellForsikring(
                råkopiIfVedfrivt10Id = individuellForsikring.råkopiIfVedfrivt10Id,
                type = individuellForsikring.type,
                virkningsdato = individuellForsikring.virkningsdato,
                opphører = individuellForsikring.opphører,
                opphørsdato = individuellForsikring.opphørsdato,
                premiegrunnlag = individuellForsikring.premiegrunnlag,
                erBetaltNoenGang = individuellForsikring.erBetaltNoenGang,
                konklusjon = konklusjon,
            )

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
