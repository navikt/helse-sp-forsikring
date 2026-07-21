package no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur

import no.nav.helse.sykepenger.forsikring.oppslag.OppslagRepository
import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId

class FakeOppslagRepository : OppslagRepository {
    private val alleOppslag = mutableListOf<Oppslag>()

    override fun hent(id: OppslagId): Oppslag = alleOppslag.find { it.id == id } ?: Oppslag(id, emptyList())

    fun lagre(oppslag: Oppslag) {
        alleOppslag.add(oppslag)
    }
}
