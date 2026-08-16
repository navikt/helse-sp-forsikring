package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.forsikring.api.forsikringsvurderingApi
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.kafka.ForsikringsvurderingBehovRiver
import no.nav.helse.sykepenger.forsikring.kafka.ForsikringsvurderingResultatBehovRiver
import no.nav.helse.sykepenger.forsikring.kafka.SelvstendigIngenDagerIgjenRiver
import no.nav.helse.sykepenger.forsikring.kafka.SelvstendigUtbetaltEtterVentetidRiver
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetRiver
import no.nav.helse.sykepenger.forsikring.kafka.VedtakFattetTellerRiver
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.telling.infrastruktur.TellingDao
import org.flywaydb.core.Flyway
import java.time.Duration

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val spForsikringDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("DATABASE_JDBC_URL")
                maximumPoolSize = 10
            },
        )

    val replikabaseDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("ORACLE_URL")
                username = env.getValue("ORACLE_USERNAME")
                password = env.getValue("ORACLE_PASSWORD")
                schema = env.getValue("ORACLE_DATABASE")
                connectionTimeout = Duration.ofSeconds(20).toMillis()
                maxLifetime = Duration.ofMinutes(30).toMillis()
                initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            },
        )

    val forsikringsvurderingService = ForsikringsvurderingService(replikabaseDataSource = replikabaseDataSource)

    val tellingDao = TellingDao(dataSource = spForsikringDataSource)

    val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
        }

    val gosysOppgaveClient =
        GosysOppgaveClient(
            baseUrl = env.getValue("GOSYS_BASE_URL"),
            tokenClient = createAzureTokenClientFromEnvironment(env),
            httpClient = httpClient,
            gosysScope = env.getValue("GOSYS_SCOPE"),
        )

    RapidApplication
        .create(System.getenv(), builder = {
            withKtorModule {
                forsikringsvurderingApi(
                    spForsikringDataSource = spForsikringDataSource,
                    forsikringsvurderingService = forsikringsvurderingService,
                    clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                    issuerUrl = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
                    jwkProviderUri = env.getValue("AZURE_OPENID_CONFIG_JWKS_URI"),
                )

                monitor.subscribe(ApplicationStarted) {
                    loggInfo("Migrerer database")
                    Flyway
                        .configure()
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
                replikabaseDataSource = replikabaseDataSource,
                spForsikringDataSource = spForsikringDataSource,
            )
            ForsikringsvurderingResultatBehovRiver(
                rapidsConnection = this,
                spForsikringDataSource = spForsikringDataSource,
            )
            SelvstendigUtbetaltEtterVentetidRiver(
                rapidsConnection = this,
                gosysOppgaveClient = gosysOppgaveClient,
                spForsikringDataSource = spForsikringDataSource,
            )
            SelvstendigIngenDagerIgjenRiver(
                rapidsConnection = this,
                gosysOppgaveClient = gosysOppgaveClient,
                spForsikringDataSource = spForsikringDataSource,
            )
            VedtakFattetRiver(
                rapidsConnection = this,
                gosysOppgaveClient = gosysOppgaveClient,
                spForsikringDataSource = spForsikringDataSource,
            )
            VedtakFattetTellerRiver(
                rapidsConnection = this,
                spForsikringDataSource = spForsikringDataSource,
                tellingDao = tellingDao,
            )
        }.start()
}
