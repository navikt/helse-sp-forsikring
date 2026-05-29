package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.util.*
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.generateUuidV7
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagId
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagIfVedrift10Id

@JvmInline
value class ForsikringsvurderingId(val value: UUID) {
    companion object {
        fun ny() = ForsikringsvurderingId(generateUuidV7())
    }
}

class Forsikringsvurdering private constructor(
    val id: ForsikringsvurderingId,
    val oppslagId: OppslagId,
    val behovJson: String,
    val løsning: Løsning,
    val ekskluderinger: List<EkskluderingNavKjøptForsikring>,
) {
    data class EkskluderingNavKjøptForsikring(
        val oppslagIfVedfrivt10Id: OppslagIfVedrift10Id,
        val ekskluderingsårsak: NavKjøptForsikring.Ekskluderingsårsak,
    )

    companion object {
        fun ny(
            id: ForsikringsvurderingId,
            oppslagId: OppslagId,
            behovJson: String,
            løsning: Løsning,
            ekskluderinger: List<EkskluderingNavKjøptForsikring>
        ) = Forsikringsvurdering(
            id = id,
            oppslagId = oppslagId,
            behovJson = behovJson,
            løsning = løsning,
            ekskluderinger = ekskluderinger
        )
    }
}
