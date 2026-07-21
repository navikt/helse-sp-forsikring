package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain

import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagIfVedrift10Id

sealed interface Forsikringskategori {
    data object KollektivForsikring : Forsikringskategori

    data class NavKjøptForsikring(
        val id: OppslagIfVedrift10Id,
    ) : Forsikringskategori

    fun navn() =
        when (this) {
            is KollektivForsikring -> Kategori.KOLLEKTIV.name
            is NavKjøptForsikring -> Kategori.NAVKJØPT.name
        }

    enum class Kategori {
        KOLLEKTIV,
        NAVKJØPT,
    }
}
