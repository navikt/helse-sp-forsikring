package no.nav.helse.sykepenger.forsikring.domain

enum class IndividuellForsikringType(
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val tilleggsforsikringFor: KollektivForsikring? = null,
    override val dekning: Forsikringsdekning,
    override val navn: String,
) : Forsikringstype {
    SELVSTENDIG_80_PROSENT_FRA_DAG_1(
        yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        dekning = Forsikringsdekning.ÅTTI_PROSENT_FRA_DAG_1,
        navn = "Selvstendig næringsdrivende 80 % fra 1. dag",
    ),
    SELVSTENDIG_100_PROSENT_FRA_DAG_17(
        yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_17,
        navn = "Selvstendig næringsdrivende 100 % fra 17. dag",
    ),
    SELVSTENDIG_100_PROSENT_FRA_DAG_1(
        yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1,
        navn = "Selvstendig næringsdrivende 100 % fra 1. dag",
    ),
    SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1(
        yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
        tilleggsforsikringFor = KollektivForsikring.JORDBRUKER,
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1,
        navn = "Jordbruker tilleggsforsikring 100 % fra 1. dag",
    ),
    FRILANSER_100_PROSENT_FRA_DAG_1(
        yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS,
        dekning = Forsikringsdekning.HUNDRE_PROSENT_FRA_DAG_1,
        navn = "Frilanser 100 % fra 1. dag",
    ),
    ;

    fun passerMed(
        yrkesaktivitetstype: Yrkesaktivitetstype,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    ): Boolean =
        yrkesaktivitetstype == this.yrkesaktivitetstype &&
            (
                tilleggsforsikringFor == null ||
                    spesielleYrkesgrupper.any { it in tilleggsforsikringFor.spesielleYrkesgrupper }
            )
}
