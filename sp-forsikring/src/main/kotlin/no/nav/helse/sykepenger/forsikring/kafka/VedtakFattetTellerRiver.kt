package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.Forsikringstype
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.sql.DataSource

class VedtakFattetTellerRiver(
    rapidsConnection: RapidsConnection,
    private val spForsikringDataSource: DataSource,
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
                    it.requireKey("fødselsnummer", "behandlingId", "@id", "vedtakFattetTidspunkt")
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
        val behandlingId = UUID.fromString(packet["behandlingId"].asString())
        val vedtakFattetTidspunkt =
            packet["vedtakFattetTidspunkt"].asLocalDateTime().atZone(ZoneId.of("Europe/Oslo")).toInstant()
        val forsikringsvurderingId = Forsikringsvurdering.Id.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        medMdc(
            MdcKey.MELDING_ID to meldingId.toString(),
            MdcKey.FORSIKRINGSVURDERING_ID to forsikringsvurderingId.toString(),
        ) {
            loggInfo("Mottok VedtakFattet-melding", "melding" to packet.toJson())
            spForsikringDataSource.inTransaction { transaction ->
                val vedtakFattetMeldingDao = VedtakFattetMeldingDao(transaction)
                val utbetalingPerForsikringstypeDao = UtbetalingPerForsikringstypeDao(transaction)

                if (vedtakFattetMeldingDao.eksisterer(meldingId)) {
                    loggInfo("Hopper over vedtak_fattet-melding som allerede er lagret ned")
                }

                vedtakFattetMeldingDao.lagre(
                    id = meldingId,
                    forsikringsvurderingId = forsikringsvurderingId,
                    identitetsnummer = Identitetsnummer.fraString(fødselsnummer),
                    behandlingId = behandlingId,
                    vedtakFattetTidspunkt = vedtakFattetTidspunkt,
                    json = packet.toJson(),
                )
                val forsikringsvurdering =
                    ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                        ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")
                if (!forsikringsvurdering.harForsikring()) {
                    return@inTransaction
                }

                val dager: List<Dag> =
                    packet["utbetalingsdager"].mapArray {
                        Dag(
                            iVentetid = it["type"].asString().equals("ventetidsdag", ignoreCase = true),
                            beløpTilBruker = it["beløpTilBruker"].asInt(),
                            dato = it["dato"].asLocalDate(),
                            dekningsgrad = it["dekningsgrad"].asInt(),
                        )
                    }

                val navKjøptForsikring = forsikringsvurdering.gjeldendeNavKjøptForsikring()
                val kollektivForsikring = forsikringsvurdering.kollektivForsikring
                if (navKjøptForsikring != null && kollektivForsikring != null) {
                    if (navKjøptForsikring.type.tilleggsforsikringFor != kollektivForsikring) {
                        error(
                            "Bruker har en ugyldig kombinasjon av kollektiv og nav-kjøpt forsikring." +
                                " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                                " til økt utbetaling (med tanke på senere justering av premiesats)",
                        )
                    }

                    utbetalingPerForsikringstypeDao.lagre(
                        id = UUID.randomUUID(),
                        vedtakFattetMeldingId = meldingId,
                        forsikringstype = Forsikringstype.NavKjøpt(navKjøptForsikring.type),
                        utbetaltIVentetid = dager.filter { it.iVentetid }.sumOf { it.beløpTilBruker },
                        utbetaltUtenomVentetid = 0,
                    )
                    utbetalingPerForsikringstypeDao.lagre(
                        id = UUID.randomUUID(),
                        vedtakFattetMeldingId = meldingId,
                        forsikringstype = Forsikringstype.Kollektiv(kollektivForsikring),
                        utbetaltIVentetid = 0,
                        utbetaltUtenomVentetid =
                            dager
                                .filterNot { it.iVentetid }
                                .sumOf { it.beløpTilBruker } * (kollektivForsikring.dekning.grad - 80) / 100,
                    )
                } else {
                    if (navKjøptForsikring != null) {
                        val dagerIkkeOpphørt =
                            dager.filter { navKjøptForsikring.opphørsdato == null || it.dato <= navKjøptForsikring.opphørsdato }
                        if (!dagerIkkeOpphørt.all { it.dekningsgrad == navKjøptForsikring.type.dekning.grad }) {
                            error("Utbetalingsdager har ulik dekningsgrad fra forsikringstype ${navKjøptForsikring.type}")
                        }
                        val utbetaltIVentetid =
                            dagerIkkeOpphørt
                                .filter { it.iVentetid }
                                .sumOf { it.beløpTilBruker }

                        if (navKjøptForsikring.type.dekning.fraDag != 1 && utbetaltIVentetid > 0) {
                            error("Utbetaling i ventetid for forsikringstype ${navKjøptForsikring.type} som ikke har dekning fra dag 1")
                        }

                        val utbetaltUtenomVentetid =
                            dagerIkkeOpphørt
                                .filterNot { it.iVentetid }
                                .sumOf { it.beløpTilBruker } * (navKjøptForsikring.type.dekning.grad - (if (navKjøptForsikring.type.yrkesaktivitetstype == Yrkesaktivitetstype.SELVSTENDIG) 80 else 100)) / 100

                        utbetalingPerForsikringstypeDao.lagre(
                            id = UUID.randomUUID(),
                            vedtakFattetMeldingId = meldingId,
                            forsikringstype = Forsikringstype.NavKjøpt(navKjøptForsikring.type),
                            utbetaltIVentetid = utbetaltIVentetid,
                            utbetaltUtenomVentetid = utbetaltUtenomVentetid,
                        )
                    } else if (kollektivForsikring != null) {
                        if (!dager.all { it.dekningsgrad == kollektivForsikring.dekning.grad }) {
                            error("Utbetalingsdager har ulik dekningsgrad fra forsikringstype $kollektivForsikring")
                        }
                        val utbetaltIVentetid = dager.filter { it.iVentetid }.sumOf { it.beløpTilBruker }

                        if (kollektivForsikring.dekning.fraDag != 1 && utbetaltIVentetid > 0) {
                            error("Utbetaling i ventetid for forsikringstype $kollektivForsikring som ikke har dekning fra dag 1")
                        }

                        val utbetaltUtenomVentetid =
                            dager
                                .filterNot { it.iVentetid }
                                .sumOf { it.beløpTilBruker } * (kollektivForsikring.dekning.grad - 80) / 100

                        utbetalingPerForsikringstypeDao.lagre(
                            id = UUID.randomUUID(),
                            vedtakFattetMeldingId = meldingId,
                            forsikringstype = Forsikringstype.Kollektiv(kollektivForsikring),
                            utbetaltIVentetid = utbetaltIVentetid,
                            utbetaltUtenomVentetid = utbetaltUtenomVentetid,
                        )
                    }
                }
            }
        }
    }
}

data class Dag(
    val iVentetid: Boolean,
    val beløpTilBruker: Int,
    val dato: LocalDate,
    val dekningsgrad: Int,
)

private fun <T> JsonNode.mapArray(fn: (JsonNode) -> T): List<T> = IntRange(0, size() - 1).map { fn(get(it)) }
