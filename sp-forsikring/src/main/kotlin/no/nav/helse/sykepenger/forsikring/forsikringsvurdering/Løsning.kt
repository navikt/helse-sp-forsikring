package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.util.*

// NB! Denne eksponeres direkte som svar på behov, endringer i denne typen medfører dermed
// endringer i kontrakten som konsumentene av løsningen må ta hensyn til
sealed class Løsning(val forsikringsvurderingId: ForsikringsvurderingId, val harForsikring: Boolean) {
    class UtenForsikring(
        forsikringsvurderingId: ForsikringsvurderingId
    ) : Løsning(forsikringsvurderingId = forsikringsvurderingId, harForsikring = false)

    class MedForsikring(
        forsikringsvurderingId: ForsikringsvurderingId,
        val dekning: Dekning
    ) : Løsning(forsikringsvurderingId = forsikringsvurderingId, harForsikring = true) {
        data class Dekning(val grad: Int, val fraDag: Int)
    }
}
