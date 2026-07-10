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
import java.time.Duration
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur.ForsikringsvurderingBehovRiver
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur.ForsikringsvurderingResultatBehovRiver
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur.PgForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur.forsikringsvurderingApi
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.SelvstendigIngenDagerIgjenRiver
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.SelvstendigUtbetaltEtterVentetidRiver
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.VedtakFattetRiver
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.PgOppslagRepository
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.telling.infrastruktur.TellingDao
import no.nav.helse.sykepenger.forsikring.telling.infrastruktur.VedtakFattetTellerRiver
import org.flywaydb.core.Flyway

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val spForsikringDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.getValue("DATABASE_JDBC_URL")
            maximumPoolSize = 10
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
    val oppslagRepository = PgOppslagRepository(spForsikringDataSource)
    val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)
    val oppslagService = OppslagService(replikabaseDao)
    val forsikringsvurderingService = ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)

    val tellingDao = TellingDao(dataSource = spForsikringDataSource)

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }

    val gosysOppgaveClient = GosysOppgaveClient(
        baseUrl = env.getValue("GOSYS_BASE_URL"),
        tokenClient = createAzureTokenClientFromEnvironment(env),
        httpClient = httpClient,
        gosysScope = env.getValue("GOSYS_SCOPE")
    )

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
            SelvstendigUtbetaltEtterVentetidRiver(
                rapidsConnection = this,
                oppgaveClient = gosysOppgaveClient,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
                oppslagRepository = oppslagRepository,
            )
            SelvstendigIngenDagerIgjenRiver(
                rapidsConnection = this,
                oppgaveClient = gosysOppgaveClient,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
            )
            VedtakFattetRiver(
                rapidsConnection = this,
                oppgaveClient = gosysOppgaveClient,
                oppslagRepository = oppslagRepository,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
            )
            VedtakFattetTellerRiver(
                rapidsConnection = this,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
                tellingDao = tellingDao,
            )
        }.start()
}
