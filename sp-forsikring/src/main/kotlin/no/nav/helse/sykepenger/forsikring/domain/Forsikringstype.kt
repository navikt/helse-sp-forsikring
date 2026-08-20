package no.nav.helse.sykepenger.forsikring.domain

sealed interface Forsikringstype {
    val dekning: Forsikringsdekning
}
