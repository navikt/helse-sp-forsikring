package no.nav.helse.sykepenger.forsikring.oppgaver.domain

import java.math.BigDecimal

sealed interface Årsak {
    object UtbetaltFraDagÉnOgDekningsgrad80Prosent : Årsak
    object SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød : Årsak
    data class ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(val sykepengegrunnlag: BigDecimal, val premiegrunnlag: BigDecimal, val avviksprosent: BigDecimal) : Årsak
}
