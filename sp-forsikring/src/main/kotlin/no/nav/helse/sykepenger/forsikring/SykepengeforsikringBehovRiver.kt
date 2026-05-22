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
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.oppslag.NavKjøptForsikring.Type
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import tools.jackson.databind.JsonNode

class SykepengeforsikringBehovRiver(
    rapidsConnection: RapidsConnection,
    private val replikabaseDataSource: DataSource,
    private val spForsikringDataSource: DataSource,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf("Sykepengeforsikring"))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey(
                        "@id",
                        "fødselsnummer",
                        "yrkesaktivitetstype",
                        "Sykepengeforsikring.særskilteGrupper",
                        "Sykepengeforsikring.skjæringstidspunkt"
                    )
                    it.requireArray("Sykepengeforsikring.særskilteGrupper")
                }
            }.register(this)
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val meldingId = packet["@id"].asString()
        val fødselsnummer = packet["fødselsnummer"].asString()
        val yrkesaktivitetstype = packet["yrkesaktivitetstype"].asString()
        val særskilteGrupper = packet["Sykepengeforsikring.særskilteGrupper"].map<JsonNode, String> { it.asString() }.toSet()
        val skjæringstidspunkt = packet["Sykepengeforsikring.skjæringstidspunkt"].asLocalDate()

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {
                sessionOf(spForsikringDataSource).use { session ->
                    session.transaction { transaction ->
                        val oppslag = OppslagService(transaction, replikabaseDataSource)
                            .gjørNyttOppslag(fødselsnummer, packet.toJson())

                        val løsning = if ("FISKER_BLAD_B" in særskilteGrupper) {
                            Løsning.MedForsikring(
                                oppslagId = oppslag.id,
                                dekning = Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1) // Kollektiv forsikring
                            )
                        } else if ("JORDBRUKER" in særskilteGrupper || "REINDRIFTER" in særskilteGrupper) {
                            Løsning.MedForsikring(
                                oppslagId = oppslag.id,
                                dekning = if (oppslag.navKjøpteForsikringer.firstOrNull()?.type == Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1) {
                                    Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1)
                                } else {
                                    Løsning.MedForsikring.Dekning(grad = 100, fraDag = 17) // Kollektiv forsikring
                                }
                            )
                        } else if (oppslag.navKjøpteForsikringer.isNotEmpty()) {
                            Løsning.MedForsikring(
                                oppslagId = oppslag.id,
                                dekning = when (val type = oppslag.navKjøpteForsikringer.first().type) {
                                    Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> Løsning.MedForsikring.Dekning(grad = 80, fraDag = 1)
                                    Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 17)
                                    Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1)
                                    Type.FRILANSER_100_PROSENT_FRA_DAG_1 if "FRILANSER" == yrkesaktivitetstype -> Løsning.MedForsikring.Dekning(grad = 100, fraDag = 1)

                                    else -> error("Støtter ikke kombinasjonen IF10_TYPE $type, yrkesaktivitetstype $yrkesaktivitetstype, særskilteGrupper $særskilteGrupper")
                                }
                            )
                        } else {
                            Løsning.UtenForsikring(oppslagId = oppslag.id)
                        }
                        packet["@løsning"] = mapOf("Sykepengeforsikring" to løsning)
                        context.publish(packet.toJson())
                    }
                }
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
