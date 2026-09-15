package no.nav.helse.sykepenger.forsikring.subsumsjon

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import java.util.*

/**
 * Publiserer subsumsjonene som følger av en forsikringsvurdering. Grensesnittet holder kunnskapen om rapiden
 * utenfor API-et og forretningslogikken, slik at de kun forholder seg til at subsumsjoner skal publiseres.
 */
interface Subsumsjonspubliserer {
    fun publiser(
        forsikringsvurdering: Forsikringsvurdering,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
    )
}
