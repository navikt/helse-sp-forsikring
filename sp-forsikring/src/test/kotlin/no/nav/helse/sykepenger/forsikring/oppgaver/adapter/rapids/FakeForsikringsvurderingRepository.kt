package no.nav.helse.sykepenger.forsikring.oppgaver.adapter.rapids

import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.seam.ForsikringsvurderingRepository

class FakeForsikringsvurderingRepository : ForsikringsvurderingRepository {
    private val vurderinger = mutableListOf<Forsikringsvurdering>()

    fun seed(forsikringsvurdering: Forsikringsvurdering) {
        vurderinger.add(forsikringsvurdering)
    }

    override fun lagre(forsikringsvurdering: Forsikringsvurdering, session: TransactionalSession) {
        vurderinger.add(forsikringsvurdering)
    }

    override fun hent(id: ForsikringsvurderingId): Forsikringsvurdering? =
        vurderinger.firstOrNull { it.id == id }
}
