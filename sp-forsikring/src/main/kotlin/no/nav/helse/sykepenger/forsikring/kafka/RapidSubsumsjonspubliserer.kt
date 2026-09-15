package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonsmelding
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import no.nav.helse.sykepenger.forsikring.subsumsjon.tilSubsumsjonsmeldinger
import no.nav.sykepenger.libs.logging.loggInfo
import java.util.*

/**
 * Publiserer subsumsjoner på rapiden. Rivere lager én per melding de behandler, slik at subsumsjonene
 * publiseres i konteksten av meldingen, mens API-et bruker rapidsConnection direkte.
 */
class RapidSubsumsjonspubliserer(
    private val messageContext: MessageContext,
    private val versjonAvKode: String,
) : Subsumsjonspubliserer {
    override fun publiser(
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
                loggInfo("Sender subsumsjonsmelding", "subsumsjonsmelding" to subsumsjonsmelding)
                messageContext.publish(subsumsjonsmelding)
            }
    }
}
