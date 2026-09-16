package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.forsikring.api.api
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.kafka.*
import no.nav.sykepenger.libs.logging.loggInfo
import org.flywaydb.core.Flyway
import java.net.URI
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
                maximumPoolSize = env.getValue("DB_MAX_POOL_SIZE").toInt()
                minimumIdle = 1
                idleTimeout = Duration.ofMinutes(10).toMillis()
            },
        )

    val replikabaseDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("REPLIKABASE_JDBC_URL")
                username = env.getValue("REPLIKABASE_USERNAME")
                password = env.getValue("REPLIKABASE_PASSWORD")
                schema = env.getValue("REPLIKABASE_SCHEMA")
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = Duration.ofMinutes(10).toMillis()
                connectionTimeout = Duration.ofSeconds(20).toMillis()
                maxLifetime = Duration.ofMinutes(30).toMillis()
                initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            },
        )

    val forsikringsvurderingService = ForsikringsvurderingService(replikabaseDataSource = replikabaseDataSource)

    val accessTokenProvider =
        TexasClient(
            tokenEndpoint = URI(env.getValue("NAIS_TOKEN_ENDPOINT")),
            tokenExchangeEndpoint = URI(env.getValue("NAIS_TOKEN_EXCHANGE_ENDPOINT")),
        )

    val gosysOppgaveClient =
        GosysOppgaveClient(
            baseUrl = env.getValue("GOSYS_BASE_URL"),
            tokenClient = accessTokenProvider,
            gosysScope = env.getValue("GOSYS_SCOPE"),
        )

    val versjonAvKode = env.getValue("NAIS_APP_IMAGE")

    // Det er en sirkulær avhengighet mellom RapidApplication og Ktor-oppsettet: API-et trenger rapiden for å
    // publisere subsumsjoner, men rapiden bygger Ktor-modulen. Vi utsetter derfor hele Ktor-oppsettet til
    // applikasjonen starter, og da har vi en ferdig RapidsConnection å koble inn.
    lateinit var ktorOppsett: Application.() -> Unit

    RapidApplication
        .create(
            env = env,
            consumerProducerFactory = ConsumerProducerFactory(kafkaConfig),
            builder = {
                env["HTTP_PORT"]?.toInt()?.let(::withHttpPort)
                withKtorModule { ktorOppsett() }
            },
        ).apply {
            val subsumsjonspubliserer =
                RapidSubsumsjonspubliserer(
                    messageContext = this,
                    versjonAvKode = versjonAvKode,
                )
            val endretForsikringsvurderingPubliserer =
                RapidEndretForsikringsvurderingPubliserer(
                    messageContext = this,
                )

            ktorOppsett = {
                api(
                    spForsikringDataSource = spForsikringDataSource,
                    forsikringsvurderingService = forsikringsvurderingService,
                    clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                    issuerUrl = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
                    jwkProviderUri = env.getValue("AZURE_OPENID_CONFIG_JWKS_URI"),
                    subsumsjonspubliserer = subsumsjonspubliserer,
                    endretForsikringsvurderingPubliserer = endretForsikringsvurderingPubliserer,
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

            ForsikringsvurderingBehovRiver(
                rapidsConnection = this,
                replikabaseDataSource = replikabaseDataSource,
                spForsikringDataSource = spForsikringDataSource,
                versjonAvKode = versjonAvKode,
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
        }.start()
}
