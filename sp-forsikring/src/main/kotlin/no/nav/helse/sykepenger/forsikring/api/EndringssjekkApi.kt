package no.nav.helse.sykepenger.forsikring.api

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.EndringssjekkService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Endringssjekkresultat
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.coMedMdc
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.loggWarn
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.LocalDate

internal fun Route.endringssjekkApi(
    endringssjekkService: EndringssjekkService,
    populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
) {
    post("/endringssjekk") {
        val requestBody = call.receiveText()
        val request = objectMapper.readValue(requestBody, EndringssjekkRequest::class.java)
        val saksbehandlerIdent =
            call.authentication.principal<JWTPrincipal>()?.get("NAVident") ?: error("Mangler NAVident i token")
        coMedMdc(
            MdcKey.IDENTITETSNUMMER to request.identitetsnummer,
            MdcKey.SAKSBEHANDLER_IDENT to saksbehandlerIdent,
        ) {
            loggInfo(
                "Mottok kall til POST /endringssjekk",
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
            val endringssjekkresultat =
                endringssjekkService.endringssjekk(
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    saksbehandlerIdent = saksbehandlerIdent,
                    requestBody = requestBody,
                )

            when (endringssjekkresultat) {
                is Endringssjekkresultat.IngenTidligereVurdering -> {
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
                        EndringssjekkResponse(
                            vurderingErEndret =
                                when (endringssjekkresultat) {
                                    is Endringssjekkresultat.UendretVurdering -> false
                                    is Endringssjekkresultat.EndretVurdering -> true
                                },
                        )
                    loggInfo("Svarer på POST /endringssjekk", "response" to response.toString())
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
}

data class EndringssjekkRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate,
)

data class EndringssjekkResponse(
    val vurderingErEndret: Boolean,
)

private val objectMapper =
    jacksonMapperBuilder()
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .build()
