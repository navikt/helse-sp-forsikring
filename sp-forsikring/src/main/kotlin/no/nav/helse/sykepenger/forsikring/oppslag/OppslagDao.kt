package no.nav.helse.sykepenger.forsikring.oppslag

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.replikabase.IF_FKONTO_12_Rad
import no.nav.helse.sykepenger.forsikring.replikabase.IF_VEDFRIVT_10_Rad
import org.intellij.lang.annotations.Language

class OppslagDao(private val transaction: TransactionalSession) {
    fun lagreOppslag(oppslagId: UUID, opprinneligBehov: String, oppslagTidspunkt: Instant) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag (id, opprinnelig_behov, oppslag_tidspunkt)
            VALUES (:id, :opprinnelig_behov::jsonb, :oppslag_tidspunkt)
        """
        transaction.run(
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
        transaction.run(
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
        transaction.run(
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

    fun hentOppslag(oppslagId: UUID): Oppslag {
        @Language("PostgreSQL")
        val statement = """
            SELECT
                v.IF01_KODE,
                v.IF01_AGNR_FNR,
                v.IF10_FORSFOM_SEQ,
                v.IF10_TYPE,
                v.IF10_VIRKDATO,
                v.IF10_FORSTOM,
                EXISTS (
                    SELECT 1
                    FROM oppslag_IF_FKONTO_12 f
                    WHERE f.oppslag_id = v.oppslag_id
                      AND f.IF01_KODE = v.IF01_KODE
                      AND f.IF01_AGNR_FNR = v.IF01_AGNR_FNR
                      AND f.IF10_FORSFOM_SEQ = v.IF10_FORSFOM_SEQ
                      AND f.IF12_BETDATO IS NOT NULL
                      AND f.IF12_BETDATO != 0
                ) AS er_betalt_noen_gang
            FROM oppslag_IF_VEDFRIVT_10 v
            WHERE v.oppslag_id = :oppslag_id
        """
        val navKjøpteForsikringer = transaction.run(
            queryOf(statement, mapOf("oppslag_id" to oppslagId))
                .map { row ->
                    NavKjøptForsikring(
                        IF01_KODE = row.string("IF01_KODE").single(),
                        IF01_AGNR_FNR = row.long("IF01_AGNR_FNR"),
                        IF10_FORSFOM_SEQ = row.int("IF10_FORSFOM_SEQ"),
                        type = when (val type = row.string("IF10_TYPE")) {
                            "1" -> NavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1
                            "2" -> NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17
                            "3" -> NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1
                            "4" -> NavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1
                            "5" -> NavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1
                            else -> error("Ukjent forsikringstype: $type")
                        },
                        virkningsdato = row.intToLocalDate("IF10_VIRKDATO")!!,
                        opphørsdato = row.intToLocalDate("IF10_FORSTOM"),
                        erBetaltNoenGang = row.boolean("er_betalt_noen_gang"),
                    )
                }
                .asList
        )
        return Oppslag(id = oppslagId, navKjøpteForsikringer = navKjøpteForsikringer)
    }

    fun lagreEkskluderinger(oppslagId: UUID, ekskluderinger: List<Pair<NavKjøptForsikring, NavKjøptForsikring.Ekskluderingsårsak>>) {
        ekskluderinger.forEach { (forsikring, årsak) ->
            lagreEkskludering(oppslagId, forsikring, årsak)
        }
    }

    private fun lagreEkskludering(oppslagId: UUID, forsikring: NavKjøptForsikring, årsak: NavKjøptForsikring.Ekskluderingsårsak) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO oppslag_nav_kjopt_forsikring_ekskludering
                (oppslag_id, IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ, ekskluderingsaarsak)
            VALUES
                (:oppslag_id, :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ, :ekskluderingsaarsak)
        """
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "oppslag_id" to oppslagId,
                    "IF01_KODE" to forsikring.IF01_KODE.toString(),
                    "IF01_AGNR_FNR" to forsikring.IF01_AGNR_FNR,
                    "IF10_FORSFOM_SEQ" to forsikring.IF10_FORSFOM_SEQ,
                    "ekskluderingsaarsak" to årsak.name,
                )
            ).asUpdate
        )
    }

    private fun Row.intToLocalDate(label: String) = int(label).toLocalDate()

    private fun Int.toLocalDate() =
        if (this == 0) null else LocalDate.parse(this.toString().padStart(8, '0'), DateTimeFormatter.ofPattern("yyyyMMdd"))
}
