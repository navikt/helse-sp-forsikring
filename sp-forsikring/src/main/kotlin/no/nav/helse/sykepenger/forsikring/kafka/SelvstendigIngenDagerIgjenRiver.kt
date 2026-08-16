package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import java.util.*
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
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val forsikringsvurderingId = Forsikringsvurdering.Id.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        medMdc(
            MdcKey.MELDING_ID to meldingId.toString(),
            MdcKey.FORSIKRINGSVURDERING_ID to forsikringsvurderingId.toString(),
        ) {
            loggInfo("Mottok SelvstendigIngenDagerIgjen-melding", "behov" to packet.toJson())

            val forsikringsvurdering =
                spForsikringDataSource.inTransaction { transaction ->
                    ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                } ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")
            if (!forsikringsvurdering.harForsikring()) return@medMdc

            runBlocking {
                gosysOppgaveClient.lagOppgave(
                    duplikatkontrollId = meldingId,
                    fødselsnummer = fødselsnummer,
                    årsak = Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød,
                    skjæringstidspunkt = skjæringstidspunkt,
                )
            }
        }
    }
}
