package no.nav.helse.sykepenger.forsikring

import kotliquery.Session
import kotliquery.queryOf
import org.intellij.lang.annotations.Language
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import no.nav.helse.sykepenger.forsikring.replikabase.IF_FKONTO_12_Rad
import no.nav.helse.sykepenger.forsikring.replikabase.IF_VEDFRIVT_10_Rad

class OppslagDao(private val session: Session) {
    fun lagreOppslag(oppslagId: UUID, opprinneligBehov: String, oppslagTidspunkt: Instant) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag (id, opprinnelig_behov, oppslag_tidspunkt)
            VALUES (:id, :opprinnelig_behov::jsonb, :oppslag_tidspunkt)
        """
        session.run(
            queryOf(
                statement,
                mapOf(
                    "id" to oppslagId,
                    "opprinnelig_behov" to opprinneligBehov,
                    "oppslag_tidspunkt" to Timestamp.from(oppslagTidspunkt),
                )
            ).asUpdate
        )
    }

    fun lagreIfVedfrivt10Rader(
        oppslagId: UUID,
        rader: List<IF_VEDFRIVT_10_Rad>,
    ) {
        rader.forEach { rad ->
            lagreIfVedfrivt10Rad(oppslagId, rad)
            rad.IF_FKONTO_12_rader.forEach { fkontoRad ->
                lagreIfFkonto12Rad(oppslagId, rad, fkontoRad)
            }
        }
    }

    private fun lagreIfVedfrivt10Rad(
        oppslagId: UUID,
        rad: IF_VEDFRIVT_10_Rad,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag_IF_VEDFRIVT_10 (
                oppslag_id,
                IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                IF10_GODKJ, IF10_FORSFOM, IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM,
                IF10_KOMBI, IF10_PREMGRL, IF10_FOM, IF10_PREMIE,
                IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL,
                IF10_TERM_KV, IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP,
                IF10_PURR, IF10_TKNR_BOST, IF10_TKNR_BEH,
                OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_VED, OPPDATERT
            ) VALUES (
                :oppslag_id,
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
                    "oppslag_id" to oppslagId,
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
        oppslagId: UUID,
        vedfrivtRad: IF_VEDFRIVT_10_Rad,
        rad: IF_FKONTO_12_Rad,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag_IF_FKONTO_12 (
                oppslag_id,
                IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                IF12_BETDATO_SEQ, IF12_FOM, IF12_TOM, IF12_BET_KODE, IF12_FRIUKER,
                IF12_BELOEP, IF12_BETDATO,
                OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT, OPPDATERT
            ) VALUES (
                :oppslag_id,
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
                    "oppslag_id" to oppslagId,
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
}
