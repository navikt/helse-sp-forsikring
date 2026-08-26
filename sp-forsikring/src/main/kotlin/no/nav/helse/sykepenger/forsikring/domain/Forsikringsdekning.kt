package no.nav.helse.sykepenger.forsikring.domain

enum class Forsikringsdekning(
    override val grad: Int,
    override val fraDag: Int,
) : Dekning {
    ÅTTI_PROSENT_FRA_DAG_1(grad = 80, fraDag = 1),
    HUNDRE_PROSENT_FRA_DAG_17(grad = 100, fraDag = 17),
    HUNDRE_PROSENT_FRA_DAG_1(grad = 100, fraDag = 1),
}
