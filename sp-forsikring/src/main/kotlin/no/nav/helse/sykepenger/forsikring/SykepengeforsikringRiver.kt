package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SykepengeforsikringRiver(
    rapidsConnection: RapidsConnection,
    private val sykepengeforsikringService: SykepengeforsikringService
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf("Sykepengeforsikring"))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("@id")
                    it.requireKey("fødselsnummer")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val meldingId = packet["@id"].asText()
        val fødselsnummer = packet["fødselsnummer"].asText()
        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {
                val resultat =
                    sykepengeforsikringService.hentSykepengeforsikring(
                        fødselsnummer = fødselsnummer,
                        callId = meldingId
                    )
                packet["@løsning"] = mapOf("Sykepengeforsikring" to resultat)
                context.publish(packet.toJson())
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Sykepengeforsikring-behov", err)
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata
    ) {
        loggError("Forstod ikke Sykepengeforsikring-behov", "extendedReport" to problems.toExtendedReport())
    }
}
