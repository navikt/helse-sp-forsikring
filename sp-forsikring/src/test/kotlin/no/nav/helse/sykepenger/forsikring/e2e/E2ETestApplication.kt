package no.nav.helse.sykepenger.forsikring.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import no.nav.helse.sykepenger.forsikring.launchApplication
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersRapid
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.util.Timeout
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.thread

object E2ETestApplication {
    val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    val gosysWiremock =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)

    private val httpPort = ServerSocket(0).use(ServerSocket::getLocalPort)
    val baseUrl = "http://localhost:$httpPort"

    private val applikasjonstråd by lazy {
        startIEgenTråd()
    }

    fun start() {
        ventTilApplikasjonenErKlar()
    }

    private fun startIEgenTråd(): Thread =
        thread(name = "e2e-applikasjon", isDaemon = true) {
            try {
                launchApplication(
                    env =
                        mapOf(
                            "DATABASE_JDBC_URL" to TestcontainersSpForsikringDatabase.postgresContainer.jdbcUrl,
                            "DATABASE_USERNAME" to TestcontainersSpForsikringDatabase.postgresContainer.username,
                            "DATABASE_PASSWORD" to TestcontainersSpForsikringDatabase.postgresContainer.password,
                            "REPLIKABASE_JDBC_URL" to TestcontainersReplikadatabase.oracleContainer.jdbcUrl,
                            "REPLIKABASE_USERNAME" to TestcontainersReplikadatabase.oracleContainer.username,
                            "REPLIKABASE_PASSWORD" to TestcontainersReplikadatabase.oracleContainer.password,
                            "REPLIKABASE_SCHEMA" to TestcontainersReplikadatabase.oracleContainer.username,
                            "GOSYS_BASE_URL" to gosysWiremock.baseUrl(),
                            "GOSYS_SCOPE" to "api://dev-fss.oppgavehandtering.oppgave/.default",
                            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to
                                mockOAuth2Server
                                    .tokenEndpointUrl("default")
                                    .toString(),
                            "AZURE_APP_CLIENT_ID" to "sp-forsikring-e2e",
                            "AZURE_APP_CLIENT_SECRET" to "en-hemmelighet",
                            "AZURE_OPENID_CONFIG_ISSUER" to mockOAuth2Server.issuerUrl("default").toString(),
                            "AZURE_OPENID_CONFIG_JWKS_URI" to mockOAuth2Server.jwksUrl("default").toString(),
                            "HTTP_PORT" to httpPort.toString(),
                            "NAIS_APP_NAME" to "sp-forsikring-e2e",
                            "NAIS_APP_IMAGE" to "navikt/sp-forsikring:latest",
                            "RAPID_APP_NAME" to "sp-forsikring-e2e",
                            "KAFKA_CONSUMER_GROUP_ID" to "sp-forsikring-e2e",
                            "KAFKA_RAPID_TOPIC" to "tbd.rapid.v1",
                            "KAFKA_RESET_POLICY" to "earliest",
                        ),
                    kafkaConfig = TestcontainersRapid.kafkaConfig,
                )
            } catch (feil: Throwable) {
                feil.printStackTrace()
                throw feil
            }
        }

    private fun ventTilApplikasjonenErKlar() {
        val timeoutTidspunkt = Instant.now().plus(Duration.ofMinutes(2))
        while (Instant.now() < timeoutTidspunkt) {
            check(applikasjonstråd.isAlive) { "Applikasjonen stoppet under oppstart" }
            if (erKlar()) return
            Thread.sleep(100)
        }
        error("Applikasjonen brukte for lang tid på å starte opp")
    }

    private fun erKlar(): Boolean =
        runCatching {
            Request
                .get("$baseUrl/isready")
                .connectTimeout(Timeout.ofSeconds(1))
                .responseTimeout(Timeout.ofSeconds(1))
                .execute()
                .handleResponse { response -> response.code == 200 }
        }.getOrDefault(false)
}
