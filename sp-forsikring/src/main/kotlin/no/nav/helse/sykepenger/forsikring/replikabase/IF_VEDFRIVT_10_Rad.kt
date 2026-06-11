package no.nav.helse.sykepenger.forsikring.replikabase

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.RåNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.toLocalDate

data class IF_VEDFRIVT_10_Rad(
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

fun IF_VEDFRIVT_10_Rad.mapTilRåNavKjøptForsikring(skjæringstidspunkt: LocalDate) = RåNavKjøptForsikring(
    type = when (val type = this.IF10_TYPE) {
        '1' -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1
        '2' -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17
        '3' -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1
        '4' -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1
        '5' -> AbstractNavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1
        else -> error("Ukjent forsikringstype: $type")
    },
    virkningsdato = IF10_VIRKDATO.toLocalDate()!!,
    opphørsdato = IF10_FORSTOM.toLocalDate(),
    opphørsgrunn = IF10_OPPHGR.takeIf { it.isNotBlank() },
    erBetalt = this.IF_FKONTO_12_rader.any { konto ->
        konto.IF12_FOM?.toLocalDate()?.isBefore(skjæringstidspunkt) ?: false
            && konto.IF12_TOM?.toLocalDate()?.isAfter(skjæringstidspunkt) ?: false
            && (konto.IF12_BETDATO ?: 0) > 0
    }
)

