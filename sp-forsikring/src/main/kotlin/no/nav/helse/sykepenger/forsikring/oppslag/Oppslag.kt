package no.nav.helse.sykepenger.forsikring.oppslag

import java.util.*

data class Oppslag(
    val id: UUID,
    val navKjøpteForsikringer: List<NavKjøptForsikring>
)
