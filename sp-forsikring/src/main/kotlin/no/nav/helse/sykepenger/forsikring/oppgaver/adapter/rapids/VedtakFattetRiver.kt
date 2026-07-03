package no.nav.helse.sykepenger.forsikring.oppgaver.adapter.rapids

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.seam.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.beregnAvvik
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import no.nav.helse.sykepenger.forsikring.oppgaver.seam.OppgaveoppretterClient
import no.nav.helse.sykepenger.forsikring.oppslag.seam.OppslagRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.logging.medMdc

private const val EVENT_NAME = "vedtak_fattet"

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsvurderingRepository: ForsikringsvurderingRepository,
    private val oppslagRepository: OppslagRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireValue("yrkesaktivitetstype", "SELVSTENDIG")
                it.requireContains("tags", "Førstegangsbehandling")
                it.requireKey("forsikringsvurderingId")
            }
            validate {
                it.requireKey("fødselsnummer", "sykepengegrunnlag", "skjæringstidspunkt", "@id")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asString().toBigDecimal()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val forsikringsvurderingId = ForsikringsvurderingId.fromString(packet["forsikringsvurderingId"].asString())
        val meldingId = UUID.fromString(packet["@id"].asString())

        medMdc(MdcKey.MELDING_ID to meldingId.toString(), MdcKey.FORSIKRINGSVURDERING_ID to forsikringsvurderingId.toString()) {
            loggInfo("Mottok VedtakFattet-melding", "behov" to packet.toJson())

            val forsikringsvurdering = forsikringsvurderingRepository.hent(forsikringsvurderingId)
                ?: error("Fant ikke vurdering for forsikringsvurderingId=$forsikringsvurderingId")

            if (!forsikringsvurdering.harForsikring || forsikringsvurdering.forsikringskategori == Forsikringskategori.KollektivForsikring) return@medMdc

            val premiegrunnlag = oppslagRepository.hent(forsikringsvurdering.oppslagId).navKjøpteForsikringer.find { it.id == (forsikringsvurdering.forsikringskategori as? Forsikringskategori.NavKjøptForsikring)?.id }?.premiegrunnlag
                ?: run {
                    loggError("Fant ikke premiegrunnlag for forsikringsvurderingId=$forsikringsvurderingId")
                    return@medMdc
                }

            if (sykepengegrunnlag.compareTo(premiegrunnlag) != 0) {
                val avviksprosent = beregnAvvik(sykepengegrunnlag, premiegrunnlag)
                loggInfo(
                    """Avvik mellom sykepengegrunnlag og premiegrunnlag. Oppretter oppgave.
                   Sykepengegrunnlag: ${sykepengegrunnlag.setScale(2)}
                   Premiegrunnlag: $premiegrunnlag
                   Avviksprosent: ${avviksprosent.setScale(2)}
                """.trimIndent(), "fødselsnummer" to fødselsnummer
                )

                runBlocking {
                    oppgaveClient.lagOppgave(
                        duplikatkontrollId = meldingId,
                        fødselsnummer = fødselsnummer,
                        årsak = Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(sykepengegrunnlag, premiegrunnlag, avviksprosent),
                        skjæringstidspunkt = skjæringstidspunkt
                    )
                }
            }
        }
    }
}
