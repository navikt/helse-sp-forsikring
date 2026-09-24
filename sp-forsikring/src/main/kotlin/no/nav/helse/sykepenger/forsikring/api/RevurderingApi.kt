package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
import no.nav.sykepenger.libs.logging.loggWarn
import java.time.LocalDate
import java.util.*

internal fun Route.revurderingApi(
    revurderingService: RevurderingService,
    populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
) {
    post("/revurdering") {
        val request = call.receive<RevurderingRequest>()
        val saksbehandlerIdent = call.authentication.principal<JWTPrincipal>()?.get("NAVident")
        coMedMdc(
            MdcKey.IDENTITETSNUMMER to request.identitetsnummer,
            MdcKey.VEDTAKSPERIODE_ID to request.vedtaksperiodeId.toString(),
            MdcKey.SPLEIS_BEHANDLING_ID to request.behandlingId.toString(),
            MdcKey.SAKSBEHANDLER_IDENT to saksbehandlerIdent,
        ) {
            loggInfo(
                "Mottok kall til POST /revurdering",
                "skjæringstidspunkt" to request.skjæringstidspunkt.toString(),
            )

            val identitetsnummer = Identitetsnummer.fraString(request.identitetsnummer)

            when (
                val tilgangsresultat =
                    populasjonstilgangskontrollProvider.kontrollerKjerneTilgang(
                        accessToken =
                            call.request.headers[HttpHeaders.Authorization]
                                ?.removePrefix("Bearer ") ?: error("Mangler access token"),
                        fødselsnummer = identitetsnummer.value,
                    )
            ) {
                is TilgangskontrollResultat.Ok -> {
                }

                is TilgangskontrollResultat.ManglerTilgang -> {
                    loggWarn(
                        "403: populasjonstilgangskontrollen ga avslag",
                        "navIdent" to saksbehandlerIdent,
                        "tilgangSomMangler" to tilgangsresultat.tilgangSomMangler.name,
                    )
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ProblemResponse(
                            title = "Mangler tilgang til person",
                            status = HttpStatusCode.Forbidden.value,
                            detail = "",
                            instance = call.request.uri,
                        ),
                    )
                    return@coMedMdc
                }

                is TilgangskontrollResultat.IdentIkkeFunnet -> {
                    loggWarn(
                        "400: Tilgangsmaskinen sa ident ikke funnet",
                        "navIdent" to saksbehandlerIdent,
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ProblemResponse(
                            title = "Person ikke funnet",
                            status = HttpStatusCode.BadRequest.value,
                            detail = "",
                            instance = call.request.uri,
                        ),
                    )
                    return@coMedMdc
                }

                is TilgangskontrollResultat.UventetFeil -> {
                    loggWarn(
                        "500: Uventet feil i tilgangsmaskinen",
                        "navIdent" to saksbehandlerIdent,
                        "forklaring" to tilgangsresultat.menneskeligLesbarForklaring,
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ProblemResponse(
                            title = "Uventet feil",
                            status = HttpStatusCode.InternalServerError.value,
                            detail = "",
                            instance = call.request.uri,
                        ),
                    )
                    return@coMedMdc
                }
            }
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
                        HttpStatusCode.BadRequest,
                        ProblemResponse(
                            title = "Forsikringsvurderinger ikke funnet",
                            status = HttpStatusCode.BadRequest.value,
                            detail = "Fant ingen forsikringsvurderinger for skjæringstidspunkt ${request.skjæringstidspunkt}",
                            instance = call.request.uri,
                        ),
                    )
                }

                else -> {
                    val response =
                        RevurderingResponse(
                            vurderingErEndret =
                                when (revurderingsresultat) {
                                    is Revurderingsresultat.UendretVurdering -> false
                                    is Revurderingsresultat.EndretVurdering -> true
                                },
                        )
                    loggInfo("Svarer på POST /revurdering", "response" to response.toString())
                    call.respond(HttpStatusCode.OK, response)
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

data class RevurderingResponse(
    val vurderingErEndret: Boolean,
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
