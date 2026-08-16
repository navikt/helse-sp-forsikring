package no.nav.helse.sykepenger.forsikring.domain

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    val ledd: Int?,
    val bokstav: Char?,
)
