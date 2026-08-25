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

    fun validerMot(
        yrkesaktivitetstype: Yrkesaktivitetstype,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    ) {
        validerYrkesaktivitetstype(forventet = this.yrkesaktivitetstype, faktisk = yrkesaktivitetstype)
        if (tilleggsforsikringFor != null) {
            validerSpesielleYrkesgrupperInneholderEnAv(
                forventetEnAv = tilleggsforsikringFor.spesielleYrkesgrupper,
                faktiske = spesielleYrkesgrupper,
            )
        }
    }

    class Valideringsfeil(
        message: String,
    ) : Exception(message)

    private fun validerYrkesaktivitetstype(
        forventet: Yrkesaktivitetstype,
        faktisk: Yrkesaktivitetstype,
    ) {
        if (faktisk != forventet) {
            throw Valideringsfeil(
                "Individuell forsikring er av type $this, " +
                    "der forventet yrkesaktivitetstype er $forventet, " +
                    "men yrkesaktivitetstypen var $faktisk",
            )
        }
    }

    fun validerSpesielleYrkesgrupperInneholderEnAv(
        forventetEnAv: Set<SpesiellYrkesgruppe>,
        faktiske: Set<SpesiellYrkesgruppe>,
    ) {
        if (faktiske.none { it in forventetEnAv }) {
            throw Valideringsfeil(
                "Individuell forsikring er av type $this, " +
                    "der det var forventet at spesielle yrkesgrupper inneholdt en av ${forventetEnAv.toSet()}, " +
                    "men spesielle yrkesgrupper var $faktiske",
            )
        }
    }
}
