package no.nav.helse.sykepenger.forsikring.råkopi

import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RåkopiIfFkonto12(
    val IF01_KODE: Char,
    val IF01_AGNR_FNR: Long,
    val IF10_FORSFOM_SEQ: Int,
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
    val OPPDATERT: Instant?,
) {
    @JvmInline
    value class Id(
        val value: UUID,
    ) {
        companion object {
            fun ny() = Id(generateUuidV7())
        }
    }
}
