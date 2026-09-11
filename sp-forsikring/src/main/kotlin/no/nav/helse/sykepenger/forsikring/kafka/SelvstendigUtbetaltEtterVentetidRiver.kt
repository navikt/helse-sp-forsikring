package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaction
import no.nav.sykepenger.libs.logging.MdcKey
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

class SelvstendigUtbetaltEtterVentetidRiver(
    rapidsConnection: RapidsConnection,
    private val gosysOppgaveClient: GosysOppgaveClient,
    private val spForsikringDataSource: DataSource,
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
        packet.medParsetMeldingOgTransaction<SelvstendigUtbetaltEtterVentetidMelding>(
            mdcMapping = mapOf(MdcKey.FORSIKRINGSVURDERING_ID to SelvstendigUtbetaltEtterVentetidMelding::forsikringsvurderingId),
            dataSource = spForsikringDataSource,
        ) { melding, transaction ->
            val forsikringsvurderingId = Forsikringsvurdering.Id(melding.forsikringsvurderingId)

            val forsikringsvurdering =
                ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                    ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")

            if (forsikringsvurdering.gjeldendeIndividuellForsikring()?.type !in
                setOf(
                    IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                    IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                )
            ) {
                return@medParsetMeldingOgTransaction
            }

            gosysOppgaveClient.opprettOppgave(
                personident = melding.fødselsnummer,
                uuid = melding.id.toString(),
                beskrivelse =
                    "Bruker har forsikring som kun gir tilleggsykepenger i ventetid," +
                        " og har fått utbetalt sykepenger utover ventetid." +
                        " Utbetalingen skjedde for sykefravær med skjæringstidspunkt " +
                        "${melding.skjæringstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.",
            )
        }
    }
}
