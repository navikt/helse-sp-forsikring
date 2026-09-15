package no.nav.helse.sykepenger.forsikring.api

import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.RevurderingService
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import javax.sql.DataSource

internal fun Route.spesialistApi(
    spForsikringDataSource: DataSource,
    revurderingService: RevurderingService,
    subsumsjonspubliserer: Subsumsjonspubliserer,
) {
    hentForsikringsvurderingApi(spForsikringDataSource)
    revurderingApi(
        revurderingService = revurderingService,
        subsumsjonspubliserer = subsumsjonspubliserer,
    )
}
