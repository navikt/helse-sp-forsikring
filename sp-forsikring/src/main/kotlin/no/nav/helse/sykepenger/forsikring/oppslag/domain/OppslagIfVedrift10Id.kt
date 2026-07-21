package no.nav.helse.sykepenger.forsikring.oppslag.domain

import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.util.*

@JvmInline
value class OppslagIfVedrift10Id(
    val value: UUID,
) {
    companion object {
        fun ny() = OppslagIfVedrift10Id(generateUuidV7())
    }
}
