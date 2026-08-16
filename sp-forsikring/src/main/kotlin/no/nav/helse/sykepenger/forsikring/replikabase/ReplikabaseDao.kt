package no.nav.helse.sykepenger.forsikring.replikabase

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer

class ReplikabaseDao(
    private val replikabaseTransactionalSession: TransactionalSession,
) {
    fun hentAlleIfVedfrivt10(identitetsnummer: Identitetsnummer): List<InfotrygdIfVedfrivt10Dto> =
        replikabaseTransactionalSession.run(
            queryOf(
                // language=postgresql
                statement =
                    """
                    SELECT *
                    FROM IF_VEDFRIVT_10
                    WHERE IF01_KODE = '1' AND IF01_AGNR_FNR = :IF01_AGNR_FNR AND IF10_GODKJ = 'J'
                    """.trimIndent(),
                paramMap = mapOf("IF01_AGNR_FNR" to identitetsnummer.tilInfotrygdFødselsnummer()),
            ).map { row ->
                InfotrygdIfVedfrivt10Dto(
                    IF01_KODE = row.string("IF01_KODE").first(),
                    IF01_AGNR_FNR = row.long("IF01_AGNR_FNR"),
                    IF10_FORSFOM_SEQ = row.int("IF10_FORSFOM_SEQ"),
                    IF10_GODKJ = row.string("IF10_GODKJ").first(),
                    IF10_FORSFOM = row.int("IF10_FORSFOM"),
                    IF10_VIRKDATO = row.int("IF10_VIRKDATO"),
                    IF10_TYPE = row.string("IF10_TYPE").first(),
                    IF10_SELVFOM = row.string("IF10_SELVFOM"),
                    IF10_KOMBI = row.string("IF10_KOMBI").first(),
                    IF10_PREMGRL = row.int("IF10_PREMGRL"),
                    IF10_FOM = row.int("IF10_FOM"),
                    IF10_PREMIE = row.int("IF10_PREMIE"),
                    IF10_GML_PREMGRL = row.int("IF10_GML_PREMGRL"),
                    IF10_GML_FOM = row.int("IF10_GML_FOM"),
                    IF10_GML_PREMIE = row.int("IF10_GML_PREMIE"),
                    IF10_FRIFOM = row.int("IF10_FRIFOM"),
                    IF10_FORSTOM = row.int("IF10_FORSTOM"),
                    IF10_OPPHGR = row.string("IF10_OPPHGR"),
                    IF10_VARSEL = row.int("IF10_VARSEL"),
                    IF10_TERM_KV = row.string("IF10_TERM_KV").first(),
                    IF10_TERM_AAR = row.string("IF10_TERM_AAR"),
                    IF10_VARSEL_BELOEP = row.int("IF10_VARSEL_BELOEP"),
                    IF10_BETALT_BELOEP = row.int("IF10_BETALT_BELOEP"),
                    IF10_PURR = row.int("IF10_PURR"),
                    IF10_TKNR_BOST = row.int("IF10_TKNR_BOST"),
                    IF10_TKNR_BEH = row.int("IF10_TKNR_BEH"),
                    OPPRETTET = row.sqlTimestamp("OPPRETTET").toInstant(),
                    ENDRET_I_KILDE = row.sqlTimestamp("ENDRET_I_KILDE").toInstant(),
                    KILDE_IF = row.string("KILDE_IF"),
                    ID_VED = row.bigDecimal("ID_VED"),
                    OPPDATERT = row.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                )
            }.asList,
        )

    fun hentAlleIfFkonto12(identitetsnummer: Identitetsnummer): List<InfotrygdIfFkonto12Dto> =
        replikabaseTransactionalSession.run(
            queryOf(
                // language=postgresql
                statement =
                    """
                    SELECT *
                    FROM IF_FKONTO_12
                    WHERE IF01_KODE = '1' AND IF01_AGNR_FNR = :IF01_AGNR_FNR AND IF10_FORSFOM_SEQ IN (
                        SELECT IF10_FORSFOM_SEQ
                        FROM IF_VEDFRIVT_10
                        WHERE IF01_KODE = '1' AND IF01_AGNR_FNR = :IF01_AGNR_FNR AND IF10_GODKJ = 'J'
                    )
                    """.trimIndent(),
                paramMap = mapOf("IF01_AGNR_FNR" to identitetsnummer.tilInfotrygdFødselsnummer()),
            ).map { row ->
                InfotrygdIfFkonto12Dto(
                    IF01_KODE = row.string("IF01_KODE").first(),
                    IF01_AGNR_FNR = row.long("IF01_AGNR_FNR"),
                    IF10_FORSFOM_SEQ = row.int("IF10_FORSFOM_SEQ"),
                    IF12_BETDATO_SEQ = row.intOrNull("IF12_BETDATO_SEQ"),
                    IF12_FOM = row.intOrNull("IF12_FOM"),
                    IF12_TOM = row.intOrNull("IF12_TOM"),
                    IF12_BET_KODE = row.stringOrNull("IF12_BET_KODE")?.first(),
                    IF12_FRIUKER = row.stringOrNull("IF12_FRIUKER"),
                    IF12_BELOEP = row.bigDecimalOrNull("IF12_BELOEP"),
                    IF12_BETDATO = row.intOrNull("IF12_BETDATO"),
                    OPPRETTET = row.sqlTimestamp("OPPRETTET").toInstant(),
                    ENDRET_I_KILDE = row.sqlTimestamp("ENDRET_I_KILDE").toInstant(),
                    KILDE_IF = row.string("KILDE_IF"),
                    ID_KONT = row.bigDecimal("ID_KONT"),
                    OPPDATERT = row.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                )
            }.asList,
        )

    private fun Identitetsnummer.tilInfotrygdFødselsnummer(): String {
        val år = value.substring(4, 6)
        val måned = value.substring(2, 4)
        val dag = value.substring(0, 2)
        val id = value.substring(6)
        return "$år$måned$dag$id"
    }
}
