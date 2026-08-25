package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.*

object TestcontainersSpForsikringDatabase {
    private val postgresContainer =
        PostgreSQLContainer("postgres:18")
            .also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
                poolName = "sp-forsikring-test"
                maximumPoolSize = 5
                connectionTimeout = 5_000
            },
        )

    init {
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    fun reset() {
        val tabeller =
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        """
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                        """.trimIndent(),
                    ).map { it.string("tablename") }.asList,
                )
            }
        if (tabeller.isEmpty()) return
        sessionOf(dataSource).use { session ->
            session.run(queryOf("TRUNCATE TABLE ${tabeller.joinToString(", ")} RESTART IDENTITY CASCADE").asExecute)
        }
    }

    fun shutdown() {
        dataSource.close()
    }

    fun countRåkopi(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement =
            """
            SELECT COUNT(*)
            FROM råkopi
            WHERE id IN (
              SELECT råkopi_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
            )
            """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId),
                ).map { it.int(1) }.asSingle,
            )!!
        }
    }

    fun countAlleRåkopier(): Int = countRows("råkopi")

    fun countAlleForsikringsvurderinger(): Int = countRows("forsikringsvurdering")

    fun countRåkopiIF_VEDFRIVT_10(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement =
            """
            SELECT COUNT(*)
            FROM råkopi_IF_VEDFRIVT_10
            WHERE råkopi_id IN (
              SELECT råkopi_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
            )
            """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId),
                ).map { it.int(1) }.asSingle,
            )!!
        }
    }

    fun countRåkopiIF_FKONTO_12(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement =
            """
            SELECT COUNT(*)
            FROM råkopi_IF_FKONTO_12
            WHERE råkopi_IF_VEDFRIVT_10_id IN (
              SELECT id FROM råkopi_IF_VEDFRIVT_10 WHERE råkopi_id IN (
                SELECT råkopi_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
              )
            )
            """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId),
                ).map { it.int(1) }.asSingle,
            )!!
        }
    }

    fun hentEkskluderinger(forsikringsvurderingId: UUID): Map<Int, String> {
        @Language("PostgreSQL")
        val statement =
            """
            SELECT råkopi_IF_VEDFRIVT_10.IF10_FORSFOM_SEQ, forsikringsvurdering_individuell_forsikring.konklusjon
            FROM råkopi_IF_VEDFRIVT_10, forsikringsvurdering_individuell_forsikring
            WHERE råkopi_IF_VEDFRIVT_10.id = forsikringsvurdering_individuell_forsikring.råkopi_IF_VEDFRIVT_10_id
            AND forsikringsvurdering_individuell_forsikring.forsikringsvurdering_id = :forsikringsvurdering_id::uuid
            AND forsikringsvurdering_individuell_forsikring.konklusjon <> 'GYLDIG'
            """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session
                .run(
                    queryOf(
                        statement,
                        mapOf("forsikringsvurdering_id" to forsikringsvurderingId),
                    ).map { it.int("IF10_FORSFOM_SEQ") to it.string("konklusjon") }.asList,
                ).toMap()
        }
    }

    private fun countRows(tableName: String): Int {
        @Language("PostgreSQL")
        val statement = "SELECT COUNT(*) FROM $tableName"
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(statement)
                    .map { it.int(1) }
                    .asSingle,
            )!!
        }
    }
}
