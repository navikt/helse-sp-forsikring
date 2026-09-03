package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.Parameter
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.replikabase.tilInfotrygdFødselsnummer
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.oracle.OracleContainer
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

object TestcontainersReplikadatabase {
    val oracleContainer: OracleContainer =
        OracleContainer("gvenzl/oracle-free:slim-faststart")
            .also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = oracleContainer.jdbcUrl
                username = oracleContainer.username
                password = oracleContainer.password
                poolName = "replikabase-test"
                maximumPoolSize = 5
                connectionTimeout = 5_000
            },
        )

    init {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:replikabase/db/migration")
            .load()
            .migrate()
    }

    var vedfrivtSeq = 0
    var fkontoSeq = 0

    fun reset() {
        sessionOf(dataSource).use { session ->
            session.transaction { transaction ->
                transaction.run(queryOf("DELETE FROM IF_FKONTO_12").asUpdate)
                transaction.run(queryOf("DELETE FROM IF_VEDFRIVT_10").asUpdate)
            }
        }
        vedfrivtSeq = 0
        fkontoSeq = 0
    }

    fun shutdown() {
        dataSource.close()
    }

    fun opprettBetaltForsikring(
        identitetsnummer: Identitetsnummer,
        virkningsdato: LocalDate,
        type: Char,
        premiegrunnlag: Int,
        opphørsdato: LocalDate? = null,
        opphørsgrunn: String? = null,
    ) {
        val vedfrivtId = vedfrivtSeq++
        val nå = Instant.now()
        val IF01_KODE = '1'
        val IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer()
        val IF10_FORSFOM_SEQ = vedfrivtId
        val forsikringFom = virkningsdato.minusDays(28L)
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_GODKJ = 'J',
            IF10_FORSFOM = forsikringFom.tilInfotrygddato(),
            IF10_VIRKDATO = virkningsdato.tilInfotrygddato(),
            IF10_TYPE = type,
            IF10_SELVFOM = " ",
            IF10_KOMBI = 'N',
            IF10_PREMGRL = premiegrunnlag,
            IF10_FOM = 0,
            IF10_PREMIE = 0,
            IF10_GML_PREMGRL = 0,
            IF10_GML_FOM = 0,
            IF10_GML_PREMIE = 0,
            IF10_FRIFOM = 0,
            IF10_FORSTOM = opphørsdato?.tilInfotrygddato() ?: 0,
            IF10_OPPHGR = opphørsgrunn ?: " ",
            IF10_VARSEL = 0,
            IF10_TERM_KV = ' ',
            IF10_TERM_AAR = " ",
            IF10_VARSEL_BELOEP = 0,
            IF10_BETALT_BELOEP = 0,
            IF10_PURR = 0,
            IF10_TKNR_BOST = 0,
            IF10_TKNR_BEH = 0,
            OPPRETTET = nå,
            ENDRET_I_KILDE = nå,
            KILDE_IF = " ",
            ID_VED = BigDecimal.valueOf(vedfrivtId.toLong()),
            OPPDATERT = nå,
        )
        val fkontoId = fkontoSeq++

        val betalingFomYearMonth = YearMonth.of(forsikringFom.year, 1 + (6 * (forsikringFom.month.value / 7)))
        val betalingTomYearMonth = betalingFomYearMonth.plusMonths(5)
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = fkontoId,
            IF12_FOM = betalingFomYearMonth.atDay(1).tilInfotrygddato(),
            IF12_TOM = betalingTomYearMonth.atEndOfMonth().tilInfotrygddato(),
            IF12_BET_KODE = 'B',
            IF12_FRIUKER = null,
            IF12_BELOEP = null,
            IF12_BETDATO = forsikringFom.plusDays(14).tilInfotrygddato(),
            OPPRETTET = nå,
            ENDRET_I_KILDE = nå,
            KILDE_IF = " ",
            ID_KONT = BigDecimal.valueOf(fkontoId.toLong()),
            OPPDATERT = nå,
        )
    }

    fun insertVedfrivt(
        IF01_KODE: Char = '1',
        IF01_AGNR_FNR: Long,
        IF10_FORSFOM_SEQ: Int = 0,
        IF10_GODKJ: Char = 'J',
        IF10_FORSFOM: Int = 0,
        IF10_VIRKDATO: Int = 20260101,
        IF10_TYPE: Char = '1',
        IF10_SELVFOM: String = " ",
        IF10_KOMBI: Char = ' ',
        IF10_PREMGRL: Int = 0,
        IF10_FOM: Int = 0,
        IF10_PREMIE: Int = 0,
        IF10_GML_PREMGRL: Int = 0,
        IF10_GML_FOM: Int = 0,
        IF10_GML_PREMIE: Int = 0,
        IF10_FRIFOM: Int = 0,
        IF10_FORSTOM: Int = 0,
        IF10_OPPHGR: String = " ",
        IF10_VARSEL: Int = 0,
        IF10_TERM_KV: Char = ' ',
        IF10_TERM_AAR: String = " ",
        IF10_VARSEL_BELOEP: Int = 0,
        IF10_BETALT_BELOEP: Int = 0,
        IF10_PURR: Int = 0,
        IF10_TKNR_BOST: Int = 0,
        IF10_TKNR_BEH: Int = 0,
        OPPRETTET: Instant = Instant.now(),
        ENDRET_I_KILDE: Instant = Instant.now(),
        KILDE_IF: String = " ",
        ID_VED: BigDecimal = BigDecimal.ZERO,
        OPPDATERT: Instant? = null,
    ) {
        sessionOf(dataSource).use { session ->
            @Language("Oracle")
            val query = """
                INSERT INTO IF_VEDFRIVT_10 (
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ, IF10_GODKJ, IF10_FORSFOM,
                    IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM, IF10_KOMBI, IF10_PREMGRL,
                    IF10_FOM, IF10_PREMIE, IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                    IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL, IF10_TERM_KV,
                    IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP, IF10_PURR,
                    IF10_TKNR_BOST, IF10_TKNR_BEH, OPPRETTET, ENDRET_I_KILDE, KILDE_IF,
                    ID_VED, OPPDATERT
                ) VALUES (
                    :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ, :IF10_GODKJ, :IF10_FORSFOM,
                    :IF10_VIRKDATO, :IF10_TYPE, :IF10_SELVFOM, :IF10_KOMBI, :IF10_PREMGRL,
                    :IF10_FOM, :IF10_PREMIE, :IF10_GML_PREMGRL, :IF10_GML_FOM, :IF10_GML_PREMIE,
                    :IF10_FRIFOM, :IF10_FORSTOM, :IF10_OPPHGR, :IF10_VARSEL, :IF10_TERM_KV,
                    :IF10_TERM_AAR, :IF10_VARSEL_BELOEP, :IF10_BETALT_BELOEP, :IF10_PURR,
                    :IF10_TKNR_BOST, :IF10_TKNR_BEH, :OPPRETTET, :ENDRET_I_KILDE, :KILDE_IF,
                    :ID_VED, :OPPDATERT
                )
            """
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "IF01_KODE" to IF01_KODE.toString(),
                        "IF01_AGNR_FNR" to IF01_AGNR_FNR,
                        "IF10_FORSFOM_SEQ" to IF10_FORSFOM_SEQ,
                        "IF10_GODKJ" to IF10_GODKJ.toString(),
                        "IF10_FORSFOM" to IF10_FORSFOM,
                        "IF10_VIRKDATO" to IF10_VIRKDATO,
                        "IF10_TYPE" to IF10_TYPE.toString(),
                        "IF10_SELVFOM" to IF10_SELVFOM,
                        "IF10_KOMBI" to IF10_KOMBI.toString(),
                        "IF10_PREMGRL" to IF10_PREMGRL,
                        "IF10_FOM" to IF10_FOM,
                        "IF10_PREMIE" to IF10_PREMIE,
                        "IF10_GML_PREMGRL" to IF10_GML_PREMGRL,
                        "IF10_GML_FOM" to IF10_GML_FOM,
                        "IF10_GML_PREMIE" to IF10_GML_PREMIE,
                        "IF10_FRIFOM" to IF10_FRIFOM,
                        "IF10_FORSTOM" to IF10_FORSTOM,
                        "IF10_OPPHGR" to IF10_OPPHGR,
                        "IF10_VARSEL" to IF10_VARSEL,
                        "IF10_TERM_KV" to IF10_TERM_KV.toString(),
                        "IF10_TERM_AAR" to IF10_TERM_AAR,
                        "IF10_VARSEL_BELOEP" to IF10_VARSEL_BELOEP,
                        "IF10_BETALT_BELOEP" to IF10_BETALT_BELOEP,
                        "IF10_PURR" to IF10_PURR,
                        "IF10_TKNR_BOST" to IF10_TKNR_BOST,
                        "IF10_TKNR_BEH" to IF10_TKNR_BEH,
                        "OPPRETTET" to Timestamp.from(OPPRETTET),
                        "ENDRET_I_KILDE" to Timestamp.from(ENDRET_I_KILDE),
                        "KILDE_IF" to KILDE_IF,
                        "ID_VED" to ID_VED,
                        "OPPDATERT" to Parameter(OPPDATERT?.let { Timestamp.from(it) }, Timestamp::class.java),
                    ),
                ).asUpdate,
            )
        }
    }

    fun insertFkonto12(
        IF01_KODE: Char = '1',
        IF01_AGNR_FNR: Long? = null,
        IF10_FORSFOM_SEQ: Int? = null,
        IF12_BETDATO_SEQ: Int? = null,
        IF12_FOM: Int? = null,
        IF12_TOM: Int? = null,
        IF12_BET_KODE: Char? = null,
        IF12_FRIUKER: String? = null,
        IF12_BELOEP: BigDecimal? = null,
        IF12_BETDATO: Int? = null,
        OPPRETTET: Instant = Instant.now(),
        ENDRET_I_KILDE: Instant = Instant.now(),
        KILDE_IF: String = " ",
        ID_KONT: BigDecimal = BigDecimal.ZERO,
        OPPDATERT: Instant? = null,
    ) {
        sessionOf(dataSource).use { session ->
            @Language("Oracle")
            val query = """
                INSERT INTO IF_FKONTO_12 (
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ, IF12_BETDATO_SEQ,
                    IF12_FOM, IF12_TOM, IF12_BET_KODE, IF12_FRIUKER, IF12_BELOEP,
                    IF12_BETDATO, OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT, OPPDATERT
                ) VALUES (
                    :IF01_KODE, :IF01_AGNR_FNR, :IF10_FORSFOM_SEQ, :IF12_BETDATO_SEQ,
                    :IF12_FOM, :IF12_TOM, :IF12_BET_KODE, :IF12_FRIUKER, :IF12_BELOEP,
                    :IF12_BETDATO, :OPPRETTET, :ENDRET_I_KILDE, :KILDE_IF, :ID_KONT, :OPPDATERT
                )
            """
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "IF01_KODE" to IF01_KODE.toString(),
                        "IF01_AGNR_FNR" to Parameter(IF01_AGNR_FNR, Long::class.java),
                        "IF10_FORSFOM_SEQ" to Parameter(IF10_FORSFOM_SEQ, Int::class.java),
                        "IF12_BETDATO_SEQ" to Parameter(IF12_BETDATO_SEQ, Int::class.java),
                        "IF12_FOM" to Parameter(IF12_FOM, Int::class.java),
                        "IF12_TOM" to Parameter(IF12_TOM, Int::class.java),
                        "IF12_BET_KODE" to Parameter(IF12_BET_KODE?.toString(), String::class.java),
                        "IF12_FRIUKER" to Parameter(IF12_FRIUKER, String::class.java),
                        "IF12_BELOEP" to Parameter(IF12_BELOEP, BigDecimal::class.java),
                        "IF12_BETDATO" to Parameter(IF12_BETDATO, Int::class.java),
                        "OPPRETTET" to Timestamp.from(OPPRETTET),
                        "ENDRET_I_KILDE" to Timestamp.from(ENDRET_I_KILDE),
                        "KILDE_IF" to KILDE_IF,
                        "ID_KONT" to ID_KONT,
                        "OPPDATERT" to Parameter(OPPDATERT?.let { Timestamp.from(it) }, Timestamp::class.java),
                    ),
                ).asUpdate,
            )
        }
    }
}
