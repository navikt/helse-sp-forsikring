package no.nav.helse.sykepenger.forsikring

import java.time.LocalDate

sealed interface Forsikring {
    fun opphørsdato(): LocalDate?
    fun dekningGrad(): Int
    fun dekningFraDag(): Int
}
