package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.domain.FordelingAvBeløpPåUtbetalingsdag
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.Utbetalingsdag
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetMelding.Utbetalingsdag.Type
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaksjon
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.VedtakFattetMeldingDao
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
            val individuellForsikring = forsikringsvurdering.gjeldendeIndividuellForsikring()

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
                        individuellForsikring = individuellForsikring,
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
            if (individuellForsikring != null) {
                utbetalingPerForsikringstypeDao.insert(
                    vedtakFattetMeldingId = vedtakFattetMelding.id,
                    forsikringstype = individuellForsikring.type,
                    utbetaltIVentetid = fordelingerIVentetid.summer { it.påGrunnAvIndividuellForsikring },
                    utbetaltUtenomVentetid = fordelingerUtenomVentetid.summer { it.påGrunnAvIndividuellForsikring },
                )
            }

            if ("Førstegangsbehandling" in vedtakFattetMelding.tags && individuellForsikring != null) {
                val premiegrunnlag = BigDecimal(individuellForsikring.premiegrunnlag)

                if (vedtakFattetMelding.sykepengegrunnlag.compareTo(premiegrunnlag) != 0) {
                    val avviksprosent = beregnAvvik(vedtakFattetMelding.sykepengegrunnlag, premiegrunnlag)
                    loggInfo(
                        """
                        Avvik mellom sykepengegrunnlag og premiegrunnlag. Oppretter oppgave.
                        Sykepengegrunnlag: ${vedtakFattetMelding.sykepengegrunnlag.setScale(2)}
                        Premiegrunnlag: $premiegrunnlag
                        Avviksprosent: ${avviksprosent.setScale(2)}
                        """.trimIndent(),
                        "fødselsnummer" to vedtakFattetMelding.fødselsnummer,
                    )

                    runBlocking {
                        gosysOppgaveClient.lagOppgave(
                            duplikatkontrollId = vedtakFattetMelding.id,
                            fødselsnummer = vedtakFattetMelding.fødselsnummer,
                            årsak =
                                Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(
                                    vedtakFattetMelding.sykepengegrunnlag,
                                    premiegrunnlag,
                                    avviksprosent,
                                ),
                            skjæringstidspunkt = vedtakFattetMelding.skjæringstidspunkt,
                        )
                    }
                }
            }
        }
    }

    /**
     * Beregner og returnerer prosentvis avvik mellom sykepengegrunnlag og premiegrunnlag.
     *
     * @param sykepengegrunnlag Det beregnede sykepengegrunnlaget.
     * @param premiegrunnlag Det registrerte premiegrunnlaget.
     * @return prosent avvik mellom sykepengegrunnlaget og premiegrunnlaget
     *
     * Formel: Avvik (%) = ((Fastsatt sykepengegrunnlag - fastsatt premiegrunnlag) / fastsatt sykepengegrunnlag) * 100
     */

    private fun beregnAvvik(
        sykepengegrunnlag: BigDecimal,
        premiegrunnlag: BigDecimal,
    ): BigDecimal =
        sykepengegrunnlag
            .subtract(premiegrunnlag)
            .abs()
            .divide(sykepengegrunnlag, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)

    /**
     * Summerer beløpene med full mellomregningspresisjon. Avrunding til to desimaler skjer først når summen
     * lagres, slik at vi ikke akkumulerer avrundingsfeil per utbetalingsdag.
     */
    private fun List<FordelingAvBeløpPåUtbetalingsdag>.summer(beløp: (FordelingAvBeløpPåUtbetalingsdag) -> BigDecimal): BigDecimal = fold(BigDecimal.ZERO) { sum, fordeling -> sum + beløp(fordeling) }

    private fun LocalDateTime.tilInstantIOslo(): Instant = atZone(ZoneId.of("Europe/Oslo")).toInstant()
}
