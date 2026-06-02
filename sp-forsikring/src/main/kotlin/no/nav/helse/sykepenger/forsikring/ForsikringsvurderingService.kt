package no.nav.helse.sykepenger.forsikring

// 🔴 Rød sone: Implementer forretningslogikken her selv — forstå den grundig før du bruker AI.
interface ForsikringsvurderingService {
    fun hentForsikringsvurdering(fødselsnummer: String, callId: String): ForsikringsvurderingResultat?
}

class ForsikringsvurderingServiceImpl: ForsikringsvurderingService {
    override fun hentForsikringsvurdering(
        fødselsnummer: String,
        callId: String
    ): ForsikringsvurderingResultat? {
        // TODO: Implementer oppslag mot datakilde
        throw NotImplementedError("Implementer forsikringsvurderingsoppslag")
    }
}

data class ForsikringsvurderingResultat(
    // TODO: Legg til felter etter behov
    val forsikret: Boolean
)
