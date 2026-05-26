package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.LocalDate

data class NavKjøptForsikring(
    val type: Type,
    val virkningsdato: LocalDate,
    val tom: LocalDate?,
) {
    enum class Type {
        SELVSTENDIG_80_PROSENT_FRA_DAG_1,
        SELVSTENDIG_100_PROSENT_FRA_DAG_17,
        SELVSTENDIG_100_PROSENT_FRA_DAG_1,
        SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
        FRILANSER_100_PROSENT_FRA_DAG_1,
    }
}
