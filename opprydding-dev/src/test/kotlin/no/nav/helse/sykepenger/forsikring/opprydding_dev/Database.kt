package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

object Database {
    private val postgresContainer = PostgreSQLContainer("postgres:18").also { it.start() }

    val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
        }
    )

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

    fun countOppslag(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM oppslag").executeQuery().use { rs ->
                rs.next(); rs.getInt(1)
            }
        }

    fun countForsikringsvurdering(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM forsikringsvurdering").executeQuery().use { rs ->
                rs.next(); rs.getInt(1)
            }
        }

    fun countOppslagIfVedfrivt10(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM oppslag_IF_VEDFRIVT_10").executeQuery().use { rs ->
                rs.next(); rs.getInt(1)
            }
        }

    fun countOppslagIfFkonto12(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM oppslag_IF_FKONTO_12").executeQuery().use { rs ->
                rs.next(); rs.getInt(1)
            }
        }

    fun countEkskluderinger(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM forsikringsvurdering_ekskludering_navkjopt_forsikring").executeQuery().use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
}
