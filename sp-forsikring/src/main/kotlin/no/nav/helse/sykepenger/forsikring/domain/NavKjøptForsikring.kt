package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import java.time.LocalDate

class NavKjøptForsikring(
    val råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
    val type: NavKjøptForsikringType,
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
    ): VurdertNavKjøptForsikring =
        VurdertNavKjøptForsikring.fraNavKjøptForsikringMedKonklusjon(
            navKjøptForsikring = this,
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            konklusjon =
                when {
                    // Skjæringstidspunkt må ikke være i opptjeningstid [IF10_FORSFOM, IF10_VIRKDATO)
                    erInnen28DagerFørVirkningsdato(skjæringstidspunkt) ->
                        VurdertNavKjøptForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO

                    // Skjæringstidspunkt må være etter eller lik virkningsdato
                    !harVirkningPå(skjæringstidspunkt) ->
                        VurdertNavKjøptForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO

                    // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
                    erOpphørtPå(skjæringstidspunkt) ->
                        VurdertNavKjøptForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT

                    // Forsikringen må være betalt noen gang
                    !erBetaltNoenGang ->
                        VurdertNavKjøptForsikring.Konklusjon.ALDRI_BETALT

                    else ->
                        VurdertNavKjøptForsikring.Konklusjon.GYLDIG
                },
        )

    private fun erInnen28DagerFørVirkningsdato(dato: LocalDate) = dato in virkningsdato.minusDays(28)..<virkningsdato

    private fun harVirkningPå(dato: LocalDate) = virkningsdato <= dato

    private fun erOpphørtPå(dato: LocalDate) = opphører && (opphørsdato == null || dato > opphørsdato)

    companion object {
        fun ny(
            råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id,
            type: NavKjøptForsikringType,
            virkningsdato: LocalDate,
            opphører: Boolean,
            opphørsdato: LocalDate?,
            premiegrunnlag: Int,
            erBetaltNoenGang: Boolean,
        ): NavKjøptForsikring =
            NavKjøptForsikring(
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
