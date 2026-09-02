package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
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
import org.flywaydb.core.Flyway
import java.time.Duration

fun main() {
    launchApplication(
        env = System.getenv(),
        kafkaConfig = AivenConfig.default,
    )
}

fun launchApplication(
    env: Map<String, String>,
    kafkaConfig: Config,
) {
    val spForsikringDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("DATABASE_JDBC_URL")
                username = env.getValue("DATABASE_USERNAME")
                password = env.getValue("DATABASE_PASSWORD")
                maximumPoolSize = 10
            },
        )

    val replikabaseDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("REPLIKABASE_JDBC_URL")
                username = env.getValue("REPLIKABASE_USERNAME")
                password = env.getValue("REPLIKABASE_PASSWORD")
                schema = env.getValue("REPLIKABASE_SCHEMA")
                connectionTimeout = Duration.ofSeconds(20).toMillis()
                maxLifetime = Duration.ofMinutes(30).toMillis()
                initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            },
        )

    val forsikringsvurderingService = ForsikringsvurderingService(replikabaseDataSource = replikabaseDataSource)

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
        .create(
            env = env,
            consumerProducerFactory = ConsumerProducerFactory(kafkaConfig),
            builder = {
                env["HTTP_PORT"]?.toInt()?.let(::withHttpPort)
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
            },
        ).apply {
            ForsikringsvurderingBehovRiver(
                rapidsConnection = this,
                replikabaseDataSource = replikabaseDataSource,
                spForsikringDataSource = spForsikringDataSource,
                versjonAvKode = env.getValue("NAIS_APP_IMAGE"),
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
            )
        }.start()
}
