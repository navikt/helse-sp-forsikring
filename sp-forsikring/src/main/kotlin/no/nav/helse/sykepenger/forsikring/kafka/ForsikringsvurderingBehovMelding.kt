package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.*

data class ForsikringsvurderingBehovMelding(
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    @JsonProperty("Forsikringsvurdering")
    val forsikringsvurdering: Forsikringsvurdering,
) {
    data class Forsikringsvurdering(
        val rettighetskategoriId: UUID,
        val skjæringstidspunkt: LocalDate,
    )
}
