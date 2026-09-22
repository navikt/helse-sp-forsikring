package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import java.time.Instant
import java.util.*

data class EndringssjekkLogg(
    val id: UUID,
    val forsikringsvurderingId: Forsikringsvurdering.Id,
    val utførtAvSaksbehandlerIdent: String,
    val tidspunkt: Instant,
)
