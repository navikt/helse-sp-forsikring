package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import java.time.LocalDate

class IndividuellForsikring(
    val råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
    val type: IndividuellForsikringType,
    val virkningsdato: LocalDate,
    val opphører: Boolean,
    val opphørsdato: LocalDate?,
    val premiegrunnlag: Int,
    val erBetaltNoenGang: Boolean,
) {
    fun vurder(
        skjæringstidspunkt: LocalDate,
        yrkesaktivitetstype: Yrkesaktivitetstype,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    ): VurdertIndividuellForsikring =
        VurdertIndividuellForsikring.fraIndividuellForsikringMedKonklusjon(
            individuellForsikring = this,
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            konklusjon =
                when {
                    // Skjæringstidspunkt må ikke være i opptjeningstid [IF10_FORSFOM, IF10_VIRKDATO)
                    erInnen28DagerFørVirkningsdato(skjæringstidspunkt) ->
                        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO

                    // Skjæringstidspunkt må være etter eller lik virkningsdato
                    !harVirkningPå(skjæringstidspunkt) ->
                        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO

                    // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
                    erOpphørtPå(skjæringstidspunkt) ->
                        VurdertIndividuellForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT

                    // Forsikringen må være betalt noen gang
                    !erBetaltNoenGang ->
                        VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT

                    else ->
                        VurdertIndividuellForsikring.Konklusjon.GYLDIG
                },
        )

    private fun erInnen28DagerFørVirkningsdato(dato: LocalDate) = dato in virkningsdato.minusDays(28)..<virkningsdato

    private fun harVirkningPå(dato: LocalDate) = virkningsdato <= dato

    private fun erOpphørtPå(dato: LocalDate) = opphører && (opphørsdato == null || dato > opphørsdato)

    companion object {
        fun ny(
            råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
            type: IndividuellForsikringType,
            virkningsdato: LocalDate,
            opphører: Boolean,
            opphørsdato: LocalDate?,
            premiegrunnlag: Int,
            erBetaltNoenGang: Boolean,
        ): IndividuellForsikring =
            IndividuellForsikring(
                råkopiIfVedfrivt10Id = råkopiIfVedfrivt10Id,
                type = type,
                virkningsdato = virkningsdato,
                opphører = opphører,
                opphørsdato = opphørsdato,
                premiegrunnlag = premiegrunnlag,
                erBetaltNoenGang = erBetaltNoenGang,
            )
    }
}
