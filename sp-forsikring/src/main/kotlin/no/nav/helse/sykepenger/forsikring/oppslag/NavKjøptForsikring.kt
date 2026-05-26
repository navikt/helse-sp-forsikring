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

    sealed interface Valideringsresultat {
        data object OK : Valideringsresultat
        class UoverenstemmelseMedYrkesaktivitetstype(
            forventetYrkesaktivitetstype: Yrkesaktivitetstype,
            faktiskYrkesaktivitetstype: Yrkesaktivitetstype,
            type: Type,
        ) : Valideringsresultat {
            val melding =
                "Nav-kjøpt forsikring er av type ${type}, " +
                    "der forventet yrkesaktivitetstype er $forventetYrkesaktivitetstype, " +
                    "men yrkesaktivitetstypen var $faktiskYrkesaktivitetstype"
        }

        class UoverenstemmelseMedSpesielleYrkesgrupper(
            forventetEnAvSpesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            faktiskeSpesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            type: Type,
        ) : Valideringsresultat {
            val melding =
                "Nav-kjøpt forsikring er av type ${type}, " +
                    "der det var forventet at spesielle yrkesgrupper inneholdt en av $forventetEnAvSpesielleYrkesgrupper, " +
                    "men spesielle yrkesgrupper var $faktiskeSpesielleYrkesgrupper"
        }
    }

    fun validerType(yrkesaktivitetstype: Yrkesaktivitetstype, spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>): Valideringsresultat =
        when (type) {
            Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> {
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)
            }

            Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> {
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)
            }

            Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> {
                valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)
            }

            Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> {
                val førsteValidering = valider(Yrkesaktivitetstype.SELVSTENDIG, yrkesaktivitetstype, type)
                if (førsteValidering == Valideringsresultat.OK) {
                    validerEnAv(spesielleYrkesgrupper, SpesiellYrkesgruppe.Jordbruker, SpesiellYrkesgruppe.Reindrifter)
                } else {
                    førsteValidering
                }
            }

            Type.FRILANSER_100_PROSENT_FRA_DAG_1 -> {
                valider(Yrkesaktivitetstype.FRILANS, yrkesaktivitetstype, type)
            }
        }

    private fun valider(
        forventetYrkesaktivitetstype: Yrkesaktivitetstype,
        faktiskYrkesaktivitetstype: Yrkesaktivitetstype,
        type: Type,
    ): Valideringsresultat =
        if (faktiskYrkesaktivitetstype != forventetYrkesaktivitetstype) {
            Valideringsresultat.UoverenstemmelseMedYrkesaktivitetstype(
                forventetYrkesaktivitetstype,
                faktiskYrkesaktivitetstype,
                type
            )
        } else {
            Valideringsresultat.OK
        }

    private fun validerEnAv(
        faktiskeSpesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        vararg forventetEnAvSpesielleYrkesgrupper: SpesiellYrkesgruppe
    ): Valideringsresultat =
        if (faktiskeSpesielleYrkesgrupper.none { it in forventetEnAvSpesielleYrkesgrupper }) {
            Valideringsresultat.UoverenstemmelseMedSpesielleYrkesgrupper(
                forventetEnAvSpesielleYrkesgrupper.toSet(),
                faktiskeSpesielleYrkesgrupper,
                type
            )
        } else {
            Valideringsresultat.OK
        }
}
