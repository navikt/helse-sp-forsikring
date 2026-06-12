package no.nav.helse.sykepenger.forsikring.oppslag

import java.sql.Timestamp
import java.time.Instant
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.replikabase.IF_FKONTO_12_Rad
import no.nav.helse.sykepenger.forsikring.replikabase.IF_VEDFRIVT_10_Rad
import no.nav.helse.sykepenger.forsikring.toLocalDate
import org.intellij.lang.annotations.Language

class OppslagDao(private val transaction: TransactionalSession) {
    fun lagreOppslag(oppslagId: OppslagId, oppslagTidspunkt: Instant) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag (id, oppslag_tidspunkt)
            VALUES (:id, :oppslag_tidspunkt)
        """
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "id" to oppslagId.value,
                    "oppslag_tidspunkt" to Timestamp.from(oppslagTidspunkt),
                )
            ).asUpdate
        )
    }

    fun lagreIfVedfrivt10Rader(
        oppslagId: OppslagId,
        rader: List<IF_VEDFRIVT_10_Rad>,
    ) {
        rader.forEach { rad ->
            val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
            lagreIfVedfrivt10Rad(oppslagIfVedrift10Id, oppslagId, rad)
            rad.IF_FKONTO_12_rader.forEach { fkontoRad ->
                lagreIfFkonto12Rad(oppslagIfVedrift10Id, rad, fkontoRad)
            }
        }
    }

    private fun lagreIfVedfrivt10Rad(
        id: OppslagIfVedrift10Id,
        oppslagId: OppslagId,
        rad: IF_VEDFRIVT_10_Rad
    ) {
        @Language("PostgreSQL")
        val statement = """
                INSERT INTO oppslag_IF_VEDFRIVT_10 (
                    id, oppslag_id,
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                    IF10_GODKJ, IF10_FORSFOM, IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM,
                    IF10_KOMBI, IF10_PREMGRL, IF10_FOM, IF10_PREMIE,
                    IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                    IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL,
                    IF10_TERM_KV, IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP,
                    IF10_PURR, IF10_TKNR_BOST, IF10_TKNR_BEH,
                    OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_VED, OPPDATERT
                ) VALUES (
                    :id, :oppslag_id,
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
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "id" to id.value,
                    "oppslag_id" to oppslagId.value,
                    "IF01_KODE" to rad.IF01_KODE.toString(),
                    "IF01_AGNR_FNR" to rad.IF01_AGNR_FNR,
                    "IF10_FORSFOM_SEQ" to rad.IF10_FORSFOM_SEQ,
                    "IF10_GODKJ" to rad.IF10_GODKJ.toString(),
                    "IF10_FORSFOM" to rad.IF10_FORSFOM,
                    "IF10_VIRKDATO" to rad.IF10_VIRKDATO,
                    "IF10_TYPE" to rad.IF10_TYPE.toString(),
                    "IF10_SELVFOM" to rad.IF10_SELVFOM,
                    "IF10_KOMBI" to rad.IF10_KOMBI.toString(),
                    "IF10_PREMGRL" to rad.IF10_PREMGRL,
                    "IF10_FOM" to rad.IF10_FOM,
                    "IF10_PREMIE" to rad.IF10_PREMIE,
                    "IF10_GML_PREMGRL" to rad.IF10_GML_PREMGRL,
                    "IF10_GML_FOM" to rad.IF10_GML_FOM,
                    "IF10_GML_PREMIE" to rad.IF10_GML_PREMIE,
                    "IF10_FRIFOM" to rad.IF10_FRIFOM,
                    "IF10_FORSTOM" to rad.IF10_FORSTOM,
                    "IF10_OPPHGR" to rad.IF10_OPPHGR,
                    "IF10_VARSEL" to rad.IF10_VARSEL,
                    "IF10_TERM_KV" to rad.IF10_TERM_KV.toString(),
                    "IF10_TERM_AAR" to rad.IF10_TERM_AAR,
                    "IF10_VARSEL_BELOEP" to rad.IF10_VARSEL_BELOEP,
                    "IF10_BETALT_BELOEP" to rad.IF10_BETALT_BELOEP,
                    "IF10_PURR" to rad.IF10_PURR,
                    "IF10_TKNR_BOST" to rad.IF10_TKNR_BOST,
                    "IF10_TKNR_BEH" to rad.IF10_TKNR_BEH,
                    "OPPRETTET" to Timestamp.from(rad.OPPRETTET),
                    "ENDRET_I_KILDE" to Timestamp.from(rad.ENDRET_I_KILDE),
                    "KILDE_IF" to rad.KILDE_IF,
                    "ID_VED" to rad.ID_VED,
                    "OPPDATERT" to rad.OPPDATERT?.let { Timestamp.from(it) },
                )
            ).asUpdate
        )
    }

    private fun lagreIfFkonto12Rad(
        oppslagIfVedrift10Id: OppslagIfVedrift10Id,
        vedfrivtRad: IF_VEDFRIVT_10_Rad,
        rad: IF_FKONTO_12_Rad,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag_IF_FKONTO_12 (
                id, oppslag_IF_VEDFRIVT_10_id,
                IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                IF12_BETDATO_SEQ, IF12_FOM, IF12_TOM, IF12_BET_KODE, IF12_FRIUKER,
                IF12_BELOEP, IF12_BETDATO,
                OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT, OPPDATERT
            ) VALUES (
                :id, :oppslag_IF_VEDFRIVT_10_id,
                :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ,
                :IF12_BETDATO_SEQ, :IF12_FOM, :IF12_TOM, :IF12_BET_KODE, :IF12_FRIUKER,
                :IF12_BELOEP, :IF12_BETDATO,
                :OPPRETTET, :ENDRET_I_KILDE, :KILDE_IF, :ID_KONT, :OPPDATERT
            )
        """
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "id" to OppslagIfFkonto12Id.ny().value,
                    "oppslag_IF_VEDFRIVT_10_id" to oppslagIfVedrift10Id.value,
                    "IF01_KODE" to vedfrivtRad.IF01_KODE.toString(),
                    "IF01_AGNR_FNR" to vedfrivtRad.IF01_AGNR_FNR,
                    "IF10_FORSFOM_SEQ" to vedfrivtRad.IF10_FORSFOM_SEQ,
                    "IF12_BETDATO_SEQ" to rad.IF12_BETDATO_SEQ,
                    "IF12_FOM" to rad.IF12_FOM,
                    "IF12_TOM" to rad.IF12_TOM,
                    "IF12_BET_KODE" to rad.IF12_BET_KODE?.toString(),
                    "IF12_FRIUKER" to rad.IF12_FRIUKER,
                    "IF12_BELOEP" to rad.IF12_BELOEP,
                    "IF12_BETDATO" to rad.IF12_BETDATO,
                    "OPPRETTET" to Timestamp.from(rad.OPPRETTET),
                    "ENDRET_I_KILDE" to Timestamp.from(rad.ENDRET_I_KILDE),
                    "KILDE_IF" to rad.KILDE_IF,
                    "ID_KONT" to rad.ID_KONT,
                    "OPPDATERT" to rad.OPPDATERT?.let { Timestamp.from(it) },
                )
            ).asUpdate
        )
    }

    fun hentOppslag(oppslagId: OppslagId): Oppslag {
        @Language("PostgreSQL")
        val statement = """
            SELECT
                v.id,
                v.IF10_TYPE,
                v.IF10_VIRKDATO,
                v.IF10_FORSTOM,
                v.IF10_OPPHGR,
                EXISTS (
                    SELECT 1
                    FROM oppslag_IF_FKONTO_12 f
                    WHERE f.oppslag_IF_VEDFRIVT_10_id = v.id
                      AND f.IF12_BETDATO IS NOT NULL
                      AND f.IF12_BETDATO != 0
                ) AS er_betalt_noen_gang
            FROM oppslag_IF_VEDFRIVT_10 v
            WHERE v.oppslag_id = :oppslag_id
        """
        val navKjøpteForsikringer = transaction.run(
            queryOf(statement, mapOf("oppslag_id" to oppslagId.value))
                .map { row ->
                    NavKjøptForsikring(
                        id = OppslagIfVedrift10Id(row.uuid("id")),
                        type = when (val type = row.string("IF10_TYPE")) {
                            "1" -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1
                            "2" -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17
                            "3" -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1
                            "4" -> AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1
                            "5" -> AbstractNavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1
                            else -> error("Ukjent forsikringstype: $type")
                        },
                        virkningsdato = row.intToLocalDate("IF10_VIRKDATO")!!,
                        opphørsdato = row.intToLocalDate("IF10_FORSTOM"),
                        opphørsgrunn = row.string("IF10_OPPHGR").takeIf { it.isNotBlank() },
                        erBetaltNoenGang = true //row.boolean("er_betalt_noen_gang"),
                    )
                }
                .asList
        )
        return Oppslag(id = oppslagId, navKjøpteForsikringer = navKjøpteForsikringer)
    }

    private fun Row.intToLocalDate(label: String) = int(label).toLocalDate()

}
