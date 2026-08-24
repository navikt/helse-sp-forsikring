package no.nav.helse.sykepenger.forsikring.domain

import java.math.BigDecimal
import java.math.RoundingMode

class FordelingAvBeløpPåUtbetalingsdag private constructor(
    val dag: Utbetalingsdag,
    val uavhengigAvForsikring: BigDecimal,
    val påGrunnAvKollektivForsikring: BigDecimal,
    val påGrunnAvNavKjøptForsikring: BigDecimal,
) {
    companion object {
        /**
         * Fordelingen per dag er en mellomregning: den summeres opp over alle dagene i et vedtak før beløpet
         * lagres med to desimaler. Vi regner derfor med rikelig med desimaler her, slik at avrundingsfeil ikke
         * akkumulerer seg dag for dag.
         */
        const val MELLOMREGNINGSSKALA = 10

        fun finnFordeling(
            dag: Utbetalingsdag,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            kollektivForsikring: KollektivForsikring?,
            navKjøptForsikring: VurdertNavKjøptForsikring?,
        ): FordelingAvBeløpPåUtbetalingsdag {
            if (dag.beløpTilBruker == 0) {
                return FordelingAvBeløpPåUtbetalingsdag(
                    dag = dag,
                    uavhengigAvForsikring = nullBeløp(),
                    påGrunnAvKollektivForsikring = nullBeløp(),
                    påGrunnAvNavKjøptForsikring = nullBeløp(),
                )
            }

            val ordinærGrad =
                yrkesaktivitetstype.dekning
                    .takeUnless { dekning -> dag.erIVentetid && !dekning.iVentetid() }
                    ?.grad
                    ?: 0

            val kollektivTilleggsgrad =
                kollektivForsikring
                    ?.takeUnless { dag.erIVentetid && !it.dekning.iVentetid() }
                    ?.let { it.dekning.grad - ordinærGrad }
                    ?: 0

            val navKjøptTilleggsgrad =
                navKjøptForsikring
                    ?.takeUnless { dag.erIVentetid && !it.type.dekning.iVentetid() }
                    ?.takeUnless { it.erOpphørtPå(dag.dato) }
                    ?.let { it.type.dekning.grad - ordinærGrad - kollektivTilleggsgrad }
                    ?: 0

            val forventetDekningsgrad = (ordinærGrad + kollektivTilleggsgrad + navKjøptTilleggsgrad)

            check(forventetDekningsgrad != 0) {
                // Spleis sender per nå ordinær dekningsgrad i disse tilfellene, så vi kan ikke se på den, men vi
                // bør kunne se at beløpet er 0. Da skulle vi aldri kommet hit ettersom vi hopper over slike dager.
                "Forventet at dag med dato ${dag.dato} ikke hadde noen utbetaling (egentlig dekningsgrad 0), men den hadde et beløp på ${dag.beløpTilBruker}"
            }

            check(dag.dekningsgrad == forventetDekningsgrad) {
                "Forventet at dag med dato ${dag.dato} hadde en dekningsgrad på $forventetDekningsgrad, men den var ${dag.dekningsgrad}"
            }

            val navKjøptForsikringBeløp = dag.beløpForGrad(navKjøptTilleggsgrad)
            val kollektivForsikringBeløp = dag.beløpForGrad(kollektivTilleggsgrad)

            return FordelingAvBeløpPåUtbetalingsdag(
                dag = dag,
                uavhengigAvForsikring =
                    BigDecimal(dag.beløpTilBruker)
                        .setScale(MELLOMREGNINGSSKALA)
                        .minus(kollektivForsikringBeløp)
                        .minus(navKjøptForsikringBeløp),
                påGrunnAvKollektivForsikring = kollektivForsikringBeløp,
                påGrunnAvNavKjøptForsikring = navKjøptForsikringBeløp,
            )
        }

        private fun nullBeløp(): BigDecimal = BigDecimal.ZERO.setScale(MELLOMREGNINGSSKALA)

        private fun Utbetalingsdag.beløpForGrad(grad: Int): BigDecimal =
            BigDecimal(beløpTilBruker)
                .multiply(BigDecimal(grad))
                .divide(BigDecimal(dekningsgrad), MELLOMREGNINGSSKALA, RoundingMode.HALF_UP)
    }
}
