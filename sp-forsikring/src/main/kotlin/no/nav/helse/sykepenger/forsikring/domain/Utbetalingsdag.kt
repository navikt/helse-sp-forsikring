package no.nav.helse.sykepenger.forsikring.domain

import java.time.LocalDate

data class Utbetalingsdag(
    val dato: LocalDate,
    val beløpTilBruker: Int,
    val dekningsgrad: Int,
    val erIVentetid: Boolean,
)
