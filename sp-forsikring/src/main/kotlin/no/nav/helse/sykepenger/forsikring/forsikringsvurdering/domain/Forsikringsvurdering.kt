package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain

import java.time.LocalDate
import java.util.*
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagIfVedrift10Id
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7

@JvmInline
value class ForsikringsvurderingId(val value: UUID) {
    companion object {
        fun ny() = ForsikringsvurderingId(generateUuidV7())
        fun fromString(id: String) = ForsikringsvurderingId(UUID.fromString(id))
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
    val forsikringskategori: Forsikringskategori?,
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
            forsikringskategori: Forsikringskategori?,
        ) = Forsikringsvurdering(
            id = ForsikringsvurderingId.ny(),
            oppslagId = oppslagId,
            behovJson = behovJson,
            ekskluderinger = ekskluderinger,
            harForsikring = harForsikring,
            dekning = dekning,
            opphørsdato = opphørsdato,
            forsikringskategori = forsikringskategori,
        )

        fun fraLagring(
            id: ForsikringsvurderingId,
            oppslagId: OppslagId,
            behovJson: String,
            ekskluderinger: List<EkskluderingNavKjøptForsikring>,
            harForsikring: Boolean,
            dekning: Dekning?,
            opphørsdato: LocalDate?,
            forsikringskategori: Forsikringskategori?,
        ) = Forsikringsvurdering(
            id = id,
            oppslagId = oppslagId,
            behovJson = behovJson,
            ekskluderinger = ekskluderinger,
            harForsikring = harForsikring,
            dekning = dekning,
            opphørsdato = opphørsdato,
            forsikringskategori = forsikringskategori,
        )
    }
}
