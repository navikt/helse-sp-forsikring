package no.nav.helse.sykepenger.forsikring.domain

interface Dekning {
    val grad: Int
    val fraDag: Int

    fun iVentetid(): Boolean = fraDag == 1
}
