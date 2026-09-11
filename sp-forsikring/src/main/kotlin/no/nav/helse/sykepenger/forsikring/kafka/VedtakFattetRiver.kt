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
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetMelding.Utbetalingsdag.Type
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaction
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.sql.DataSource

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val gosysOppgaveClient: GosysOppgaveClient,
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
        packet.medParsetMeldingOgTransaction<VedtakFattetMelding>(
            mdcMapping = mapOf(MdcKey.FORSIKRINGSVURDERING_ID to VedtakFattetMelding::forsikringsvurderingId),
            dataSource = spForsikringDataSource,
        ) { melding, transaction ->
            val vedtakFattetMeldingDao = VedtakFattetMeldingDao(transaction)

            if (vedtakFattetMeldingDao.eksisterer(melding.id)) {
                loggInfo("Hopper over vedtak_fattet-melding som allerede er lagret ned")
                return@medParsetMeldingOgTransaction
            }

            val forsikringsvurdering =
                melding.forsikringsvurderingId
                    ?.let { Forsikringsvurdering.Id(it) }
                    ?.let {
                        ForsikringsvurderingRepository(transaction).hent(it)
                            ?: error("Fant ikke forsikringsvurdering med $it")
                    }

            vedtakFattetMeldingDao.insert(
                id = melding.id,
                forsikringsvurderingId = forsikringsvurdering?.id,
                identitetsnummer = Identitetsnummer.fraString(melding.fødselsnummer),
                behandlingId = melding.behandlingId,
                vedtakFattetTidspunkt = melding.vedtakFattetTidspunkt.tilInstantIOslo(),
                json = packet.toJson(),
            )

            if (forsikringsvurdering == null) {
                return@medParsetMeldingOgTransaction
            }

            val kollektivForsikring = forsikringsvurdering.kollektivForsikring
            val individuellForsikring = forsikringsvurdering.gjeldendeIndividuellForsikring()

            val utbetalingsdager =
                melding.utbetalingsdager.map {
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
                        individuellForsikring = individuellForsikring,
                    )
                }

            val (fordelingerIVentetid, fordelingerUtenomVentetid) =
                fordelingerAvBeløpPåUtbetalingsdager.partition { it.dag.erIVentetid }

            val utbetalingPerForsikringstypeDao = UtbetalingPerForsikringstypeDao(transaction)
            if (kollektivForsikring != null) {
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = melding.id,
                    forsikringstype = kollektivForsikring,
                    utbetaltIVentetid = fordelingerIVentetid.summer { it.påGrunnAvKollektivForsikring },
                    utbetaltUtenomVentetid = fordelingerUtenomVentetid.summer { it.påGrunnAvKollektivForsikring },
                )
            }
            if (individuellForsikring != null) {
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = melding.id,
                    forsikringstype = individuellForsikring.type,
                    utbetaltIVentetid = fordelingerIVentetid.summer { it.påGrunnAvIndividuellForsikring },
                    utbetaltUtenomVentetid = fordelingerUtenomVentetid.summer { it.påGrunnAvIndividuellForsikring },
                )
            }

            if ("Førstegangsbehandling" in melding.tags && individuellForsikring != null) {
                val premiegrunnlag = individuellForsikring.premiegrunnlag
                val avviksbeløp = melding.sykepengegrunnlag.subtract(BigDecimal(premiegrunnlag)).abs()
                loggInfo("Beregnet avvik: ${avviksbeløp.iBeløpsFormat()}")

                val avviksgrense = 100
                if (avviksbeløp >= BigDecimal(avviksgrense)) {
                    gosysOppgaveClient.opprettOppgave(
                        personident = melding.fødselsnummer,
                        uuid = melding.id.toString(),
                        beskrivelse =
                            "Sykepenger er utbetalt med sykepengegrunnlag" +
                                " ${melding.sykepengegrunnlag.iBeløpsFormat()}," +
                                " med forsikring med premiegrunnlag ${premiegrunnlag.iBeløpsFormat()}." +
                                " Avviket er på ${avviksbeløp.iBeløpsFormat()}," +
                                " som er høyere enn ønsket (<${avviksgrense.iBeløpsFormat()})." +
                                " Utbetalingen skjedde for sykefravær med skjæringstidspunkt " +
                                "${melding.skjæringstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.",
                    )
                }
            }
        }
    }

    private fun Number.iBeløpsFormat(): String =
        NumberFormat
            .getInstance(Locale.of("no", "NO"))
            .apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
                roundingMode = RoundingMode.HALF_UP
            }.format(this)
            .replace('\u00A0', ' ')
            .replace('\u2212', '-')
            .replace(",00", "")
            .plus(" kr")

    /**
     * Summerer beløpene med full mellomregningspresisjon. Avrunding til to desimaler skjer først når summen
     * lagres, slik at vi ikke akkumulerer avrundingsfeil per utbetalingsdag.
     */
    private fun List<FordelingAvBeløpPåUtbetalingsdag>.summer(beløp: (FordelingAvBeløpPåUtbetalingsdag) -> BigDecimal): BigDecimal = fold(BigDecimal.ZERO) { sum, fordeling -> sum + beløp(fordeling) }

    private fun LocalDateTime.tilInstantIOslo(): Instant = atZone(ZoneId.of("Europe/Oslo")).toInstant()
}
