package no.nav.helse.sykepenger.forsikring.domain

enum class KollektivForsikring(
    val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    override val dekning: Forsikringsdekning,
) : Forsikringstype {
    FISKER_BLAD_B(
        spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1,
    ),
    JORDBRUKER(
        spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER, SpesiellYrkesgruppe.REINDRIFTER),
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_17,
    ),
    ;

    companion object {
        val KOLLEKTIV_FORSIKRING_GENERELL_FOLKETRYGDLOVENREFERANSE =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 4,
                bokstav = null,
            )
    }
}
