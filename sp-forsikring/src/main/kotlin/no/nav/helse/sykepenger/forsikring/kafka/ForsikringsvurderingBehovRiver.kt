package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
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
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import tools.jackson.databind.JsonNode
import javax.sql.DataSource

class ForsikringsvurderingBehovRiver(
    rapidsConnection: RapidsConnection,
    replikabaseDataSource: DataSource,
    private val spForsikringDataSource: DataSource,
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

    inline fun <reified T : Enum<T>> JsonNode.asEnum(): T = enumValueOf<T>(asString())

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        try {
            medMdc(MdcKey.MELDING_ID to packet["@id"].asString()) {
                loggInfo("Mottok Forsikringsvurdering-behov", "behov" to packet.toJson())
                val (råkopi, forsikringsvurdering) =
                    forsikringsvurderingService.gjørForsikringsvurdering(
                        identitetsnummer = Identitetsnummer.fraString(packet["fødselsnummer"].asString()),
                        yrkesaktivitetstype =
                            when (
                                val yrkesaktivitetstypeString =
                                    packet["yrkesaktivitetstype"].asString()
                            ) {
                                "ARBEIDSTAKER" -> Yrkesaktivitetstype.ARBEIDSTAKER
                                "FRILANS" -> Yrkesaktivitetstype.FRILANS
                                "ARBEIDSLEDIG" -> Yrkesaktivitetstype.ARBEIDSLEDIG
                                "SELVSTENDIG" -> Yrkesaktivitetstype.SELVSTENDIG
                                else -> error("Ukjent yrkesaktivitetstype: $yrkesaktivitetstypeString")
                            },
                        spesielleYrkesgrupper =
                            packet["Forsikringsvurdering.spesielleYrkesgrupper"]
                                .toList()
                                .map {
                                    when (val spesiellYrkesgruppeString = it.asString()) {
                                        "FISKER_BLAD_B" -> SpesiellYrkesgruppe.FISKER_BLAD_B
                                        "JORDBRUKER" -> SpesiellYrkesgruppe.JORDBRUKER
                                        "REINDRIFTER" -> SpesiellYrkesgruppe.REINDRIFTER
                                        else -> error("Ukjent spesiell yrkesgruppe: $spesiellYrkesgruppeString")
                                    }
                                }.toSet(),
                        skjæringstidspunkt = packet["Forsikringsvurdering.skjæringstidspunkt"].asLocalDate(),
                    )

                spForsikringDataSource.inTransaction { transaction ->
                    RåkopiRepository(transaction).lagre(råkopi)
                    ForsikringsvurderingRepository(transaction).lagre(forsikringsvurdering, packet.toJson())

                    packet["@løsning"] =
                        mapOf(
                            "Forsikringsvurdering" to
                                mapOf(
                                    "forsikringsvurderingId" to forsikringsvurdering.id.value.toString(),
                                ),
                        )

                    val løsningJson = packet.toJson()
                    loggInfo("Svarer på Forsikringsvurdering-behov med løsning", "løsning" to løsningJson)
                    context.publish(løsningJson)
                }
            }
        } catch (err: Exception) {
            // Logg feilen og gå videre. Meldingen hoppes over siden vi ikke kaster exception ut av onPacket().
            loggError("Feil ved håndtering av Forsikringsvurdering-behov", err, "melding" to packet.toJson())
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggError("Forstod ikke Forsikringsvurdering-behov", "extendedReport" to problems.toExtendedReport())
    }
}
