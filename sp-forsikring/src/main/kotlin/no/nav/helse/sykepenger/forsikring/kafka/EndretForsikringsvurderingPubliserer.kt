package no.nav.helse.sykepenger.forsikring.kafka

import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import java.time.LocalDate
import java.util.*

interface EndretForsikringsvurderingPubliserer {
    fun publiser(
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
        forsikringsvurderingId: UUID,
    )
}
