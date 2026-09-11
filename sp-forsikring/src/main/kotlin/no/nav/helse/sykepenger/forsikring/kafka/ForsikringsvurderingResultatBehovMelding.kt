package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class ForsikringsvurderingResultatBehovMelding(
    @JsonProperty("ForsikringsvurderingResultat")
    val forsikringsvurderingResultat: ForsikringsvurderingResultat,
) {
    data class ForsikringsvurderingResultat(
        val forsikringsvurderingId: UUID,
    )
}
