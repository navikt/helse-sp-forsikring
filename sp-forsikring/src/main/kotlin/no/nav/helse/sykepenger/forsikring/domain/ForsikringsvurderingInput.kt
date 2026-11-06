package no.nav.helse.sykepenger.forsikring.domain

import java.time.LocalDate
import java.util.*

data class ForsikringsvurderingInput(
    val identitetsnummer: Identitetsnummer,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    val skjæringstidspunkt: LocalDate,
    /** Mangler for vurderinger fra Flex og for vurderinger gjort før vi hentet rettighetskategorien. */
    val rettighetskategoriId: UUID?,
)
