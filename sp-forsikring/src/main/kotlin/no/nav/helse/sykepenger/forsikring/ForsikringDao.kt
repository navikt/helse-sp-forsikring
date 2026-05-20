package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.annotation.JsonAutoDetect
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

interface ForsikringDao {
    fun hentForsikringer(fødselsnummer: String, skjæringstidspunkt: LocalDate): List<ForsikringDto>
    fun hentFullstendigeForsikringer(fødselsnummer: String): List<RåForsikringDto>

    data class ForsikringDto(
        val forsikringstype: Forsikringstype,
        val premiegrunnlag: Int,
        val virkningsdato: LocalDate,
        val tom: LocalDate?
    ) {
        enum class Forsikringstype {
            ÅttiProsentFraDagEn,
            HundreProsentFraDagEn,
            HundreProsentFraDagSytten,
            IkkeInteressert
        }

        internal fun erAktivPå(dato: LocalDate): Boolean =
            !(dato.isBefore(virkningsdato) || tom != null && dato.isAfter(tom))
    }

    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    data class RåForsikringDto(
        val IF01_KODE: Char,
        val IF01_AGNR_FNR: Long,
        val IF10_FORSFOM_SEQ: Int,
        val IF10_GODKJ: Char,
        val IF10_FORSFOM: Int,
        val IF10_VIRKDATO: Int,
        val IF10_TYPE: Char,
        val IF10_SELVFOM: String,
        val IF10_KOMBI: Char,
        val IF10_PREMGRL: Int,
        val IF10_FOM: Int,
        val IF10_PREMIE: Int,
        val IF10_GML_PREMGRL: Int,
        val IF10_GML_FOM: Int,
        val IF10_GML_PREMIE: Int,
        val IF10_FRIFOM: Int,
        val IF10_FORSTOM: Int,
        val IF10_OPPHGR: String,
        val IF10_VARSEL: Int,
        val IF10_TERM_KV: Char,
        val IF10_TERM_AAR: String,
        val IF10_VARSEL_BELOEP: Int,
        val IF10_BETALT_BELOEP: Int,
        val IF10_PURR: Int,
        val IF10_TKNR_BOST: Int,
        val IF10_TKNR_BEH: Int,
        val OPPRETTET: Instant,
        val ENDRET_I_KILDE: Instant,
        val KILDE_IF: String,
        val ID_VED: BigDecimal,
        val OPPDATERT: Instant?,
        val IF_FKONTO_12_rader: List<IF_FKONTO_12_Rad>,
    )

    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    data class IF_FKONTO_12_Rad(
        val IF12_BETDATO_SEQ: Int?,
        val IF12_FOM: Int?,
        val IF12_TOM: Int?,
        val IF12_BET_KODE: Char?,
        val IF12_FRIUKER: String?,
        val IF12_BELOEP: BigDecimal?,
        val IF12_BETDATO: Int?,
        val OPPRETTET: Instant,
        val ENDRET_I_KILDE: Instant,
        val KILDE_IF: String,
        val ID_KONT: BigDecimal,
        val OPPDATERT: Instant?
    )
}
