package no.nav.helse.sykepenger.forsikring.oppslag

import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import java.util.*

data class Oppslag(
    val id: UUID,
    val navKjøpteForsikringer: List<NavKjøptForsikring>
)
