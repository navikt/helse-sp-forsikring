package no.nav.helse.sykepenger.forsikring.domain

data class OrdinærDekning(
    override val grad: Int,
    override val fraDag: Int,
) : Dekning
