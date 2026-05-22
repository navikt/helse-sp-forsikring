package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

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

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {

                val fullstendigeForsikringer = infotrygdForsikringDao.hentFullstendigeForsikringer(fødselsnummer)

                /*val resultat: ForsikringDto
                val løsning =  mapOf(
                    "forsikringstype" to it.forsikringstype.name,
                    "premiegrunnlag" to it.premiegrunnlag,
                    "startdato" to it.virkningsdato,
                    "sluttdato" to it.tom
                )*/

                /*val resultat =
                    sykepengeforsikringService.hentSykepengeforsikring(
                        fødselsnummer = fødselsnummer,
                        callId = meldingId
                    )*/
                TODO()
                packet["@løsning"] = mapOf("Sykepengeforsikring" to "")
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

/*
data class Resultat(

    val forsikringstype: Forsikringstype,
)

fun svarFraFullstendigeForsikringer(forsikringer: List<RåForsikringDto>) : ForsikringDto {
    return ForsikringDto(
        forsikringstype = ForsikringDto.Forsikringstype.ÅttiProsentFraDagEn, premiegrunnlag = 0, virkningsdato = LocalDate(), tom = null

    )
}

data class ForsikringDto(
    val forsikringstype: Forsikringstype,
    val detaljer: Detaljer,
) {
    data class Detaljer(
        val premiegrunnlag: Int,
        val virkningsdato: LocalDate,
        val tom: LocalDate?
    )

    enum class Forsikringstype {
        ÅttiProsentFraDagEn,
        HundreProsentFraDagEn,
        HundreProsentFraDagSytten,
    }

    internal fun erAktivPå(dato: LocalDate): Boolean =
        !(dato.isBefore(virkningsdato) || tom != null && dato.isAfter(tom))
}
*/
