package no.nav.helse.sykepenger.forsikring.replikabase

import javax.sql.DataSource
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class ReplikabaseDao(private val dataSource: DataSource) {
    fun hentIfVedfrivt10Rader(fødselsnummer: String): List<TiOgTolv> =
        sessionOf(dataSource).use { session ->
            session.queryList(
                sql = """
                    SELECT *
                    FROM IF_VEDFRIVT_10
                    WHERE IF01_KODE = '1' AND IF01_AGNR_FNR = :IF01_AGNR_FNR
                    ORDER BY IF10_FORSFOM_SEQ
                """,
                parameterMap = mapOf(
                    "IF01_AGNR_FNR" to fødselsnummer.tilInfotrygdFødselsnummer()
                )
            ) { row ->
                IF_VEDFRIVT_10_Rad(
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
            }.map { ifVedfrivt10Rad ->
                TiOgTolv(
                    ifVedfrivt10Rad = ifVedfrivt10Rad,
                    ifFkonto12Rader = session.queryList(
                        sql = """
                        SELECT *
                        FROM IF_FKONTO_12
                        WHERE IF01_KODE = :IF01_KODE
                         AND IF01_AGNR_FNR = :IF01_AGNR_FNR
                         AND IF10_FORSFOM_SEQ = :IF10_FORSFOM_SEQ
                        ORDER BY IF12_BETDATO_SEQ
                    """.trimIndent(),
                        parameterMap = mapOf(
                            "IF01_KODE" to ifVedfrivt10Rad.IF01_KODE.toString(),
                            "IF01_AGNR_FNR" to ifVedfrivt10Rad.IF01_AGNR_FNR,
                            "IF10_FORSFOM_SEQ" to ifVedfrivt10Rad.IF10_FORSFOM_SEQ,
                        )
                    ) { row: Row ->
                        IF_FKONTO_12_Rad(
                            IF01_KODE = row.stringOrNull("IF01_KODE")?.first(),
                            IF01_AGNR_FNR = row.longOrNull("IF01_AGNR_FNR"),
                            IF10_FORSFOM_SEQ = row.intOrNull("IF10_FORSFOM_SEQ"),
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
                    }
                )
            }
        }

    private fun <T> Session.queryList(
        @Language("Oracle") sql: String,
        parameterMap: Map<String, Any>,
        extractor: (Row) -> T
    ): List<T> = run(queryOf(sql, parameterMap).map(extractor).asList)

    private fun String.tilInfotrygdFødselsnummer(): String {
        val år = substring(4, 6)
        val måned = substring(2, 4)
        val dag = substring(0, 2)
        val id = substring(6)
        return "$år$måned$dag$id"
    }
}
