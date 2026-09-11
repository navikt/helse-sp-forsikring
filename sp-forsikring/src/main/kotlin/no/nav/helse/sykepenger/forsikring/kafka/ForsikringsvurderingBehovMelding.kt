package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.*

data class ForsikringsvurderingBehovMelding(
    @JsonProperty("@id")
    val id: UUID,
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    @JsonProperty("Forsikringsvurdering")
    val forsikringsvurdering: Forsikringsvurdering,
) {
    data class Forsikringsvurdering(
        val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        val skjæringstidspunkt: LocalDate,
    )

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
