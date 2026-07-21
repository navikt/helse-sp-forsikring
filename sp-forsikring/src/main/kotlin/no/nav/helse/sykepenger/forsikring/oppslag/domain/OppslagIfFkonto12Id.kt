package no.nav.helse.sykepenger.forsikring.oppslag.domain

import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.util.*

@JvmInline
value class OppslagIfFkonto12Id(
    val value: UUID,
) {
    companion object {
        fun ny() = OppslagIfFkonto12Id(generateUuidV7())
    }
}
