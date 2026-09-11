package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.kafka.lib.medParsetMeldingOgTransaksjon
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import javax.sql.DataSource

class ForsikringsvurderingResultatBehovRiver(
    rapidsConnection: RapidsConnection,
    private val spForsikringDataSource: DataSource,
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
        try {
            packet.medParsetMeldingOgTransaksjon<ForsikringsvurderingResultatBehovMelding>(
                mdcMapping =
                    mapOf(
                        MdcKey.MELDING_ID to ForsikringsvurderingResultatBehovMelding::id,
                        MdcKey.FORSIKRINGSVURDERING_ID to { forsikringsvurderingResultat.forsikringsvurderingId },
                    ),
                dataSource = spForsikringDataSource,
            ) { melding, transaction ->
                val forsikringsvurderingId = Forsikringsvurdering.Id(melding.forsikringsvurderingResultat.forsikringsvurderingId)

                val forsikringsvurdering =
                    ForsikringsvurderingRepository(transaction).hent(forsikringsvurderingId)
                        ?: error("Fant ikke forsikringsvurdering med id ${forsikringsvurderingId.value}")

                packet["@løsning"] =
                    mapOf(
                        "ForsikringsvurderingResultat" to
                            mapOf(
                                "forsikringsvurderingId" to forsikringsvurdering.id.value.toString(),
                                "harForsikring" to forsikringsvurdering.harForsikring(),
                                "villeHattForsikringOmDenVarBetalt" to forsikringsvurdering.villeHattForsikringOmDenVarBetalt(),
                                "harForsikringSomIkkePasserMedSøknadstype" to forsikringsvurdering.harForsikringSomIkkePasserMedSøknadstype(),
                                "dekning" to
                                    forsikringsvurdering.dekning()?.let { dekning ->
                                        mapOf(
                                            "iVentetid" to (dekning.fraDag == 1),
                                            "grad" to dekning.grad,
                                        )
                                    },
                                "opphørsdato" to forsikringsvurdering.opphørsdato(),
                                "harIndividuellForsikring" to forsikringsvurdering.harIndividuellForsikring(),
                            ),
                    )

                val løsningJson = packet.toJson()
                loggInfo("Svarer på ForsikringsvurderingResultat-behov med løsning", "løsning" to løsningJson)
                context.publish(løsningJson)
            }
        } catch (err: Exception) {
            // Logg feilen og gå videre. Meldingen hoppes over siden vi ikke kaster exception ut av onPacket().
            loggError(
                "Feil ved håndtering av ForsikringsvurderingResultat-behov",
                err,
                "melding" to packet.toJson(),
            )
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
