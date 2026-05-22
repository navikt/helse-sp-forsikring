package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*

class SykepengeforsikringRiver(
    rapidsConnection: RapidsConnection,
    private val infotrygdForsikringDao: InfotrygdForsikringDao,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf("Sykepengeforsikring"))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("@id", "fødselsnummer", "Sykepengeforsikring.skjæringstidspunkt")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val meldingId = packet["@id"].asString()
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["Sykepengeforsikring.skjæringstidspunkt"].asLocalDate()
        val oppslagId = UUID.randomUUID()

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {
                val fullstendigeForsikringer = infotrygdForsikringDao.hentFullstendigeForsikringer(fødselsnummer)
                packet["@løsning"] = mapOf("Sykepengeforsikring" to ForsikringLøsningUtenForsikring(oppslagId))
                context.publish(packet.toJson())
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Sykepengeforsikring-behov", err)
            }
        }
    }

    sealed class ForsikringsLøsning(val oppslagId: UUID, val harForsikring: Boolean)
    class ForsikringLøsningUtenForsikring(
        oppslagId: UUID
    ): ForsikringsLøsning(oppslagId = oppslagId, harForsikring = false)

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata
    ) {
        loggError("Forstod ikke Sykepengeforsikring-behov", "extendedReport" to problems.toExtendedReport())
    }
}
