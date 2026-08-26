package no.nav.helse.sykepenger.forsikring.domain

import java.time.LocalDate

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    /** Ikrafttredelsesdato for siste endring på paragrafen (kapittel + paragrafIKapittel) */
    val versjon: LocalDate,
    val ledd: Int?,
    val bokstav: Char?,
    val punktum: Int?,
)
