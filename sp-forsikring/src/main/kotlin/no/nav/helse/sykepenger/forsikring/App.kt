package no.nav.helse.sykepenger.forsikring

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import java.time.Duration
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.PgForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import org.flywaydb.core.Flyway

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val spForsikringDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.getValue("DATABASE_JDBC_URL")
            maximumPoolSize = 2
        }
    )

    val replikabaseDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.getValue("ORACLE_URL")
            username = env.getValue("ORACLE_USERNAME")
            password = env.getValue("ORACLE_PASSWORD")
            schema = env.getValue("ORACLE_DATABASE")
            connectionTimeout = Duration.ofSeconds(20).toMillis()
            maxLifetime = Duration.ofMinutes(30).toMillis()
            initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        }
    )

    val forsikringsvurderingRepository = PgForsikringsvurderingRepository(spForsikringDataSource)
    val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)
    val oppslagService = OppslagService(replikabaseDao)
    val forsikringsvurderingService = ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)

    RapidApplication
        .create(System.getenv(), builder = {
            withKtorModule {
                forsikringsvurderingApi(
                    replikabaseDataSource = replikabaseDataSource,
                    forsikringsvurderingRepository = forsikringsvurderingRepository,
                    clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                    issuerUrl = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
                    jwkProviderUri = env.getValue("AZURE_OPENID_CONFIG_JWKS_URI")
                )

                monitor.subscribe(ApplicationStarted) {
                    loggInfo("Migrerer database")
                    Flyway.configure()
                        .dataSource(spForsikringDataSource)
                        .cleanDisabled(true)
                        .lockRetryCount(-1)
                        .load()
                        .migrate()
                    loggInfo("Migrering ferdig!")
                }
                monitor.subscribe(ApplicationStopped) {
                    loggInfo("Forsøker å lukke datasourcer...")
                    spForsikringDataSource.close()
                    replikabaseDataSource.close()
                    loggInfo("Lukket datasourcer")
                }
            }
        })
        .apply {
            ForsikringsvurderingBehovRiver(
                rapidsConnection = this,
                spForsikringDataSource = spForsikringDataSource,
                forsikringsvurderingService = forsikringsvurderingService,
            )
            ForsikringsvurderingResultatBehovRiver(
                rapidsConnection = this,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
            )
        }.start()
}
