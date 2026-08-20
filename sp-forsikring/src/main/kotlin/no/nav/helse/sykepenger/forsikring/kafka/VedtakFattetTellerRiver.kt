package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetMelding.Utbetalingsdag.Type
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaksjon
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.Forsikringstype
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        packet.medParsetMeldingOgTransaksjon<VedtakFattetMelding>(
            mdcMapping =
                mapOf(
                    MdcKey.MELDING_ID to VedtakFattetMelding::id,
                    MdcKey.FORSIKRINGSVURDERING_ID to VedtakFattetMelding::forsikringsvurderingId,
                ),
            dataSource = spForsikringDataSource,
        ) { vedtakFattetMelding, transaction ->
            val vedtakFattetMeldingDao = VedtakFattetMeldingDao(transaction)

            if (vedtakFattetMeldingDao.eksisterer(vedtakFattetMelding.id)) {
                loggInfo("Hopper over vedtak_fattet-melding som allerede er lagret ned")
                return@medParsetMeldingOgTransaksjon
            }

            val forsikringsvurdering =
                vedtakFattetMelding.forsikringsvurderingId
                    ?.let { Forsikringsvurdering.Id(it) }
                    ?.let {
                        ForsikringsvurderingRepository(transaction).hent(it)
                            ?: error("Fant ikke forsikringsvurdering med $it")
                    }

            vedtakFattetMeldingDao.insert(
                id = vedtakFattetMelding.id,
                forsikringsvurderingId = forsikringsvurdering?.id,
                identitetsnummer = Identitetsnummer.fraString(vedtakFattetMelding.fødselsnummer),
                behandlingId = vedtakFattetMelding.behandlingId,
                vedtakFattetTidspunkt = vedtakFattetMelding.vedtakFattetTidspunkt.tilInstantIOslo(),
                json = packet.toJson(),
            )

            if (forsikringsvurdering == null || !forsikringsvurdering.harForsikring()) {
                return@medParsetMeldingOgTransaksjon
            }

            val navKjøptForsikring = forsikringsvurdering.gjeldendeNavKjøptForsikring()
            val kollektivForsikring = forsikringsvurdering.kollektivForsikring
            val utbetalingPerForsikringstypeDao = UtbetalingPerForsikringstypeDao(transaction)
            if (navKjøptForsikring != null && kollektivForsikring != null) {
                if (navKjøptForsikring.type.tilleggsforsikringFor != kollektivForsikring) {
                    error(
                        "Bruker har en ugyldig kombinasjon av kollektiv og nav-kjøpt forsikring." +
                            " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                            " til økt utbetaling (med tanke på senere justering av premiesats)",
                    )
                }

                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = Forsikringstype.NavKjøpt(navKjøptForsikring.type),
                    utbetaltIVentetid =
                        vedtakFattetMelding.utbetalingsdager
                            .filter { it.erIVentetid() }
                            .sumOf { it.beløpTilBruker },
                    utbetaltUtenomVentetid = 0,
                )
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = Forsikringstype.Kollektiv(kollektivForsikring),
                    utbetaltIVentetid = 0,
                    utbetaltUtenomVentetid =
                        vedtakFattetMelding.utbetalingsdager
                            .filterNot { it.erIVentetid() }
                            .sumOf { it.beløpTilBruker }
                            .times(kollektivForsikring.dekning.grad - 80)
                            .div(100),
                )
            } else if (navKjøptForsikring != null) {
                val dagerIkkeOpphørt =
                    vedtakFattetMelding.utbetalingsdager
                        .filterNot { navKjøptForsikring.erOpphørtPå(it.dato) }

                if (!dagerIkkeOpphørt.all { it.dekningsgrad == navKjøptForsikring.type.dekning.grad }) {
                    error("Utbetalingsdager har ulik dekningsgrad fra forsikringstype ${navKjøptForsikring.type}")
                }
                val utbetaltIVentetid =
                    dagerIkkeOpphørt
                        .filter { it.erIVentetid() }
                        .sumOf { it.beløpTilBruker }

                if (!navKjøptForsikring.type.dekning.iVentetid() && utbetaltIVentetid > 0) {
                    error("Utbetaling i ventetid for forsikringstype ${navKjøptForsikring.type}, som ikke har dekning i ventetid")
                }

                val utbetaltUtenomVentetid =
                    dagerIkkeOpphørt
                        .filterNot { it.erIVentetid() }
                        .sumOf { it.beløpTilBruker } * (navKjøptForsikring.type.dekning.grad - (if (navKjøptForsikring.type.yrkesaktivitetstype == Yrkesaktivitetstype.SELVSTENDIG) 80 else 100)) / 100

                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = Forsikringstype.NavKjøpt(navKjøptForsikring.type),
                    utbetaltIVentetid = utbetaltIVentetid,
                    utbetaltUtenomVentetid = utbetaltUtenomVentetid,
                )
            } else if (kollektivForsikring != null) {
                if (!vedtakFattetMelding.utbetalingsdager.all { it.dekningsgrad == kollektivForsikring.dekning.grad }) {
                    error("Utbetalingsdager har ulik dekningsgrad fra forsikringstype $kollektivForsikring")
                }
                val utbetaltIVentetid =
                    vedtakFattetMelding.utbetalingsdager.filter { it.erIVentetid() }.sumOf { it.beløpTilBruker }

                if (!kollektivForsikring.dekning.iVentetid() && utbetaltIVentetid > 0) {
                    error("Utbetaling i ventetid for forsikringstype $kollektivForsikring, som ikke har dekning i ventetid")
                }

                val utbetaltUtenomVentetid =
                    vedtakFattetMelding.utbetalingsdager
                        .filterNot { it.erIVentetid() }
                        .sumOf { it.beløpTilBruker } * (kollektivForsikring.dekning.grad - 80) / 100

                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = Forsikringstype.Kollektiv(kollektivForsikring),
                    utbetaltIVentetid = utbetaltIVentetid,
                    utbetaltUtenomVentetid = utbetaltUtenomVentetid,
                )
            }
        }
    }
}

private fun VedtakFattetMelding.Utbetalingsdag.erIVentetid(): Boolean = type.isKnown(Type.Ventetidsdag)

private fun LocalDateTime.tilInstantIOslo(): Instant = atZone(ZoneId.of("Europe/Oslo")).toInstant()
