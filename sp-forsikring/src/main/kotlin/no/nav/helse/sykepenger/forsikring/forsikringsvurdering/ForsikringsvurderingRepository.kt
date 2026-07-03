package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId

interface ForsikringsvurderingRepository {
    fun lagre(forsikringsvurdering: Forsikringsvurdering, session: TransactionalSession)
    fun hent(id: ForsikringsvurderingId): Forsikringsvurdering?
}
