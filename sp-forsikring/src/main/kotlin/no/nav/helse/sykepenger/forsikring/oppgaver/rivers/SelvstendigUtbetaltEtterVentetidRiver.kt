package no.nav.helse.sykepenger.forsikring.oppgaver.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.IForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.oppgaver.OppgaveoppretterClient
import no.nav.helse.sykepenger.forsikring.oppgaver.Årsak

class SelvstendigUtbetaltEtterVentetidRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsvurderingRepository: IForsikringsvurderingRepository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "selvstendig_utbetalt_etter_ventetid")
                it.requireKey("forsikringsvurderingId")
            }
            validate {
                it.requireKey("fødselsnummer", "@id", "skjæringstidspunkt")
            }
        }.register(this)
    }
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val forsikringsvurderingId = ForsikringsvurderingId.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        val forsikringsvurdering = forsikringsvurderingRepository.hent(forsikringsvurderingId)
            ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")

        if (forsikringsvurdering.forsikretMedDekningsgrad80ProsentFraDag1()) {
            runBlocking {
                oppgaveClient.lagOppgave(
                    meldingId,
                    fødselsnummer,
                    Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent,
                    skjæringstidspunkt
                )
            }
        }
    }

    private fun Forsikringsvurdering.forsikretMedDekningsgrad80ProsentFraDag1(): Boolean {
        return harForsikring && dekning?.iVentetid == true && dekning.grad == 80
    }
}
