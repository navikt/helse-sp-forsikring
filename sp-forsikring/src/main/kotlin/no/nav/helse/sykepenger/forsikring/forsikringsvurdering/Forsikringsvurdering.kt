package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.time.LocalDate
import java.util.*
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring
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
    val ekskluderinger: List<EkskluderingNavKjøptForsikring>,
    val harForsikring: Boolean,
    val dekning: Dekning?,
    val opphørsdato: LocalDate?,
) {
    data class Dekning(val iVentetid: Boolean, val grad: Int)

    data class EkskluderingNavKjøptForsikring(
        val oppslagIfVedfrivt10Id: OppslagIfVedrift10Id,
        val ekskluderingsårsak: AbstractNavKjøptForsikring.Ekskluderingsårsak,
    )

    companion object {
        fun ny(
            oppslagId: OppslagId,
            behovJson: String,
            ekskluderinger: List<EkskluderingNavKjøptForsikring>,
            harForsikring: Boolean,
            dekning: Dekning?,
            opphørsdato: LocalDate?,
        ) = Forsikringsvurdering(
            id = ForsikringsvurderingId.ny(),
            oppslagId = oppslagId,
            behovJson = behovJson,
            ekskluderinger = ekskluderinger,
            harForsikring = harForsikring,
            dekning = dekning,
            opphørsdato = opphørsdato,
        )

        fun fraLagring(
            id: ForsikringsvurderingId,
            oppslagId: OppslagId,
            behovJson: String,
            ekskluderinger: List<EkskluderingNavKjøptForsikring>,
            harForsikring: Boolean,
            dekning: Dekning?,
            opphørsdato: LocalDate?,
        ) = Forsikringsvurdering(
            id = id,
            oppslagId = oppslagId,
            behovJson = behovJson,
            ekskluderinger = ekskluderinger,
            harForsikring = harForsikring,
            dekning = dekning,
            opphørsdato = opphørsdato
        )
    }
}
