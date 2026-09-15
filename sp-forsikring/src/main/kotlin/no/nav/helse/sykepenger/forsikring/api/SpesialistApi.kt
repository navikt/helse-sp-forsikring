package no.nav.helse.sykepenger.forsikring.api

import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.RevurderingService
import javax.sql.DataSource

internal fun Route.spesialistApi(
    spForsikringDataSource: DataSource,
    revurderingService: RevurderingService,
) {
    hentForsikringsvurderingApi(spForsikringDataSource)
    revurderingApi(revurderingService)
}
