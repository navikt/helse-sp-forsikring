package no.nav.helse.sykepenger.forsikring.domain

enum class Forsikringsdekning(
    override val grad: Int,
    override val fraDag: Int,
    val folketrygdlovenreferanse: Folketrygdlovenreferanse,
) : Dekning {
    ÅTTI_PROSENT_FRA_DAG_1(
        grad = 80,
        fraDag = 1,
        folketrygdlovenreferanse =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'a',
            ),
    ),
    HUNDRE_PROSENT_FRA_DAG_17(
        grad = 100,
        fraDag = 17,
        folketrygdlovenreferanse =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'b',
            ),
    ),
    HUNDRE_PROSENT_FRA_DAG_1(
        grad = 100,
        fraDag = 1,
        folketrygdlovenreferanse =
            Folketrygdlovenreferanse(
                kapittel = 8,
                paragrafIKapittel = 36,
                ledd = 1,
                bokstav = 'c',
            ),
    ),
}
