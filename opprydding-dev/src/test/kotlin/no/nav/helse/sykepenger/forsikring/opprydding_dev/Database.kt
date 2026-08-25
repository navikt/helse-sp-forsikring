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

    fun countRåkopi(dataSource: DataSource = this.dataSource) = countRows("råkopi", dataSource)

    fun countForsikringsvurdering(dataSource: DataSource = this.dataSource) = countRows("forsikringsvurdering", dataSource)

    fun countRåkopiIfVedfrivt10(dataSource: DataSource = this.dataSource) = countRows("råkopi_IF_VEDFRIVT_10", dataSource)

    fun countRåkopiIfFkonto12(dataSource: DataSource = this.dataSource) = countRows("råkopi_IF_FKONTO_12", dataSource)

    fun countIndividuelleForsikringer(dataSource: DataSource = this.dataSource) = countRows("forsikringsvurdering_individuell_forsikring", dataSource)

    fun countSpesielleYrkesgrupper(dataSource: DataSource = this.dataSource) = countRows("forsikringsvurdering_spesiell_yrkesgruppe", dataSource)

    fun countVedtakFattetMeldinger(dataSource: DataSource = this.dataSource) = countRows("vedtak_fattet_melding", dataSource)

    fun countUtbetalingPerForsikringstype(dataSource: DataSource = this.dataSource) = countRows("utbetaling_per_forsikringstype", dataSource)

    private fun countRows(
        tabell: String,
        dataSource: DataSource,
    ): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $tabell").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
}
