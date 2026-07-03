package no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur

import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class ReplikabaseDao(private val dataSource: DataSource) {
    fun hentIfVedfrivt10Rader(fødselsnummer: String): List<IF_VEDFRIVT_10_Rad> =
        sessionOf(dataSource).use { session ->
            @Language("Oracle")
            val statement = """
                SELECT *
                FROM IF_VEDFRIVT_10
                WHERE IF01_KODE = '1' AND IF01_AGNR_FNR = ? AND IF10_GODKJ = 'J'
                ORDER BY IF10_FORSFOM_SEQ
            """
            session.run(
                queryOf(statement, fødselsnummer.tilInfotrygdFødselsnummer()).map { rs ->
                    val if01Kode = rs.string("IF01_KODE").first()
                    val if01AgnrFnr = rs.long("IF01_AGNR_FNR")
                    val if10ForsfomSeq = rs.int("IF10_FORSFOM_SEQ")

                    @Language("Oracle")
                    val fkonto12Statement = """
                        SELECT *
                        FROM IF_FKONTO_12
                        WHERE IF01_KODE = ? AND IF01_AGNR_FNR = ? AND IF10_FORSFOM_SEQ = ?
                        ORDER BY IF12_BETDATO_SEQ
                    """
                    val fkonto12Rader = sessionOf(dataSource).use { innerSession ->
                        innerSession.run(
                            queryOf(fkonto12Statement, if01Kode.toString(), if01AgnrFnr, if10ForsfomSeq).map { fkRs ->
                                IF_FKONTO_12_Rad(
                                    IF12_BETDATO_SEQ = fkRs.intOrNull("IF12_BETDATO_SEQ"),
                                    IF12_FOM = fkRs.intOrNull("IF12_FOM"),
                                    IF12_TOM = fkRs.intOrNull("IF12_TOM"),
                                    IF12_BET_KODE = fkRs.stringOrNull("IF12_BET_KODE")?.first(),
                                    IF12_FRIUKER = fkRs.stringOrNull("IF12_FRIUKER"),
                                    IF12_BELOEP = fkRs.bigDecimalOrNull("IF12_BELOEP"),
                                    IF12_BETDATO = fkRs.intOrNull("IF12_BETDATO"),
                                    OPPRETTET = fkRs.sqlTimestamp("OPPRETTET").toInstant(),
                                    ENDRET_I_KILDE = fkRs.sqlTimestamp("ENDRET_I_KILDE").toInstant(),
                                    KILDE_IF = fkRs.string("KILDE_IF"),
                                    ID_KONT = fkRs.bigDecimal("ID_KONT"),
                                    OPPDATERT = fkRs.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                                )
                            }.asList
                        )
                    }

                    IF_VEDFRIVT_10_Rad(
                        IF01_KODE = if01Kode,
                        IF01_AGNR_FNR = if01AgnrFnr,
                        IF10_FORSFOM_SEQ = if10ForsfomSeq,
                        IF10_GODKJ = rs.string("IF10_GODKJ").first(),
                        IF10_FORSFOM = rs.int("IF10_FORSFOM"),
                        IF10_VIRKDATO = rs.int("IF10_VIRKDATO"),
                        IF10_TYPE = rs.string("IF10_TYPE").first(),
                        IF10_SELVFOM = rs.string("IF10_SELVFOM"),
                        IF10_KOMBI = rs.string("IF10_KOMBI").first(),
                        IF10_PREMGRL = rs.int("IF10_PREMGRL"),
                        IF10_FOM = rs.int("IF10_FOM"),
                        IF10_PREMIE = rs.int("IF10_PREMIE"),
                        IF10_GML_PREMGRL = rs.int("IF10_GML_PREMGRL"),
                        IF10_GML_FOM = rs.int("IF10_GML_FOM"),
                        IF10_GML_PREMIE = rs.int("IF10_GML_PREMIE"),
                        IF10_FRIFOM = rs.int("IF10_FRIFOM"),
                        IF10_FORSTOM = rs.int("IF10_FORSTOM"),
                        IF10_OPPHGR = rs.string("IF10_OPPHGR"),
                        IF10_VARSEL = rs.int("IF10_VARSEL"),
                        IF10_TERM_KV = rs.string("IF10_TERM_KV").first(),
                        IF10_TERM_AAR = rs.string("IF10_TERM_AAR"),
                        IF10_VARSEL_BELOEP = rs.int("IF10_VARSEL_BELOEP"),
                        IF10_BETALT_BELOEP = rs.int("IF10_BETALT_BELOEP"),
                        IF10_PURR = rs.int("IF10_PURR"),
                        IF10_TKNR_BOST = rs.int("IF10_TKNR_BOST"),
                        IF10_TKNR_BEH = rs.int("IF10_TKNR_BEH"),
                        OPPRETTET = rs.sqlTimestamp("OPPRETTET").toInstant(),
                        ENDRET_I_KILDE = rs.sqlTimestamp("ENDRET_I_KILDE").toInstant(),
                        KILDE_IF = rs.string("KILDE_IF"),
                        ID_VED = rs.bigDecimal("ID_VED"),
                        OPPDATERT = rs.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                        IF_FKONTO_12_rader = fkonto12Rader,
                    )
                }.asList
            )
        }

    private fun String.tilInfotrygdFødselsnummer(): String {
        val år = substring(4, 6)
        val måned = substring(2, 4)
        val dag = substring(0, 2)
        val id = substring(6)
        return "$år$måned$dag$id"
    }
}
