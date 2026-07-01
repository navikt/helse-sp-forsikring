package no.nav.helse.sykepenger.forsikring.oppgaver.domain

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

fun JsonNode.asUuid(): UUID = UUID.fromString(asText())

fun String.toBigDecimal(scale: Int = 10): BigDecimal {
    return BigDecimal(this).setScale(scale, RoundingMode.HALF_UP)
}

/**
 * Beregner og returnerer prosentvis avvik mellom sykepengegrunnlag og premiegrunnlag.
 *
 * @param sykepengegrunnlag Det beregnede sykepengegrunnlaget.
 * @param premiegrunnlag Det registrerte premiegrunnlaget.
 * @return prosent avvik mellom sykepengegrunnlaget og premiegrunnlaget
 *
 * Formel: Avvik (%) = ((Fastsatt sykepengegrunnlag - fastsatt premiegrunnlag) / fastsatt sykepengegrunnlag) * 100
 */

fun beregnAvvik(sykepengegrunnlag: BigDecimal, premiegrunnlag: BigDecimal): BigDecimal {
    val differansen = (sykepengegrunnlag.subtract(premiegrunnlag)).abs()
    return differansen.divide(sykepengegrunnlag, RoundingMode.HALF_UP).multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
}
