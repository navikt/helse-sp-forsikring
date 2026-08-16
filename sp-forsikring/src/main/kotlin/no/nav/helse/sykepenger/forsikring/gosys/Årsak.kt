package no.nav.helse.sykepenger.forsikring.gosys

import java.math.BigDecimal

sealed interface Årsak {
    object UtbetaltFraDagÉnOgDekningsgrad80Prosent : Årsak

    object UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker : Årsak

    object SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød : Årsak

    data class ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(
        val sykepengegrunnlag: BigDecimal,
        val premiegrunnlag: BigDecimal,
        val avviksprosent: BigDecimal,
    ) : Årsak
}
