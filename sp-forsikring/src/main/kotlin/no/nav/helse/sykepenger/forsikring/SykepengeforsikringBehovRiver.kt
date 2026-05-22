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

class SykepengeforsikringBehovRiver(
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
                val vedfrivt10Rader = infotrygdForsikringDao.hentIfVedfrivt10Rader(fødselsnummer)
                val løsning = if (vedfrivt10Rader.isNotEmpty()) {
                    Løsning.MedForsikring(
                        oppslagId = oppslagId,
                        dekning = when (val type = vedfrivt10Rader.first().IF10_TYPE) {
                            '1' -> Løsning.MedForsikring.Dekning(grad = 80, fraDag = 1)
                            '2' -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 17)
                            '3' -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1)
                            '4' -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1)

                            else -> error("Støttet ikke verdi på IF10_TYPE: $type")
                        }
                    )
                } else {
                    Løsning.UtenForsikring(oppslagId = oppslagId)
                }
                packet["@løsning"] = mapOf("Sykepengeforsikring" to løsning)
                context.publish(packet.toJson())
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Sykepengeforsikring-behov", err)
            }
        }
    }

    sealed class Løsning(val oppslagId: UUID, val harForsikring: Boolean) {
        class UtenForsikring(
            oppslagId: UUID
        ) : Løsning(oppslagId = oppslagId, harForsikring = false)

        class MedForsikring(
            oppslagId: UUID,
            val dekning: Dekning
        ) : Løsning(oppslagId = oppslagId, harForsikring = true) {
            data class Dekning(val grad: Int, val fraDag: Int)
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
