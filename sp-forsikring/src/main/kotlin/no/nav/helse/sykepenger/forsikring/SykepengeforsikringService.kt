package no.nav.helse.sykepenger.forsikring

// 🔴 Rød sone: Implementer forretningslogikken her selv — forstå den grundig før du bruker AI.
interface SykepengeforsikringService {
    fun hentSykepengeforsikring(fødselsnummer: String, callId: String): SykepengeforsikringResultat?
}

class SykepengeforsikringServiceImpl: SykepengeforsikringService {
    override fun hentSykepengeforsikring(
        fødselsnummer: String,
        callId: String
    ): SykepengeforsikringResultat? {
        // TODO: Implementer oppslag mot datakilde
        throw NotImplementedError("Implementer sykepengeforsikringsoppslag")
    }
}

data class SykepengeforsikringResultat(
    // TODO: Legg til felter etter behov
    val forsikret: Boolean
)
