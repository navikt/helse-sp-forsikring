package no.nav.helse.sykepenger.forsikring.oppslag

import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId

interface OppslagRepository {
    fun hent(id: OppslagId): Oppslag
}
