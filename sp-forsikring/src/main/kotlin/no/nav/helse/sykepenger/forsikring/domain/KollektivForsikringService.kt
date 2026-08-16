package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo

class KollektivForsikringService {
    fun utledKollektiveForsikringer(spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>): Set<KollektivForsikring> =
        KollektivForsikring.entries
            .filter { kollektivForsikring -> kollektivForsikring.spesielleYrkesgrupper.any { it in spesielleYrkesgrupper } }
            .toSet()
            .also { kollektiveForsikringer ->
                loggInfo(
                    "Utledet ${kollektiveForsikringer.size} kollektiv(e) forsikring(er) for bruker basert på spesielle yrkesgrupper",
                    "spesielleYrkesgrupper" to spesielleYrkesgrupper.toString(),
                    "kollektiveForsikringer" to kollektiveForsikringer,
                )
            }
}
