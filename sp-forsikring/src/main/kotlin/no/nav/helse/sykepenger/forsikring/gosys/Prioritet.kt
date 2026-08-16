package no.nav.helse.sykepenger.forsikring.gosys

import kotlinx.serialization.Serializable

@Serializable
enum class Prioritet {
    @Suppress("unused")
    HOY,
    NORM,

    @Suppress("unused")
    LAV,
}
