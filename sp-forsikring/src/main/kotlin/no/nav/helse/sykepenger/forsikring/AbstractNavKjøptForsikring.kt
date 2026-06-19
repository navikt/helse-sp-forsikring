package no.nav.helse.sykepenger.forsikring

import java.time.LocalDate
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagIfVedrift10Id


class RåNavKjøptForsikring(
    type: Type,
    virkningsdato: LocalDate,
    opphørsdato: LocalDate?,
    opphørsgrunn: String?,
    val erBetalt: Boolean
): AbstractNavKjøptForsikring (
    type = type,
    virkningsdato = virkningsdato,
    opphørsdato = opphørsdato,
    opphørsgrunn = opphørsgrunn,
)

class NavKjøptForsikring(
    val id: OppslagIfVedrift10Id,
    type: Type,
    virkningsdato: LocalDate,
    opphørsdato: LocalDate?,
    opphørsgrunn: String?,
    val erBetaltNoenGang: Boolean
): AbstractNavKjøptForsikring (
    type = type,
    virkningsdato = virkningsdato,
    opphørsdato = opphørsdato,
    opphørsgrunn = opphørsgrunn,
)

sealed class AbstractNavKjøptForsikring(
    val type: Type,
    val virkningsdato: LocalDate,
    val opphørsdato: LocalDate?,
    val opphørsgrunn: String?,
): Forsikring {
    enum class Type(val dekningGrad: Int, val dekningFraDag: Int) {
        SELVSTENDIG_80_PROSENT_FRA_DAG_1(80, 1),
        SELVSTENDIG_100_PROSENT_FRA_DAG_17(100, 17),
        SELVSTENDIG_100_PROSENT_FRA_DAG_1(100, 1),
        SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1(100, 1),
        FRILANSER_100_PROSENT_FRA_DAG_1(100, 1),
    }

    fun erInnen28DagerFørVirkningsdato(dato: LocalDate) =
        dato in virkningsdato.minusDays(28)..<virkningsdato

    fun harVirkningPå(dato: LocalDate) =
        virkningsdato <= dato

    fun erOpphørtPå(dato: LocalDate) =
        (opphørsdato != null && dato > opphørsdato) || (opphørsgrunn != null && opphørsdato == null)

    override fun dekningGrad(): Int = type.dekningGrad

    override fun dekningFraDag(): Int = type.dekningFraDag

    override fun opphørsdato(): LocalDate? = opphørsdato

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
        SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO,
        SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO,
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
