package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.FordelingAvBeløpPåUtbetalingsdag
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.Utbetalingsdag
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetMelding.Utbetalingsdag.Type
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaksjon
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import java.math.BigDecimal
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

            if (forsikringsvurdering == null) {
                return@medParsetMeldingOgTransaksjon
            }

            val kollektivForsikring = forsikringsvurdering.kollektivForsikring
            val navKjøptForsikring = forsikringsvurdering.gjeldendeNavKjøptForsikring()

            val utbetalingsdager =
                vedtakFattetMelding.utbetalingsdager.map {
                    Utbetalingsdag(
                        dato = it.dato,
                        beløpTilBruker = it.beløpTilBruker,
                        dekningsgrad = it.dekningsgrad,
                        erIVentetid = it.type.isKnown(Type.Ventetidsdag),
                    )
                }

            val fordelingerAvBeløpPåUtbetalingsdager =
                utbetalingsdager.map { dag ->
                    FordelingAvBeløpPåUtbetalingsdag.finnFordeling(
                        dag = dag,
                        yrkesaktivitetstype = forsikringsvurdering.yrkesaktivitetstype,
                        kollektivForsikring = kollektivForsikring,
                        navKjøptForsikring = navKjøptForsikring,
                    )
                }

            val (fordelingerIVentetid, fordelingerUtenomVentetid) =
                fordelingerAvBeløpPåUtbetalingsdager.partition { it.dag.erIVentetid }

            val utbetalingPerForsikringstypeDao = UtbetalingPerForsikringstypeDao(transaction)
            if (kollektivForsikring != null) {
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = kollektivForsikring,
                    utbetaltIVentetid = fordelingerIVentetid.summer { it.påGrunnAvKollektivForsikring },
                    utbetaltUtenomVentetid = fordelingerUtenomVentetid.summer { it.påGrunnAvKollektivForsikring },
                )
            }
            if (navKjøptForsikring != null) {
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = navKjøptForsikring.type,
                    utbetaltIVentetid = fordelingerIVentetid.summer { it.påGrunnAvNavKjøptForsikring },
                    utbetaltUtenomVentetid = fordelingerUtenomVentetid.summer { it.påGrunnAvNavKjøptForsikring },
                )
            }
        }
    }
}

/**
 * Summerer beløpene med full mellomregningspresisjon. Avrunding til to desimaler skjer først når summen
 * lagres, slik at vi ikke akkumulerer avrundingsfeil per utbetalingsdag.
 */
private fun List<FordelingAvBeløpPåUtbetalingsdag>.summer(beløp: (FordelingAvBeløpPåUtbetalingsdag) -> BigDecimal): BigDecimal = fold(BigDecimal.ZERO) { sum, fordeling -> sum + beløp(fordeling) }

private fun LocalDateTime.tilInstantIOslo(): Instant = atZone(ZoneId.of("Europe/Oslo")).toInstant()
