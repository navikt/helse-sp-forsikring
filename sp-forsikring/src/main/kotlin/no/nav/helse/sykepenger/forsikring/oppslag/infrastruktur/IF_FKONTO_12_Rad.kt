package no.nav.helse.sykepenger.forsikring.oppslag.adapter.oracle

import java.math.BigDecimal
import java.time.Instant

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
