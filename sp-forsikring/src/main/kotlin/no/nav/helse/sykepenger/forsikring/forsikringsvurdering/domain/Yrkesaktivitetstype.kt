package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain

enum class Yrkesaktivitetstype {
    ARBEIDSTAKER,
    FRILANS,
    ARBEIDSLEDIG,
    SELVSTENDIG,
}

sealed interface SpesiellYrkesgruppe {
    data class Fisker(val blad: Blad) : SpesiellYrkesgruppe {
        enum class Blad { A, B }
    }
    data object Jordbruker : SpesiellYrkesgruppe
    data object Reindrifter : SpesiellYrkesgruppe

    data class Ukjent(val value: String): SpesiellYrkesgruppe
}
