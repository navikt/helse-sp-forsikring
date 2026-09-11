package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaction
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonsmelding
import no.nav.helse.sykepenger.forsikring.subsumsjon.tilSubsumsjonsmeldinger
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import javax.sql.DataSource

class ForsikringsvurderingBehovRiver(
    rapidsConnection: RapidsConnection,
    replikabaseDataSource: DataSource,
    private val spForsikringDataSource: DataSource,
    private val versjonAvKode: String,
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
                        "yrkesaktivitetstype",
                        "Forsikringsvurdering.spesielleYrkesgrupper",
                        "Forsikringsvurdering.skjæringstidspunkt",
                    )
                    it.requireArray("Forsikringsvurdering.spesielleYrkesgrupper")
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
                val (råkopi, forsikringsvurdering) =
                    forsikringsvurderingService.gjørForsikringsvurdering(
                        identitetsnummer = Identitetsnummer.fraString(melding.fødselsnummer),
                        yrkesaktivitetstype = melding.yrkesaktivitetstype.tilDomene(),
                        spesielleYrkesgrupper =
                            melding.forsikringsvurdering.spesielleYrkesgrupper
                                .map { it.tilDomene() }
                                .toSet(),
                        skjæringstidspunkt = melding.forsikringsvurdering.skjæringstidspunkt,
                    )

                RåkopiRepository(transaction).lagre(råkopi)
                ForsikringsvurderingRepository(transaction).lagre(forsikringsvurdering, packet.toJson())

                val subsumsjonsMeldinger =
                    forsikringsvurdering
                        .tilSubsumsjonsmeldinger(
                            vedtaksperiodeId = melding.vedtaksperiodeId,
                            behandlingId = melding.behandlingId,
                            versjonAvKode = versjonAvKode,
                        ).map(Subsumsjonsmelding::tilJson)

                packet["@løsning"] =
                    mapOf(
                        "Forsikringsvurdering" to
                            mapOf(
                                "forsikringsvurderingId" to forsikringsvurdering.id.value.toString(),
                            ),
                    )

                val løsningJson = packet.toJson()

                subsumsjonsMeldinger.forEach { subsumsjonsmelding ->
                    loggInfo("Sender subsumsjonsmelding", "subsumsjonsmelding" to subsumsjonsmelding)
                    context.publish(subsumsjonsmelding)
                }

                loggInfo("Svarer på Forsikringsvurdering-behov med løsning", "løsning" to løsningJson)
                context.publish(løsningJson)
            }
        } catch (err: Exception) {
            // Logg feilen og gå videre. Meldingen hoppes over siden vi ikke kaster exception ut av onPacket().
            loggError("Feil ved håndtering av Forsikringsvurdering-behov", err, "melding" to packet.toJson())
        }
    }

    private fun ForsikringsvurderingBehovMelding.Yrkesaktivitetstype.tilDomene(): Yrkesaktivitetstype =
        when (this) {
            ForsikringsvurderingBehovMelding.Yrkesaktivitetstype.ARBEIDSTAKER -> Yrkesaktivitetstype.ARBEIDSTAKER
            ForsikringsvurderingBehovMelding.Yrkesaktivitetstype.FRILANS -> Yrkesaktivitetstype.FRILANS
            ForsikringsvurderingBehovMelding.Yrkesaktivitetstype.ARBEIDSLEDIG -> Yrkesaktivitetstype.ARBEIDSLEDIG
            ForsikringsvurderingBehovMelding.Yrkesaktivitetstype.SELVSTENDIG -> Yrkesaktivitetstype.SELVSTENDIG
        }

    private fun ForsikringsvurderingBehovMelding.SpesiellYrkesgruppe.tilDomene(): SpesiellYrkesgruppe =
        when (this) {
            ForsikringsvurderingBehovMelding.SpesiellYrkesgruppe.FISKER_BLAD_B -> SpesiellYrkesgruppe.FISKER_BLAD_B
            ForsikringsvurderingBehovMelding.SpesiellYrkesgruppe.JORDBRUKER -> SpesiellYrkesgruppe.JORDBRUKER
            ForsikringsvurderingBehovMelding.SpesiellYrkesgruppe.REINDRIFTER -> SpesiellYrkesgruppe.REINDRIFTER
        }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggError("Forstod ikke Forsikringsvurdering-behov", "extendedReport" to problems.toExtendedReport())
    }
}
