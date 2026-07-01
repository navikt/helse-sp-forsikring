package no.nav.helse.sykepenger.forsikring.oppslag.domain

import java.util.*
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7

@JvmInline
value class OppslagIfVedrift10Id(val value: UUID) {
    companion object {
        fun ny() = OppslagIfVedrift10Id(generateUuidV7())
    }
}
