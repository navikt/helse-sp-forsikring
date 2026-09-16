package no.nav.helse.sykepenger.forsikring.subsumsjon

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import java.util.*

interface Subsumsjonspubliserer {
    fun publiser(
        forsikringsvurdering: Forsikringsvurdering,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
    )
}
