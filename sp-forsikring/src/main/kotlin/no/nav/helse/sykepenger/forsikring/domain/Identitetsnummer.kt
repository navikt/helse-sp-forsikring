package no.nav.helse.sykepenger.forsikring.domain

@JvmInline
value class Identitetsnummer(
    val value: String,
) {
    companion object {
        fun fraString(identitetsnummer: String): Identitetsnummer {
            require(identitetsnummer.matches(Regex("\\d{11}"))) {
                "identitetsnummer må bestå av nøyaktig 11 siffer"
            }
            return Identitetsnummer(identitetsnummer)
        }
    }
}
