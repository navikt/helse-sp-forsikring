package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.LocalDate
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.Yrkesaktivitetstype

data class NavKjøptForsikring(
    val type: Type,
    val virkningsdato: LocalDate,
    val opphørsdato: LocalDate?,
) {
    enum class Type {
        SELVSTENDIG_80_PROSENT_FRA_DAG_1,
        SELVSTENDIG_100_PROSENT_FRA_DAG_17,
        SELVSTENDIG_100_PROSENT_FRA_DAG_1,
        SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
        FRILANSER_100_PROSENT_FRA_DAG_1,
    }

    fun harVirkningPå(dato: LocalDate) =
        virkningsdato > dato

    fun erOpphørtPå(dato: LocalDate) =
        opphørsdato != null && dato > opphørsdato

    fun validerType(yrkesaktivitetstype: Yrkesaktivitetstype, spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>) {
        when (type) {
            Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 ->
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)

            Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 ->
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)

            Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 ->
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)

            Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> {
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)
                validerEnAv(spesielleYrkesgrupper, SpesiellYrkesgruppe.Jordbruker, SpesiellYrkesgruppe.Reindrifter)
            }

            Type.FRILANSER_100_PROSENT_FRA_DAG_1 ->
                valider(Yrkesaktivitetstype.FRILANS, yrkesaktivitetstype, type)
        }
    }

    private fun valider(
        forventetYrkesaktivitetstype: Yrkesaktivitetstype,
        faktiskYrkesaktivitetstype: Yrkesaktivitetstype,
        type: Type,
    ) {
        if (faktiskYrkesaktivitetstype != forventetYrkesaktivitetstype) {
            error(
                "Nav-kjøpt forsikring er av type $type, " +
                    "der forventet yrkesaktivitetstype er $forventetYrkesaktivitetstype, " +
                    "men yrkesaktivitetstypen var $faktiskYrkesaktivitetstype"
            )
        }
    }

    private fun validerEnAv(
        faktiskeSpesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        vararg forventetEnAvSpesielleYrkesgrupper: SpesiellYrkesgruppe,
    ) {
        if (faktiskeSpesielleYrkesgrupper.none { it in forventetEnAvSpesielleYrkesgrupper }) {
            error(
                "Nav-kjøpt forsikring er av type $type, " +
                    "der det var forventet at spesielle yrkesgrupper inneholdt en av ${forventetEnAvSpesielleYrkesgrupper.toSet()}, " +
                    "men spesielle yrkesgrupper var $faktiskeSpesielleYrkesgrupper"
            )
        }
    }
}
