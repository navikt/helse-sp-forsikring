package no.nav.helse.sykepenger.forsikring

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Int.toLocalDate() =
    if (this == 0) null else LocalDate.parse(this.toString().padStart(8, '0'), DateTimeFormatter.ofPattern("yyyyMMdd"))
