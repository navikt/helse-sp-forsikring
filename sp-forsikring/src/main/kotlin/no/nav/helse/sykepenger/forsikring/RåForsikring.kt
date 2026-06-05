package no.nav.helse.sykepenger.forsikring

import java.time.LocalDate

data class Betaling(
    val fom: LocalDate?,
    val tom: LocalDate?,
    val betdato: LocalDate?,
)

data class RåForsikring(
    val id: Int,
    val type: NavKjøptForsikring.Type,
    val forsikringFom: LocalDate?,
    val virkningsdato: LocalDate,
    val opphørsdato: LocalDate?,
    val betalinger: List<Betaling>,
) : Forsikring {
    fun erIOpptjeningstid(dato: LocalDate) =
        forsikringFom != null && dato >= forsikringFom && dato < virkningsdato

    fun harVirkningPå(dato: LocalDate) = virkningsdato <= dato

    fun erOpphørtPå(dato: LocalDate) = opphørsdato != null && dato > opphørsdato

    fun erBetaltNoenGang() = betalinger.any { it.betdato != null }

    fun erBetaltForSkjæringstidspunkt(dato: LocalDate) =
        betalinger.any { betaling ->
            betaling.betdato != null &&
                (betaling.fom == null || betaling.fom <= dato) &&
                (betaling.tom == null || betaling.tom >= dato)
        }

    fun validerType(yrkesaktivitetstype: Yrkesaktivitetstype, spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>) {
        when (type) {
            NavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
            NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
            NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 ->
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype)

            NavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> {
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype)
                validerEnAv(spesielleYrkesgrupper, SpesiellYrkesgruppe.Jordbruker, SpesiellYrkesgruppe.Reindrifter)
            }

            NavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1 ->
                valider(Yrkesaktivitetstype.FRILANS, yrkesaktivitetstype)
        }
    }

    override fun dekningGrad() = type.dekningGrad
    override fun dekningFraDag() = type.dekningFraDag

    private fun valider(forventet: Yrkesaktivitetstype, faktisk: Yrkesaktivitetstype) {
        if (faktisk != forventet) {
            throw NavKjøptForsikring.Valideringsfeil(
                "Nav-kjøpt forsikring er av type $type, " +
                    "der forventet yrkesaktivitetstype er $forventet, " +
                    "men yrkesaktivitetstypen var $faktisk"
            )
        }
    }

    private fun validerEnAv(
        faktiskeSpesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        vararg forventetEnAv: SpesiellYrkesgruppe,
    ) {
        if (faktiskeSpesielleYrkesgrupper.none { it in forventetEnAv }) {
            throw NavKjøptForsikring.Valideringsfeil(
                "Nav-kjøpt forsikring er av type $type, " +
                    "der det var forventet at spesielle yrkesgrupper inneholdt en av ${forventetEnAv.toSet()}, " +
                    "men spesielle yrkesgrupper var $faktiskeSpesielleYrkesgrupper"
            )
        }
    }
}
