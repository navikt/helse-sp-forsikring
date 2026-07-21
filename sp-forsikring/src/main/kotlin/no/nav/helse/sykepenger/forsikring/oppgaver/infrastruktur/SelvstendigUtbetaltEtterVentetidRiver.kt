package no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.oppgaver.OppgaveClient
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import java.util.*

class SelvstendigUtbetaltEtterVentetidRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveClient,
    private val forsikringsvurderingRepository: ForsikringsvurderingRepository,
    private val oppslagRepository: OppslagRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
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
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val forsikringsvurderingId = ForsikringsvurderingId.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        medMdc(MdcKey.MELDING_ID to meldingId.toString(), MdcKey.FORSIKRINGSVURDERING_ID to forsikringsvurderingId.toString()) {
            loggInfo("Mottok SelvstendigUtbetaltEtterVentetid-melding", "behov" to packet.toJson())

            val forsikringsvurdering =
                forsikringsvurderingRepository.hent(forsikringsvurderingId)
                    ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")

            if (!forsikringsvurdering.harForsikring || forsikringsvurdering.forsikringskategori == Forsikringskategori.KollektivForsikring) return@medMdc

            val navKjøptForsikring = hentNavKjøptForsikring(forsikringsvurdering, oppslagRepository, forsikringsvurderingId)
            val årsak =
                when (navKjøptForsikring.type) {
                    AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent
                    AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> Årsak.UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker
                    else -> return@medMdc
                }

            runBlocking {
                oppgaveClient.lagOppgave(
                    meldingId,
                    fødselsnummer,
                    årsak,
                    skjæringstidspunkt,
                )
            }
        }
    }
}
