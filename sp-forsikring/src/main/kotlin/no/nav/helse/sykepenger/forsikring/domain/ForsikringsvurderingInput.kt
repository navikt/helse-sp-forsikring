package no.nav.helse.sykepenger.forsikring.domain

import java.time.LocalDate

data class ForsikringsvurderingInput(
    val identitetsnummer: Identitetsnummer,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    val skjæringstidspunkt: LocalDate,
)
