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
import no.nav.helse.sykepenger.forsikring.SykepengeforsikringBehovRiver.Løsning.MedForsikring.Dekning
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
                        "Sykepengeforsikring.spesielleYrkesgrupper",
                        "Sykepengeforsikring.skjæringstidspunkt"
                    )
                    it.requireArray("Sykepengeforsikring.spesielleYrkesgrupper")
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
        val spesielleYrkesgrupper = packet["Sykepengeforsikring.spesielleYrkesgrupper"].map<JsonNode, String> { it.asString() }.toSet()
        val skjæringstidspunkt = packet["Sykepengeforsikring.skjæringstidspunkt"].asLocalDate()

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {
                sessionOf(spForsikringDataSource).use { session ->
                    session.transaction { transaction ->
                        val oppslag = OppslagService(transaction, replikabaseDataSource)
                            .gjørNyttOppslag(fødselsnummer, packet.toJson())

                        val navKjøpteForsikringer = oppslag.navKjøpteForsikringer.toMutableList()

                        // Skjæringstidspunkt må være etter eller lik virkningsdato
                        val forsikringerMedVirkningsdatoEtterSkjæringstidspunkt = navKjøpteForsikringer.filter {
                            it.virkningsdato > skjæringstidspunkt
                        }
                        navKjøpteForsikringer.removeAll(forsikringerMedVirkningsdatoEtterSkjæringstidspunkt)

                        // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
                        val forsikringerMedOpphørsdatoFørSkjæringstidspunkt = navKjøpteForsikringer.filter { forsikring ->
                            forsikring.opphørsdato != null && skjæringstidspunkt > forsikring.opphørsdato
                        }
                        navKjøpteForsikringer.removeAll(forsikringerMedOpphørsdatoFørSkjæringstidspunkt)

                        // TODO: Forsikringen er ikke betalt noen gang (ennå) - filtreres ut

                        // Kontroller mismatch mellom yrkesaktivitetstype og type forsikring i Infotrygd
                        navKjøpteForsikringer.forEach {
                            val forventetYrkesaktivitetstype = when (it.type) {
                                Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> "SELVSTENDIG"
                                Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> "SELVSTENDIG"
                                Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> "SELVSTENDIG"
                                Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> "SELVSTENDIG"
                                Type.FRILANSER_100_PROSENT_FRA_DAG_1 -> "FRILANS"
                            }
                            if (yrkesaktivitetstype != forventetYrkesaktivitetstype) {
                                val feilmelding = "Nav-kjøpt forsikring er av type ${it.type}, " +
                                    "der forventet yrkesaktivitetstype er $forventetYrkesaktivitetstype, " +
                                    "men yrkesaktivitetstypen var $yrkesaktivitetstype"
                                loggError(feilmelding, "behov" to packet.toJson()) // TODO: Logge alle nav-kjøpte forsikringer som ble funnet?
                                error(feilmelding)
                            }
                        }

                        // Kontroller mismatch mellom spesiell yrkesgruppe og type tilleggsforsikring i Infotrygd
                        if (navKjøpteForsikringer.any { it.type == Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 }
                            && spesielleYrkesgrupper.none { it in setOf("JORDBRUKER", "REINDRIFTER") }) {
                            val feilmelding = "Bruker har Nav-kjøpt forsikring av type ${Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1}, " +
                                "som kun gjelder for de spesielle yrkesgruppene JORDBRUKER eller REINDRIFTER, " +
                                "men spesielle yrkesgrupper var $spesielleYrkesgrupper"
                            loggError(feilmelding, "behov" to packet.toJson()) // TODO: Logge alle nav-kjøpte forsikringer som ble funnet?
                            error(feilmelding)
                        }

                        val dekninger = navKjøpteForsikringer.map {
                            when (it.type) {
                                Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> Dekning(grad = 80, fraDag = 1)
                                Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> Dekning(grad = 100, fraDag = 17)
                                Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> Dekning(grad = 100, fraDag = 1)
                                Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> Dekning(grad = 100, fraDag = 1)
                                Type.FRILANSER_100_PROSENT_FRA_DAG_1 -> Dekning(grad = 100, fraDag = 1)
                            }
                        }.toMutableList()
                        if ("FISKER_BLAD_B" in spesielleYrkesgrupper) {
                            dekninger.add(Dekning(grad = 100, fraDag = 1)) // Kollektiv forsikring
                        }
                        if ("JORDBRUKER" in spesielleYrkesgrupper || "REINDRIFTER" in spesielleYrkesgrupper) {
                            dekninger.add(Dekning(grad = 100, fraDag = 17)) // Kollektiv forsikring
                        }

                        val grader = dekninger.map { it.grad }.distinct()
                        if (grader.size > 1) error("Fant dekninger med ulike grader: $grader")

                        val dekning = dekninger.minByOrNull { it.fraDag }
                        val løsning = dekning?.let { Løsning.MedForsikring(oppslagId = oppslag.id, dekning = it) }
                            ?: Løsning.UtenForsikring(oppslagId = oppslag.id)

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
