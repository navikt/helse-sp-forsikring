package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.ForsikringsvurderingInput
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingGjenbrukDao
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaction
import no.nav.helse.sykepenger.forsikring.rettighetskategori.Rettighetskategori
import no.nav.helse.sykepenger.forsikring.rettighetskategori.RettighetskategoriClient
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.Instant
import javax.sql.DataSource

class ForsikringsvurderingBehovRiver(
    rapidsConnection: RapidsConnection,
    replikabaseDataSource: DataSource,
    private val spForsikringDataSource: DataSource,
    private val versjonAvKode: String,
    private val outboxRepositoryFactory: OutboxRepositoryFactory,
    private val rettighetskategoriClient: RettighetskategoriClient,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf("Forsikringsvurdering"))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey(
                        "@id",
                        "fødselsnummer",
                        "vedtaksperiodeId",
                        "behandlingId",
                        "Forsikringsvurdering.rettighetskategoriId",
                        "Forsikringsvurdering.skjæringstidspunkt",
                    )
                }
            }.register(this)
    }

    private val forsikringsvurderingService: ForsikringsvurderingService =
        ForsikringsvurderingService(replikabaseDataSource)

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        try {
            packet.medParsetMeldingOgTransaction<ForsikringsvurderingBehovMelding>(
                dataSource = spForsikringDataSource,
            ) { melding, transaction ->
                val identitetsnummer = Identitetsnummer.fraString(melding.fødselsnummer)
                val rettighetskategoriId = melding.forsikringsvurdering.rettighetskategoriId
                val forsikringsvurderingRepository = ForsikringsvurderingRepository(transaction)
                val outboxRepository = outboxRepositoryFactory.lag(transaction)

                val tidligereForsikringsvurderingId =
                    forsikringsvurderingRepository.finnSisteId(
                        identitetsnummer = identitetsnummer,
                        rettighetskategoriId = rettighetskategoriId,
                        skjæringstidspunkt = melding.forsikringsvurdering.skjæringstidspunkt,
                    )

                val forsikringsvurderingId =
                    if (tidligereForsikringsvurderingId != null) {
                        // Endringer i Infotrygd siden forrige vurdering fanges opp av endringssjekk
                        ForsikringsvurderingGjenbrukDao(transaction).insert(
                            forsikringsvurderingId = tidligereForsikringsvurderingId,
                            behov = packet.toJson(),
                            opprettet = Instant.now(),
                        )
                        loggInfo(
                            "Gjenbruker forsikringsvurdering for samme rettighetskategori og skjæringstidspunkt",
                            "forsikringsvurderingId" to tidligereForsikringsvurderingId.value.toString(),
                            "rettighetskategoriId" to rettighetskategoriId.toString(),
                        )
                        tidligereForsikringsvurderingId
                    } else {
                        val rettighetskategori = rettighetskategoriClient.hent(rettighetskategoriId, identitetsnummer)

                        if (rettighetskategori.hovedkategori != Rettighetskategori.Hovedkategori.SELVSTENDIG_NAERINGSDRIVENDE) {
                            loggError(
                                "Kan bare vurdere forsikring for selvstendig næringsdrivende. Svarer ikke på behovet.",
                                "hovedkategori" to rettighetskategori.hovedkategori.name,
                                "rettighetskategoriId" to rettighetskategoriId.toString(),
                            )
                            return@medParsetMeldingOgTransaction
                        }

                        val (råkopi, forsikringsvurdering) =
                            forsikringsvurderingService.gjørForsikringsvurdering(
                                input =
                                    ForsikringsvurderingInput(
                                        identitetsnummer = identitetsnummer,
                                        yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
                                        spesielleYrkesgrupper =
                                            rettighetskategori.tilleggskategorier
                                                .map { it.tilSpesiellYrkesgruppe() }
                                                .toSet(),
                                        skjæringstidspunkt = melding.forsikringsvurdering.skjæringstidspunkt,
                                        rettighetskategoriId = rettighetskategoriId,
                                    ),
                                vedtaksperiodeId = melding.vedtaksperiodeId,
                                behandlingId = melding.behandlingId,
                                forrigeForsikringsvurderingId = null,
                            )

                        RåkopiRepository(transaction).lagre(råkopi)
                        forsikringsvurderingRepository.lagre(
                            forsikringsvurdering = forsikringsvurdering,
                            behovEllerRequestBody = packet.toJson(),
                        )

                        RapidSubsumsjonspubliserer(
                            versjonAvKode = versjonAvKode,
                        ).publiser(
                            outboxRepository = outboxRepository,
                            forsikringsvurdering = forsikringsvurdering,
                            vedtaksperiodeId = melding.vedtaksperiodeId,
                            behandlingId = melding.behandlingId,
                        )

                        forsikringsvurdering.id
                    }

                packet["@løsning"] =
                    mapOf(
                        "Forsikringsvurdering" to
                            mapOf(
                                "forsikringsvurderingId" to forsikringsvurderingId.value.toString(),
                            ),
                    )

                val løsningJson = packet.toJson()

                loggInfo("Legger løsning på Forsikringsvurdering-behov i outbox", "løsning" to løsningJson)
                outboxRepository.push(identitetsnummer.value, løsningJson)
            }
        } catch (err: Exception) {
            // Logg feilen og gå videre. Meldingen hoppes over siden vi ikke kaster exception ut av onPacket().
            loggError("Feil ved håndtering av Forsikringsvurdering-behov", err, "melding" to packet.toJson())
        }
    }

    private fun Rettighetskategori.Tilleggskategori.tilSpesiellYrkesgruppe(): SpesiellYrkesgruppe =
        when (this) {
            Rettighetskategori.Tilleggskategori.JORDBRUKER -> SpesiellYrkesgruppe.JORDBRUKER
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggError("Forstod ikke Forsikringsvurdering-behov", "extendedReport" to problems.toExtendedReport())
    }
}
