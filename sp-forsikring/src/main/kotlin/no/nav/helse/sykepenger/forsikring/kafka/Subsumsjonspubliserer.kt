package no.nav.helse.sykepenger.forsikring.subsumsjon

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.kafka.OutboxRepository
import java.util.*

interface Subsumsjonspubliserer {
    fun publiser(
        outboxRepository: OutboxRepository,
        forsikringsvurdering: Forsikringsvurdering,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
    )
}
