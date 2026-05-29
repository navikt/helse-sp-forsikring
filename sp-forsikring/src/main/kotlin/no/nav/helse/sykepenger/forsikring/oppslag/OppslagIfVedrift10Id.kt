package no.nav.helse.sykepenger.forsikring.oppslag

import java.util.*
import no.nav.helse.sykepenger.forsikring.generateUuidV7

@JvmInline
value class OppslagIfVedrift10Id(val value: UUID) {
    companion object {
        fun ny() = OppslagIfVedrift10Id(generateUuidV7())
    }
}
