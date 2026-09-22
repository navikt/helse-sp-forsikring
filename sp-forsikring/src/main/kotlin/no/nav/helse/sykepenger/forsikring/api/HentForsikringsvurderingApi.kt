package no.nav.helse.sykepenger.forsikring.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.EndringssjekkLoggDao
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.sykepenger.libs.logging.loggInfo
import javax.sql.DataSource

internal fun Route.hentForsikringsvurderingApi(spForsikringDataSource: DataSource) {
    get("/forsikringsvurderinger/{forsikringsvurderingId}") {
        val rawId = call.parameters["forsikringsvurderingId"]
        val id =
            rawId?.let { runCatching { Forsikringsvurdering.Id.fromString(it) }.getOrNull() }
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ProblemResponse(
                        title = "Ugyldig forsikringsvurderingId",
                        status = HttpStatusCode.BadRequest.value,
                        detail = "forsikringsvurderingId må være en gyldig UUID",
                        instance = call.request.uri,
                    ),
                )
        loggInfo("Mottok kall til GET /forsikringsvurderinger/${id.value}")

        val resultat =
            spForsikringDataSource.inTransaction { transactionalSession ->
                ForsikringsvurderingRepository(transactionalSession).hent(id)?.let { vurdering ->
                    vurdering to EndringssjekkLoggDao(transactionalSession).hentSistHentet(id)
                }
            } ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ProblemResponse(
                    title = "Forsikringsvurdering ikke funnet",
                    status = HttpStatusCode.NotFound.value,
                    detail = "Fant ingen forsikringsvurdering med id ${id.value}",
                    instance = call.request.uri,
                ),
            )

        val (forsikringsvurdering, sistHentet) = resultat
        val response = forsikringsvurdering.tilSpesialistResponse(sistHentet?.tilResponse())

        loggInfo("Svarer på GET /forsikringsvurderinger/$id", "response" to response.toString())

        call.respond(response)
    }
}
