package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

interface IForsikringsvurderingRepository {
    fun lagre(forsikringsvurdering: Forsikringsvurdering)
    fun hent(id: ForsikringsvurderingId): Forsikringsvurdering?
}
