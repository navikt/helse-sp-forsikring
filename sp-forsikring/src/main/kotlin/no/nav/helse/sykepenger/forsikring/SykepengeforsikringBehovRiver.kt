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
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe.Fisker.Blad
import no.nav.helse.sykepenger.forsikring.SykepengeforsikringBehovRiver.Løsning.MedForsikring.Dekning
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagDao
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
        val spesielleYrkesgrupper = packet["Sykepengeforsikring.spesielleYrkesgrupper"].map<JsonNode, SpesiellYrkesgruppe> {
            when (val spesiellYrkesgruppe = it.asString()) {
                "FISKER_BLAD_B" -> SpesiellYrkesgruppe.Fisker(Blad.B)
                "JORDBRUKER" -> SpesiellYrkesgruppe.Jordbruker
                "REINDRIFTER" -> SpesiellYrkesgruppe.Reindrifter
                else -> SpesiellYrkesgruppe.Ukjent(spesiellYrkesgruppe)
            }
        }.toSet()
        val skjæringstidspunkt = packet["Sykepengeforsikring.skjæringstidspunkt"].asLocalDate()

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Henter sykepengeforsikring")
            try {
                sessionOf(spForsikringDataSource).use { session ->
                    session.transaction { transaction ->
                        val oppslag = OppslagService(transaction, replikabaseDataSource)
                            .gjørNyttOppslag(fødselsnummer, packet.toJson())

                        val navKjøpteForsikringer = oppslag.navKjøpteForsikringer.toMutableList()
                        val ekskluderinger = mutableListOf<Pair<NavKjøptForsikring, NavKjøptForsikring.Ekskluderingsårsak>>()
                        val oppslagDao = OppslagDao(transaction)

                        // Skjæringstidspunkt må være etter eller lik virkningsdato
                        val forsikringerMedVirkningsdatoEtterSkjæringstidspunkt = navKjøpteForsikringer.filter {
                            it.harVirkningPå(skjæringstidspunkt)
                        }
                        navKjøpteForsikringer.removeAll(forsikringerMedVirkningsdatoEtterSkjæringstidspunkt)
                        forsikringerMedVirkningsdatoEtterSkjæringstidspunkt.forEach {
                            ekskluderinger.add(it to NavKjøptForsikring.Ekskluderingsårsak.VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT)
                        }

                        // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
                        val opphørteForsikringer = navKjøpteForsikringer.filter { forsikring ->
                            forsikring.erOpphørtPå(skjæringstidspunkt)
                        }
                        navKjøpteForsikringer.removeAll(opphørteForsikringer)
                        opphørteForsikringer.forEach {
                            ekskluderinger.add(it to NavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT)
                        }

                        // Forsikringen må være betalt noen gang
                        val ubetalteForsikringer = navKjøpteForsikringer.filterNot(NavKjøptForsikring::erBetaltNoenGang)
                        navKjøpteForsikringer.removeAll(ubetalteForsikringer)
                        ubetalteForsikringer.forEach {
                            ekskluderinger.add(it to NavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT)
                        }

                        oppslagDao.lagreEkskluderinger(oppslag.id, ekskluderinger)

                        // Kontroller mismatch mellom yrkesaktivitetstype og type forsikring i Infotrygd
                        navKjøpteForsikringer.forEach {
                            it.validerType(yrkesaktivitetstype, spesielleYrkesgrupper)
                        }

                        val alleForsikringer = navKjøpteForsikringer + kollektiveForsikringerFor(spesielleYrkesgrupper)

                        val dekninger = alleForsikringer.map {
                            Dekning(grad = it.dekningGrad(), fraDag = it.dekningFraDag())
                        }

                        if (alleForsikringer.distinctBy { it.dekningGrad() }.size > 1) {
                            val message = "Bruker har flere gyldige forsikringer med ulike dekningsgrader"
                            loggError(message, "forsikringer" to alleForsikringer.map {
                                when (it) {
                                    is KollektivForsikring -> "Kollektiv forsikring for ${it.spesiellYrkesgruppe}"
                                    is NavKjøptForsikring -> "Nav-kjøpt forsikring av type ${it.type}"
                                }
                            })
                            error(message)
                        }

                        val dekning = dekninger.minByOrNull { it.fraDag }
                        val løsning = dekning?.let { Løsning.MedForsikring(oppslagId = oppslag.id, dekning = it) }
                            ?: Løsning.UtenForsikring(oppslagId = oppslag.id)

                        packet["@løsning"] = mapOf("Sykepengeforsikring" to løsning)
                        context.publish(packet.toJson())
                    }
                }
            } catch (err: Exception) {
                loggError("Feil ved håndtering av Sykepengeforsikring-behov", err, "melding" to packet.toJson())
                throw err
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
