package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.*

data class SelvstendigUtbetaltEtterVentetidMelding(
    @JsonProperty("@id")
    val id: UUID,
    val fødselsnummer: String,
    val forsikringsvurderingId: UUID,
    val skjæringstidspunkt: LocalDate,
)
