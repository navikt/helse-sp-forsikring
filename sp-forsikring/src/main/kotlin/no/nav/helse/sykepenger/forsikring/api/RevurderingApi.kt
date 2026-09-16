package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.RevurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Revurderingsresultat
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.coMedMdc
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.LocalDate
import java.util.*

internal fun Route.revurderingApi(
    revurderingService: RevurderingService,
) {
    post("/revurdering") {
        val request = call.receive<RevurderingRequest>()
        coMedMdc(
            MdcKey.IDENTITETSNUMMER to request.identitetsnummer,
            MdcKey.VEDTAKSPERIODE_ID to request.vedtaksperiodeId.toString(),
            MdcKey.SPLEIS_BEHANDLING_ID to request.behandlingId.toString(),
        ) {
            loggInfo(
                "Mottok kall til POST /revurdering",
                "skjæringstidspunkt" to request.skjæringstidspunkt.toString(),
            )

            val identitetsnummer = Identitetsnummer.fraString(request.identitetsnummer)

            val revurderingsresultat =
                revurderingService.revurder(
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    vedtaksperiodeId = request.vedtaksperiodeId,
                    behandlingId = request.behandlingId,
                ) { forrigeForsikringsvurdering -> request.tilBehovJson(forrigeForsikringsvurdering) }

            when (revurderingsresultat) {
                is Revurderingsresultat.IngenTidligereVurdering -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ProblemResponse(
                            title = "Forsikringsvurderinger ikke funnet",
                            status = HttpStatusCode.NotFound.value,
                            detail = "Fant ingen forsikringsvurderinger for skjæringstidspunkt ${request.skjæringstidspunkt}",
                            instance = call.request.uri,
                        ),
                    )
                }

                is Revurderingsresultat.UendretVurdering -> {
                    loggInfo("Svarer på POST /revurdering ingen ny vurdering")
                    call.respond(HttpStatusCode.OK)
                }

                is Revurderingsresultat.EndretVurdering -> {
                    val response = revurderingsresultat.forsikringsvurdering.tilSpesialistResponse()
                    loggInfo("Svarer på POST /revurdering med ny vurdering", "response" to response.toString())
                    call.respond(response)
                }
            }
        }
    }
}

data class RevurderingRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
)

private fun RevurderingRequest.tilBehovJson(forrigeVurdering: Forsikringsvurdering): String =
    behovJsonMapper.writeValueAsString(
        RevurderingBehov(
            fødselsnummer = identitetsnummer,
            yrkesaktivitetstype = forrigeVurdering.yrkesaktivitetstype.name,
            forrigeForsikringsvurderingId = forrigeVurdering.id.value.toString(),
            forsikringsvurdering =
                RevurderingBehov.Vurderingsgrunnlag(
                    spesielleYrkesgrupper = forrigeVurdering.spesielleYrkesgrupper.map { it.name },
                    skjæringstidspunkt = skjæringstidspunkt.toString(),
                ),
        ),
    )

private val behovJsonMapper = ObjectMapper()

@JsonPropertyOrder(
    "kilde",
    "fødselsnummer",
    "yrkesaktivitetstype",
    "forrigeForsikringsvurderingId",
    "Forsikringsvurdering",
)
private data class RevurderingBehov(
    val kilde: String = "POST /revurdering",
    val fødselsnummer: String,
    val yrkesaktivitetstype: String,
    val forrigeForsikringsvurderingId: String,
    @JsonProperty("Forsikringsvurdering") val forsikringsvurdering: Vurderingsgrunnlag,
) {
    data class Vurderingsgrunnlag(
        val spesielleYrkesgrupper: List<String>,
        val skjæringstidspunkt: String,
    )
}
