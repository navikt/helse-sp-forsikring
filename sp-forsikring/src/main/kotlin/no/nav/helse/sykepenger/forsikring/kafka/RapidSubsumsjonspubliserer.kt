package no.nav.helse.sykepenger.forsikring.kafka

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonsmelding
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import no.nav.helse.sykepenger.forsikring.subsumsjon.tilSubsumsjonsmeldinger
import no.nav.sykepenger.libs.logging.loggInfo
import java.util.*

class RapidSubsumsjonspubliserer(
    private val versjonAvKode: String,
) : Subsumsjonspubliserer {
    override fun publiser(
        outboxRepository: OutboxRepository,
        forsikringsvurdering: Forsikringsvurdering,
        vedtaksperiodeId: UUID,
        behandlingId: UUID,
    ) {
        forsikringsvurdering
            .tilSubsumsjonsmeldinger(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                versjonAvKode = versjonAvKode,
            ).map(Subsumsjonsmelding::tilJson)
            .forEach { subsumsjonsmelding ->
                loggInfo("Legger subsubmsjonsmelding i outbox", "subsumsjonsmelding" to subsumsjonsmelding)
                outboxRepository.leggTil(
                    key = forsikringsvurdering.input.identitetsnummer.value,
                    melding = subsumsjonsmelding,
                )
            }
    }
}
