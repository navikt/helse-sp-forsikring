package no.nav.helse.sykepenger.forsikring.domain

import java.time.LocalDate

enum class KollektivForsikring(
    val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    override val dekning: Forsikringsdekning,
    override val navn: String,
    val folketrygdlovenreferanse: Folketrygdlovenreferanse,
) : Forsikringstype {
    FISKER_BLAD_B(
        spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1,
        navn = "Fisker Blad B 100 % fra 1. dag",
        folketrygdlovenreferanse =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                versjon = LocalDate.parse("2019-10-01"),
                ledd = 1,
                bokstav = 'c',
                punktum = null,
            ),
    ),
    JORDBRUKER(
        spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER, SpesiellYrkesgruppe.REINDRIFTER),
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_17,
        navn = "Jordbruker 100 % fra 17. dag",
        folketrygdlovenreferanse =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                versjon = LocalDate.parse("2019-10-01"),
                ledd = 1,
                bokstav = 'b',
                punktum = null,
            ),
    ),
    ;

    companion object {
        val KOLLEKTIV_FORSIKRING_GENERELL_FOLKETRYGDLOVENREFERANSE =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                versjon = LocalDate.parse("2019-10-01"),
                ledd = 4,
                bokstav = null,
                punktum = null,
            )
    }
}
