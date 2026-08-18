package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

object Database {
    private val postgresContainer = PostgreSQLContainer("postgres:18").also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
            },
        )

    private val flyway =
        Flyway
            .configure()
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

    fun countRåkopi(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM råkopi").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    fun countForsikringsvurdering(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM forsikringsvurdering").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    fun countRåkopiIfVedfrivt10(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM råkopi_IF_VEDFRIVT_10").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    fun countRåkopiIfFkonto12(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM råkopi_IF_FKONTO_12").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    fun countNavKjøpteForsikringer(dataSource: DataSource = this.dataSource): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM forsikringsvurdering_navkjøpt_forsikring").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
}
