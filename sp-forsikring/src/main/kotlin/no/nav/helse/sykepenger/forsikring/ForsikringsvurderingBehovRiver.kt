package no.nav.helse.sykepenger.forsikring

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
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe.Fisker.Blad
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import tools.jackson.databind.JsonNode

class ForsikringsvurderingBehovRiver(
    rapidsConnection: RapidsConnection,
    private val replikabaseDataSource: DataSource,
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
            loggInfo("Henter forsikringsvurdering")
            try {
                sessionOf(spForsikringDataSource).use { session ->
                    session.transaction { transaction ->
                        val forsikringsvurdering = ForsikringsvurderingService(
                            spForsikringTransaction = transaction,
                            replikabaseDataSource = replikabaseDataSource
                        ).gjørVurdering(
                            behovJson = packet.toJson(),
                            skjæringstidspunkt = skjæringstidspunkt,
                            fødselsnummer = fødselsnummer,
                            spesielleYrkesgrupper = spesielleYrkesgrupper,
                            yrkesaktivitetstype = yrkesaktivitetstype
                        )

                        packet["@løsning"] = mapOf("Forsikringsvurdering" to forsikringsvurdering.løsning)
                        context.publish(packet.toJson())
                    }
                }
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Forsikringsvurdering-behov", err, "melding" to packet.toJson())
                throw err
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
