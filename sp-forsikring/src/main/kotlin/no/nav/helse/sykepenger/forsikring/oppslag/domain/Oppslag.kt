package no.nav.helse.sykepenger.forsikring.oppslag.domain

import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.util.*

@JvmInline
value class OppslagId(
    val value: UUID,
) {
    companion object {
        fun ny() = OppslagId(generateUuidV7())
    }
}

data class Oppslag(
    val id: OppslagId,
    val navKjøpteForsikringer: List<NavKjøptForsikring>,
)
