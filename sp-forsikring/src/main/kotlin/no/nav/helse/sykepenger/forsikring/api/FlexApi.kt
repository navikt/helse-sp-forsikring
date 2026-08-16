package no.nav.helse.sykepenger.forsikring.api

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import java.time.LocalDate

fun Route.flexApi(forsikringsvurderingService: ForsikringsvurderingService) {
    post("/api/forsikringsvurdering") {
        val request = call.receive<ForsikringsvurderingRequest>()
        val identitetsnummer = Identitetsnummer.fraString(request.identitetsnummer)
        loggInfo("Mottok kall til POST /api/forsikringsvurdering", "request" to request)

        val (_, forsikringsvurdering) =
            forsikringsvurderingService.gjørForsikringsvurdering(
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype =
                    when (request.yrkesaktivitetstype) {
                        ForsikringsvurderingRequest.Yrkesaktivitetstype.ARBEIDSTAKER -> Yrkesaktivitetstype.ARBEIDSTAKER
                        ForsikringsvurderingRequest.Yrkesaktivitetstype.FRILANS -> Yrkesaktivitetstype.FRILANS
                        ForsikringsvurderingRequest.Yrkesaktivitetstype.ARBEIDSLEDIG -> Yrkesaktivitetstype.ARBEIDSLEDIG
                        ForsikringsvurderingRequest.Yrkesaktivitetstype.SELVSTENDIG -> Yrkesaktivitetstype.SELVSTENDIG
                    },
                spesielleYrkesgrupper =
                    request.spesielleYrkesgrupper
                        .map { spesiellYrkesgruppeString ->
                            when (spesiellYrkesgruppeString) {
                                ForsikringsvurderingRequest.SpesiellYrkesgruppe.FISKER_BLAD_B -> SpesiellYrkesgruppe.FISKER_BLAD_B
                                ForsikringsvurderingRequest.SpesiellYrkesgruppe.JORDBRUKER -> SpesiellYrkesgruppe.JORDBRUKER
                                ForsikringsvurderingRequest.SpesiellYrkesgruppe.REINDRIFTER -> SpesiellYrkesgruppe.REINDRIFTER
                            }
                        }.toSet(),
                skjæringstidspunkt = request.skjæringstidspunkt,
            )

        val response =
            ForsikringsvurderingResponse(
                harForsikringMedDekningIVentetid = forsikringsvurdering.harDekningIVentetidUavhengigAvBetaling(),
            )

        loggInfo("Svarer på POST /api/forsikringsvurdering", "response" to response)

        call.respond(response)
    }
}

data class ForsikringsvurderingRequest(
    val identitetsnummer: String,
    val yrkesaktivitetstype: ForsikringsvurderingRequest.Yrkesaktivitetstype,
    val spesielleYrkesgrupper: Set<ForsikringsvurderingRequest.SpesiellYrkesgruppe>,
    val skjæringstidspunkt: LocalDate,
) {
    enum class Yrkesaktivitetstype {
        ARBEIDSTAKER,
        FRILANS,
        ARBEIDSLEDIG,
        SELVSTENDIG,
    }

    enum class SpesiellYrkesgruppe {
        FISKER_BLAD_B,
        JORDBRUKER,
        REINDRIFTER,
    }
}

data class ForsikringsvurderingResponse(
    val harForsikringMedDekningIVentetid: Boolean,
)
