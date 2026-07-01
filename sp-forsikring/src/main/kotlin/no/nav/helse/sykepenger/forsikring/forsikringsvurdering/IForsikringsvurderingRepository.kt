package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession

interface IForsikringsvurderingRepository {
    fun lagre(forsikringsvurdering: Forsikringsvurdering, session: TransactionalSession)
    fun hent(id: ForsikringsvurderingId): Forsikringsvurdering?
}
