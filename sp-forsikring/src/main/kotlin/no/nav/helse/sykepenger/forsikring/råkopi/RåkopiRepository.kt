package no.nav.helse.sykepenger.forsikring.råkopi

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import org.intellij.lang.annotations.Language
import java.sql.Timestamp

class RåkopiRepository(
    private val spForsikringTransaction: TransactionalSession,
) {
    fun lagre(råkopi: Råkopi) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO råkopi (id, lest_tidspunkt)
            VALUES (:id, :lest_tidspunkt)
        """
        spForsikringTransaction.run(
            queryOf(
                statement,
                mapOf(
                    "id" to råkopi.id.value,
                    "lest_tidspunkt" to Timestamp.from(råkopi.lestTidspunkt),
                ),
            ).asUpdate,
        )
        råkopi.ifVedfrivt10er.forEach { ifVedfrivt10 ->
            lagreIfVedfrivt10Rad(
                råkopiId = råkopi.id,
                ifVedfrivt10 = ifVedfrivt10,
                session = spForsikringTransaction,
            )
        }
        råkopi.ifFkonto12er.forEach { ifFkonto12 ->
            val ifVedfrivt10 =
                råkopi.ifVedfrivt10er.singleOrNull { kandidat ->
                    kandidat.IF01_KODE == ifFkonto12.IF01_KODE &&
                        kandidat.IF01_AGNR_FNR == ifFkonto12.IF01_AGNR_FNR &&
                        kandidat.IF10_FORSFOM_SEQ == ifFkonto12.IF10_FORSFOM_SEQ
                }
                    ?: error(
                        "Fant ikke nøyaktig én IF_VEDFRIVT_10-rad for IF_FKONTO_12-raden med" +
                            " IF10_FORSFOM_SEQ=${ifFkonto12.IF10_FORSFOM_SEQ} i råkopien",
                    )
            lagreIfFkonto12Rad(
                ifVedfrivt10Id = ifVedfrivt10.id,
                ifFkonto12 = ifFkonto12,
                session = spForsikringTransaction,
            )
        }
        loggInfo("Lagret råkopi med ID ${råkopi.id.value}")
    }

    fun hent(id: Råkopi.Id): Råkopi? {
        @Language("PostgreSQL")
        val statement = """
            SELECT lest_tidspunkt
            FROM råkopi
            WHERE id = :id
        """
        val lestTidspunkt =
            spForsikringTransaction.run(
                queryOf(statement, mapOf("id" to id.value))
                    .map { row -> row.instant("lest_tidspunkt") }
                    .asSingle,
            ) ?: return null

        return Råkopi.fraLagring(
            id = id,
            lestTidspunkt = lestTidspunkt,
            ifVedfrivt10er = hentIfVedfrivt10er(id),
            ifFKonto12er = hentIfFkonto12er(id),
        )
    }

    private fun hentIfVedfrivt10er(råkopiId: Råkopi.Id): List<RåkopiIfVedfrivt10> {
        @Language("PostgreSQL")
        val statement = """
            SELECT *
            FROM råkopi_IF_VEDFRIVT_10
            WHERE råkopi_id = :rakopi_id
        """
        return spForsikringTransaction.run(
            queryOf(statement, mapOf("rakopi_id" to råkopiId.value))
                .map { row ->
                    RåkopiIfVedfrivt10(
                        id = RåkopiIfVedfrivt10.Id(row.uuid("id")),
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
                        OPPRETTET = row.instant("OPPRETTET"),
                        ENDRET_I_KILDE = row.instant("ENDRET_I_KILDE"),
                        KILDE_IF = row.string("KILDE_IF"),
                        ID_VED = row.bigDecimal("ID_VED"),
                        OPPDATERT = row.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                    )
                }.asList,
        )
    }

    private fun hentIfFkonto12er(råkopiId: Råkopi.Id): List<RåkopiIfFkonto12> {
        @Language("PostgreSQL")
        val statement = """
            SELECT f.*
            FROM råkopi_IF_FKONTO_12 f
                     JOIN råkopi_IF_VEDFRIVT_10 v ON v.id = f.råkopi_IF_VEDFRIVT_10_id
            WHERE v.råkopi_id = :rakopi_id
        """
        return spForsikringTransaction.run(
            queryOf(statement, mapOf("rakopi_id" to råkopiId.value))
                .map { row ->
                    RåkopiIfFkonto12(
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
                        OPPRETTET = row.instant("OPPRETTET"),
                        ENDRET_I_KILDE = row.instant("ENDRET_I_KILDE"),
                        KILDE_IF = row.string("KILDE_IF"),
                        ID_KONT = row.bigDecimal("ID_KONT"),
                        OPPDATERT = row.sqlTimestampOrNull("OPPDATERT")?.toInstant(),
                    )
                }.asList,
        )
    }

    private fun lagreIfVedfrivt10Rad(
        råkopiId: Råkopi.Id,
        ifVedfrivt10: RåkopiIfVedfrivt10,
        session: TransactionalSession,
    ) {
        @Language("PostgreSQL")
        val statement = """
                INSERT INTO råkopi_IF_VEDFRIVT_10 (
                    id, råkopi_id,
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                    IF10_GODKJ, IF10_FORSFOM, IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM,
                    IF10_KOMBI, IF10_PREMGRL, IF10_FOM, IF10_PREMIE,
                    IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                    IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL,
                    IF10_TERM_KV, IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP,
                    IF10_PURR, IF10_TKNR_BOST, IF10_TKNR_BEH,
                    OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_VED, OPPDATERT
                ) VALUES (
                    :id, :rakopi_id,
                    :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ,
                    :IF10_GODKJ, :IF10_FORSFOM, :IF10_VIRKDATO, :IF10_TYPE, :IF10_SELVFOM,
                    :IF10_KOMBI, :IF10_PREMGRL, :IF10_FOM, :IF10_PREMIE,
                    :IF10_GML_PREMGRL, :IF10_GML_FOM, :IF10_GML_PREMIE,
                    :IF10_FRIFOM, :IF10_FORSTOM, :IF10_OPPHGR, :IF10_VARSEL,
                    :IF10_TERM_KV, :IF10_TERM_AAR, :IF10_VARSEL_BELOEP, :IF10_BETALT_BELOEP,
                    :IF10_PURR, :IF10_TKNR_BOST, :IF10_TKNR_BEH,
                    :OPPRETTET, :ENDRET_I_KILDE, :KILDE_IF, :ID_VED, :OPPDATERT
                )
            """
        session.run(
            queryOf(
                statement,
                mapOf(
                    "id" to ifVedfrivt10.id.value,
                    "rakopi_id" to råkopiId.value,
                    "IF01_KODE" to ifVedfrivt10.IF01_KODE.toString(),
                    "IF01_AGNR_FNR" to ifVedfrivt10.IF01_AGNR_FNR,
                    "IF10_FORSFOM_SEQ" to ifVedfrivt10.IF10_FORSFOM_SEQ,
                    "IF10_GODKJ" to ifVedfrivt10.IF10_GODKJ.toString(),
                    "IF10_FORSFOM" to ifVedfrivt10.IF10_FORSFOM,
                    "IF10_VIRKDATO" to ifVedfrivt10.IF10_VIRKDATO,
                    "IF10_TYPE" to ifVedfrivt10.IF10_TYPE.toString(),
                    "IF10_SELVFOM" to ifVedfrivt10.IF10_SELVFOM,
                    "IF10_KOMBI" to ifVedfrivt10.IF10_KOMBI.toString(),
                    "IF10_PREMGRL" to ifVedfrivt10.IF10_PREMGRL,
                    "IF10_FOM" to ifVedfrivt10.IF10_FOM,
                    "IF10_PREMIE" to ifVedfrivt10.IF10_PREMIE,
                    "IF10_GML_PREMGRL" to ifVedfrivt10.IF10_GML_PREMGRL,
                    "IF10_GML_FOM" to ifVedfrivt10.IF10_GML_FOM,
                    "IF10_GML_PREMIE" to ifVedfrivt10.IF10_GML_PREMIE,
                    "IF10_FRIFOM" to ifVedfrivt10.IF10_FRIFOM,
                    "IF10_FORSTOM" to ifVedfrivt10.IF10_FORSTOM,
                    "IF10_OPPHGR" to ifVedfrivt10.IF10_OPPHGR,
                    "IF10_VARSEL" to ifVedfrivt10.IF10_VARSEL,
                    "IF10_TERM_KV" to ifVedfrivt10.IF10_TERM_KV.toString(),
                    "IF10_TERM_AAR" to ifVedfrivt10.IF10_TERM_AAR,
                    "IF10_VARSEL_BELOEP" to ifVedfrivt10.IF10_VARSEL_BELOEP,
                    "IF10_BETALT_BELOEP" to ifVedfrivt10.IF10_BETALT_BELOEP,
                    "IF10_PURR" to ifVedfrivt10.IF10_PURR,
                    "IF10_TKNR_BOST" to ifVedfrivt10.IF10_TKNR_BOST,
                    "IF10_TKNR_BEH" to ifVedfrivt10.IF10_TKNR_BEH,
                    "OPPRETTET" to Timestamp.from(ifVedfrivt10.OPPRETTET),
                    "ENDRET_I_KILDE" to Timestamp.from(ifVedfrivt10.ENDRET_I_KILDE),
                    "KILDE_IF" to ifVedfrivt10.KILDE_IF,
                    "ID_VED" to ifVedfrivt10.ID_VED,
                    "OPPDATERT" to ifVedfrivt10.OPPDATERT?.let { Timestamp.from(it) },
                ),
            ).asUpdate,
        )
    }

    private fun lagreIfFkonto12Rad(
        ifVedfrivt10Id: RåkopiIfVedfrivt10.Id,
        ifFkonto12: RåkopiIfFkonto12,
        session: TransactionalSession,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO råkopi_IF_FKONTO_12 (
                id, råkopi_IF_VEDFRIVT_10_id,
                IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                IF12_BETDATO_SEQ, IF12_FOM, IF12_TOM, IF12_BET_KODE, IF12_FRIUKER,
                IF12_BELOEP, IF12_BETDATO,
                OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT, OPPDATERT
            ) VALUES (
                :id, :rakopi_IF_VEDFRIVT_10_id,
                :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ,
                :IF12_BETDATO_SEQ, :IF12_FOM, :IF12_TOM, :IF12_BET_KODE, :IF12_FRIUKER,
                :IF12_BELOEP, :IF12_BETDATO,
                :OPPRETTET, :ENDRET_I_KILDE, :KILDE_IF, :ID_KONT, :OPPDATERT
            )
        """
        session.run(
            queryOf(
                statement,
                mapOf(
                    "id" to generateUuidV7(),
                    "rakopi_IF_VEDFRIVT_10_id" to ifVedfrivt10Id.value,
                    "IF01_KODE" to ifFkonto12.IF01_KODE.toString(),
                    "IF01_AGNR_FNR" to ifFkonto12.IF01_AGNR_FNR,
                    "IF10_FORSFOM_SEQ" to ifFkonto12.IF10_FORSFOM_SEQ,
                    "IF12_BETDATO_SEQ" to ifFkonto12.IF12_BETDATO_SEQ,
                    "IF12_FOM" to ifFkonto12.IF12_FOM,
                    "IF12_TOM" to ifFkonto12.IF12_TOM,
                    "IF12_BET_KODE" to ifFkonto12.IF12_BET_KODE?.toString(),
                    "IF12_FRIUKER" to ifFkonto12.IF12_FRIUKER,
                    "IF12_BELOEP" to ifFkonto12.IF12_BELOEP,
                    "IF12_BETDATO" to ifFkonto12.IF12_BETDATO,
                    "OPPRETTET" to Timestamp.from(ifFkonto12.OPPRETTET),
                    "ENDRET_I_KILDE" to Timestamp.from(ifFkonto12.ENDRET_I_KILDE),
                    "KILDE_IF" to ifFkonto12.KILDE_IF,
                    "ID_KONT" to ifFkonto12.ID_KONT,
                    "OPPDATERT" to ifFkonto12.OPPDATERT?.let { Timestamp.from(it) },
                ),
            ).asUpdate,
        )
    }
}
