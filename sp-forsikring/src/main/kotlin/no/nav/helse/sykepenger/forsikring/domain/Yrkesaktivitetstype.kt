package no.nav.helse.sykepenger.forsikring.domain

enum class Yrkesaktivitetstype(
    val dekning: OrdinærDekning,
) {
    ARBEIDSTAKER(dekning = OrdinærDekning(grad = 100, fraDag = 1)),
    FRILANS(dekning = OrdinærDekning(grad = 100, fraDag = 17)),
    ARBEIDSLEDIG(dekning = OrdinærDekning(grad = 100, fraDag = 1)),
    SELVSTENDIG(dekning = OrdinærDekning(grad = 80, fraDag = 17)),
}
