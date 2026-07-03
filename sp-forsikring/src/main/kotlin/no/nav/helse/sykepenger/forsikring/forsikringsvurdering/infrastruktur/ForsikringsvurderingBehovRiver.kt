package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.SpesiellYrkesgruppe.Fisker.Blad
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import tools.jackson.databind.JsonNode

class ForsikringsvurderingBehovRiver(
    rapidsConnection: RapidsConnection,
    private val spForsikringDataSource: DataSource,
    private val forsikringsvurderingService: ForsikringsvurderingService,
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
                        "Forsikringsvurdering.skjæringstidspunkt"
                    )
                    it.requireArray("Forsikringsvurdering.spesielleYrkesgrupper")
                }
            }.register(this)
    }

    inline fun <reified T : Enum<T>> JsonNode.asEnum(): T = enumValueOf<T>(asString())

    @OptIn(ExperimentalUuidApi::class)
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val meldingId = packet["@id"].asString()
        val fødselsnummer = packet["fødselsnummer"].asString()
        val yrkesaktivitetstype = packet["yrkesaktivitetstype"].asEnum<Yrkesaktivitetstype>()
        val spesielleYrkesgrupper = packet["Forsikringsvurdering.spesielleYrkesgrupper"].map<JsonNode, SpesiellYrkesgruppe> {
            when (val spesiellYrkesgruppe = it.asString()) {
                "FISKER_BLAD_B" -> SpesiellYrkesgruppe.Fisker(Blad.B)
                "JORDBRUKER" -> SpesiellYrkesgruppe.Jordbruker
                "REINDRIFTER" -> SpesiellYrkesgruppe.Reindrifter
                else -> SpesiellYrkesgruppe.Ukjent(spesiellYrkesgruppe)
            }
        }.toSet()
        val skjæringstidspunkt = packet["Forsikringsvurdering.skjæringstidspunkt"].asLocalDate()

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Mottok Forsikringsvurdering-behov", "behov" to packet.toJson())
            try {
                spForsikringDataSource.inTransaction { transaction ->
                    val forsikringsvurdering = forsikringsvurderingService.gjørVurdering(
                        session = transaction,
                        behovJson = packet.toJson(),
                        skjæringstidspunkt = skjæringstidspunkt,
                        fødselsnummer = fødselsnummer,
                        spesielleYrkesgrupper = spesielleYrkesgrupper,
                        yrkesaktivitetstype = yrkesaktivitetstype
                    )

                    packet["@løsning"] = mapOf(
                        "Forsikringsvurdering" to mapOf(
                            "forsikringsvurderingId" to forsikringsvurdering.id.value.toString(),
                        )
                    )

                    val løsningJson = packet.toJson()
                    loggInfo("Svarer på Forsikringsvurdering-behov med løsning", "løsning" to løsningJson)
                    context.publish(løsningJson)
                }
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Forsikringsvurdering-behov", err, "melding" to packet.toJson())
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata
    ) {
        loggError("Forstod ikke Forsikringsvurdering-behov", "extendedReport" to problems.toExtendedReport())
    }
}
