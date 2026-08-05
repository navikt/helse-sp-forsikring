package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.AbstractNavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc
import java.util.*

class ForsikringsvurderingResultatBehovRiver(
    rapidsConnection: RapidsConnection,
    private val forsikringsvurderingRepository: ForsikringsvurderingRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf("ForsikringsvurderingResultat"))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey(
                        "@id",
                        "ForsikringsvurderingResultat.forsikringsvurderingId",
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val meldingId = packet["@id"].asString()
        val forsikringsvurderingId =
            ForsikringsvurderingId(
                UUID.fromString(packet["ForsikringsvurderingResultat.forsikringsvurderingId"].asString()),
            )

        medMdc(MdcKey.MELDING_ID to meldingId) {
            loggInfo("Mottok ForsikringsvurderingResultat-behov", "behov" to packet.toJson())
            try {
                val forsikringsvurdering =
                    forsikringsvurderingRepository.hent(forsikringsvurderingId)
                        ?: error("Fant ikke forsikringsvurdering med id ${forsikringsvurderingId.value}")

                packet["@løsning"] =
                    mapOf(
                        "ForsikringsvurderingResultat" to
                            mapOf(
                                "forsikringsvurderingId" to forsikringsvurdering.id.value.toString(),
                                "harForsikring" to forsikringsvurdering.harForsikring,
                                "villeHattForsikringOmDenVarBetalt" to forsikringsvurdering.ekskluderinger.any { it.ekskluderingsårsak == ALDRI_BETALT },
                                "dekning" to
                                    forsikringsvurdering.dekning?.let { dekning ->
                                        mapOf(
                                            "iVentetid" to dekning.iVentetid,
                                            "grad" to dekning.grad,
                                        )
                                    },
                                "opphørsdato" to forsikringsvurdering.opphørsdato,
                                "forsikringskategori" to forsikringsvurdering.forsikringskategori?.navn(),
                            ),
                    )

                val løsningJson = packet.toJson()
                loggInfo("Svarer på ForsikringsvurderingResultat-behov med løsning", "løsning" to løsningJson)
                context.publish(løsningJson)
            } catch (err: Exception) {
                loggError("Feil ved håndtering av ForsikringsvurderingResultat-behov", err, "melding" to packet.toJson())
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggError("Forstod ikke ForsikringsvurderingResultat-behov", "extendedReport" to problems.toExtendedReport())
    }
}
