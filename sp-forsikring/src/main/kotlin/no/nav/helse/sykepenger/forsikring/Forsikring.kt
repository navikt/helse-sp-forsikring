package no.nav.helse.sykepenger.forsikring

sealed interface Forsikring {
    fun dekningGrad(): Int
    fun dekningFraDag(): Int
}
