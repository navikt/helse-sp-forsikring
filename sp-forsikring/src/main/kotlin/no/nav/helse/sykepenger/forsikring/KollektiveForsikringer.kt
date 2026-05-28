package no.nav.helse.sykepenger.forsikring

import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe.Fisker.Blad

data class KollektivForsikring(
    val spesiellYrkesgruppe: SpesiellYrkesgruppe,
    val dekningGrad: Int,
    val dekningFraDag: Int
) : Forsikring {
    override fun dekningGrad() = dekningGrad
    override fun dekningFraDag() = dekningFraDag
}

private val kollektiveForsikringer = listOf(
    KollektivForsikring(SpesiellYrkesgruppe.Fisker(Blad.B), 100, 1),
    KollektivForsikring(SpesiellYrkesgruppe.Jordbruker, 100, 17),
    KollektivForsikring(SpesiellYrkesgruppe.Reindrifter, 100, 17),
)

fun kollektiveForsikringerFor(spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>): List<KollektivForsikring> =
    kollektiveForsikringer.filter { it.spesiellYrkesgruppe in spesielleYrkesgrupper }
