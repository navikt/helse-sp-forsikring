package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.testcontainers.postgresql.PostgreSQLContainer

object TestcontainersSpForsikringDatabase {
    private val postgresContainer =
        PostgreSQLContainer("postgres:18")
            .also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
        })

    private val flyway = Flyway.configure()
        .dataSource(dataSource)
        .cleanDisabled(false)
        .load()
        .also { it.migrate() }

    fun reset() {
        flyway.clean()
        flyway.migrate()
    }

    fun shutdown() {
        dataSource.close()
    }

    fun countOppslag(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement = """
            SELECT COUNT(*)
            FROM oppslag
            WHERE id IN (
              SELECT oppslag_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
            )
        """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId)
                ).map { it.int(1) }.asSingle
            )!!
        }
    }

    fun countAlleOppslag(): Int =
        countRows("oppslag")

    fun countAlleForsikringsvurderinger(): Int =
        countRows("forsikringsvurdering")

    fun countOppslagIF_VEDFRIVT_10(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement = """
            SELECT COUNT(*)
            FROM oppslag_IF_VEDFRIVT_10
            WHERE oppslag_id IN (
              SELECT oppslag_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
            )
        """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId)
                ).map { it.int(1) }.asSingle
            )!!
        }
    }

    fun countOppslagIF_FKONTO_12(forsikringsvurderingId: String): Int {
        @Language("PostgreSQL")
        val statement = """
            SELECT COUNT(*)
            FROM oppslag_IF_FKONTO_12
            WHERE oppslag_IF_VEDFRIVT_10_id IN (
              SELECT id FROM oppslag_IF_VEDFRIVT_10 WHERE oppslag_id IN (
                SELECT oppslag_id FROM forsikringsvurdering WHERE id = :forsikringsvurderingId::uuid
              )
            )
        """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurderingId" to forsikringsvurderingId)
                ).map { it.int(1) }.asSingle
            )!!
        }
    }

    fun hentEkskluderinger(forsikringsvurderingId: UUID): Map<Int, String> {
        @Language("PostgreSQL")
        val statement = """
            SELECT oppslag_IF_VEDFRIVT_10.IF10_FORSFOM_SEQ, forsikringsvurdering_ekskludering_navkjopt_forsikring.ekskluderingsaarsak
            FROM oppslag_IF_VEDFRIVT_10, forsikringsvurdering_ekskludering_navkjopt_forsikring
            WHERE oppslag_IF_VEDFRIVT_10.id = forsikringsvurdering_ekskludering_navkjopt_forsikring.oppslag_IF_VEDFRIVT_10_id
            AND forsikringsvurdering_ekskludering_navkjopt_forsikring.forsikringsvurdering_id = :forsikringsvurdering_id::uuid
        """.trimIndent()
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    statement,
                    mapOf("forsikringsvurdering_id" to forsikringsvurderingId)
                ).map { it.int("IF10_FORSFOM_SEQ") to it.string("ekskluderingsaarsak") }.asList
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
                    .asSingle
            )!!
        }
    }
}
