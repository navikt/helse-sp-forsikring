package no.nav.helse.sykepenger.forsikring

import java.time.LocalDate

data class NavKjøptForsikring(
    val IF01_KODE: Char,
    val IF01_AGNR_FNR: Long,
    val IF10_FORSFOM_SEQ: Int,
    val type: Type,
    val virkningsdato: LocalDate,
    val opphørsdato: LocalDate?,
    val erBetaltNoenGang: Boolean,
): Forsikring {
    enum class Type(val dekningGrad: Int, val dekningFraDag: Int) {
        SELVSTENDIG_80_PROSENT_FRA_DAG_1(80, 1),
        SELVSTENDIG_100_PROSENT_FRA_DAG_17(100, 17),
        SELVSTENDIG_100_PROSENT_FRA_DAG_1(100, 1),
        SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1(100, 1),
        FRILANSER_100_PROSENT_FRA_DAG_1(100, 1),
    }

    fun harVirkningPå(dato: LocalDate) =
        virkningsdato > dato

    fun erOpphørtPå(dato: LocalDate) =
        opphørsdato != null && dato > opphørsdato

    override fun dekningGrad(): Int = type.dekningGrad

    override fun dekningFraDag(): Int = type.dekningFraDag

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

    enum class Ekskluderingsårsak {
        VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT,
        OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT,
        ALDRI_BETALT,
    }

    class Valideringsfeil(message: String) : Exception(message)

    private fun valider(
        forventetYrkesaktivitetstype: Yrkesaktivitetstype,
        faktiskYrkesaktivitetstype: Yrkesaktivitetstype,
        type: Type,
    ) {
        if (faktiskYrkesaktivitetstype != forventetYrkesaktivitetstype) {
            throw Valideringsfeil(
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
            throw Valideringsfeil(
                "Nav-kjøpt forsikring er av type $type, " +
                    "der det var forventet at spesielle yrkesgrupper inneholdt en av ${forventetEnAvSpesielleYrkesgrupper.toSet()}, " +
                    "men spesielle yrkesgrupper var $faktiskeSpesielleYrkesgrupper"
            )
        }
    }
}
