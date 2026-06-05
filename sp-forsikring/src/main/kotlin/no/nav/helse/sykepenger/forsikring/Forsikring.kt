package no.nav.helse.sykepenger.forsikring

interface Forsikring {
    fun dekningGrad(): Int
    fun dekningFraDag(): Int
}
