package no.nav.helse.sykepenger.forsikring.rettighetskategori

data class Rettighetskategori(
    val hovedkategori: Hovedkategori,
    val tilleggskategorier: Set<Tilleggskategori>,
) {
    enum class Hovedkategori {
        ARBEIDSTAKER,
        SELVSTENDIG_NAERINGSDRIVENDE,
        ARBEIDSTAKER_OG_SELVSTENDIG_NAERINGSDRIVENDE,
    }

    enum class Tilleggskategori {
        JORDBRUKER,
    }
}
