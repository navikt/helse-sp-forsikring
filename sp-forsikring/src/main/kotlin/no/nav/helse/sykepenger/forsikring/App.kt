package no.nav.helse.sykepenger.forsikring

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val replikabaseForsikringDao = ReplikabaseForsikringDao(
        dataSource = HikariDataSource(
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
    )
    val sykepengeforsikringService = SykepengeforsikringServiceImpl()

    replikabaseForsikringDao.testDb()

    Unit.loggInfo("Hei fra Unit \uD83D\uDC4B")

    RapidApplication
        .create(System.getenv(), builder = {
            withKtorModule {
                sykepengeforsikringApi(
                    sykepengeforsikringService = sykepengeforsikringService,
                    clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                    issuerUrl = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
                    jwkProviderUri = env.getValue("AZURE_OPENID_CONFIG_JWKS_URI")
                )
            }
        })
        .apply {
            SykepengeforsikringRiver(this, replikabaseForsikringDao)
        }.start()
}
