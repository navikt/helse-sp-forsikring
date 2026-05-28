package no.nav.helse.sykepenger.forsikring

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
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

    fun countOppslag(oppslagId: String): Int =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """SELECT COUNT(*) FROM oppslag WHERE id = :oppslag_id::uuid""",
                    mapOf("oppslag_id" to oppslagId)
                ).map { it.int(1) }.asSingle
            )!!
        }

    fun countOppslagIF_VEDFRIVT_10(oppslagId: String): Int =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """SELECT COUNT(*) FROM oppslag_IF_VEDFRIVT_10 WHERE oppslag_id = :oppslag_id::uuid""",
                    mapOf("oppslag_id" to oppslagId)
                ).map { it.int(1) }.asSingle
            )!!
        }

    fun countOppslagIF_FKONTO_12(oppslagId: String): Int =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """SELECT COUNT(*) FROM oppslag_IF_FKONTO_12 WHERE oppslag_id = :oppslag_id::uuid""",
                    mapOf("oppslag_id" to oppslagId)
                ).map { it.int(1) }.asSingle
            )!!
        }

    fun hentEkskluderinger(oppslagId: String): Map<Int, String> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """SELECT IF10_FORSFOM_SEQ, ekskluderingsaarsak FROM oppslag_nav_kjopt_forsikring_ekskludering WHERE oppslag_id = :oppslag_id::uuid""",
                    mapOf("oppslag_id" to oppslagId)
                ).map { it.int("IF10_FORSFOM_SEQ") to it.string("ekskluderingsaarsak") }.asList
            ).toMap()
        }
}
