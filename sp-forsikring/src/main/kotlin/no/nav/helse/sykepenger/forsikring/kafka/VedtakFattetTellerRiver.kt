package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.telling.infrastruktur.TellingDao
import tools.jackson.databind.JsonNode
import java.time.ZoneId
import java.util.*
import javax.sql.DataSource

class VedtakFattetTellerRiver(
    rapidsConnection: RapidsConnection,
    private val spForsikringDataSource: DataSource,
    private val tellingDao: TellingDao,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "vedtak_fattet")
                    it.requireValue("yrkesaktivitetstype", "SELVSTENDIG")
                    it.requireKey("forsikringsvurderingId")
                }
                validate {
                    it.requireKey("fødselsnummer", "vedtaksperiodeId", "@id", "vedtakFattetTidspunkt")
                    it.requireArray("utbetalingsdager")
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
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asString())
        val vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toInstant()
        val forsikringsvurderingId = Forsikringsvurdering.Id.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        medMdc(MdcKey.MELDING_ID to meldingId.toString(), MdcKey.FORSIKRINGSVURDERING_ID to forsikringsvurderingId.toString()) {
            loggInfo("Mottok VedtakFattet-melding", "behov" to packet.toJson())
            val forsikringsvurdering =
                spForsikringDataSource.inTransaction { transaction ->
                    ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                } ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")
            if (!forsikringsvurdering.harForsikring() || !forsikringsvurdering.harNavKjøptForsikring()) {
                return@medMdc
            }

            val dager: List<Dag> =
                packet["utbetalingsdager"].mapArray {
                    Dag(
                        iVentetid = it["type"].asString().equals("ventetidsdag", ignoreCase = true),
                        beløpTilBruker = it["beløpTilBruker"].asInt(),
                    )
                }

            val utbetalingIVentetid = dager.filter { it.iVentetid }.sumOf { it.beløpTilBruker }
            val utbetalingUtenomVentetid = dager.filterNot { it.iVentetid }.sumOf { it.beløpTilBruker }

            tellingDao.lagre(
                id = meldingId,
                fødselsnummer = fødselsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                dekningsgrad = forsikringsvurdering.dekning()!!.grad,
                harDekningIVentetid = forsikringsvurdering.dekning()!!.fraDag == 1,
                utbetaltIVentetid = utbetalingIVentetid,
                utbetaltUtenomVentetid = utbetalingUtenomVentetid,
                json = packet.toJson(),
            )
        }
    }
}

data class Dag(
    val iVentetid: Boolean,
    val beløpTilBruker: Int,
)

private fun <T> JsonNode.mapArray(fn: (JsonNode) -> T): List<T> = IntRange(0, size() - 1).map { fn(get(it)) }
