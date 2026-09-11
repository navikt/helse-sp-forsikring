package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaction
import no.nav.sykepenger.libs.logging.MdcKey
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

class SelvstendigIngenDagerIgjenRiver(
    rapidsConnection: RapidsConnection,
    private val gosysOppgaveClient: GosysOppgaveClient,
    private val spForsikringDataSource: DataSource,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "selvstendig_ingen_dager_igjen")
                    it.requireKey("forsikringsvurderingId")
                }
                validate {
                    it.requireKey(
                        "skjæringstidspunkt",
                        "fødselsnummer",
                        "@id",
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        packet.medParsetMeldingOgTransaction<SelvstendigIngenDagerIgjenMelding>(
            mdcMapping = mapOf(MdcKey.FORSIKRINGSVURDERING_ID to SelvstendigIngenDagerIgjenMelding::forsikringsvurderingId),
            dataSource = spForsikringDataSource,
        ) { melding, transaction ->
            val forsikringsvurderingId = Forsikringsvurdering.Id(melding.forsikringsvurderingId)

            val forsikringsvurdering =
                ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                    ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")
            if (!forsikringsvurdering.harForsikring()) return@medParsetMeldingOgTransaction

            gosysOppgaveClient.opprettOppgave(
                personident = melding.fødselsnummer,
                uuid = melding.id.toString(),
                beskrivelse =
                    "Brukers rett til sykepenger har opphørt som en følge av" +
                        " at maks antall sykepengedager er nådd," +
                        " brukeren har fylt 70," +
                        " eller brukeren er død." +
                        " Inntraff i forbindelse med sykefravær med skjæringstidspunkt " +
                        "${melding.skjæringstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.",
            )
        }
    }
}
