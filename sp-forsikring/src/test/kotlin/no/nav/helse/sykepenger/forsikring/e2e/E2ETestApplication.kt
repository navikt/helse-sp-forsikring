package no.nav.helse.sykepenger.forsikring.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
    const val CLIENT_ID = "sp-forsikring-e2e"
    val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    const val GOSYS_OPPGAVER_PATH = "/api/v1/oppgaver"

    val gosysWiremock =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)
            .also { wireMockServer ->
                wireMockServer.stubFor(
                    post(urlPathEqualTo(GOSYS_OPPGAVER_PATH))
                        .willReturn(aResponse().withStatus(201)),
                )
            }

    private val httpPort = ServerSocket(0).use(ServerSocket::getLocalPort)
    val baseUrl = "http://localhost:$httpPort"

    const val KAFKA_CONSUMER_GROUP_ID = CLIENT_ID

    private var started = false

    @Volatile
    private var applikasjonsfeil: Throwable? = null

    /**
     * Alle E2E-testene deler den samme applikasjonsinstansen. Hvis en river kaster exception, stopper hele
     * applikasjonen, og da vil alle etterfølgende tester bare stå og vente på meldinger som aldri kommer.
     * Denne sjekken gjør at vi feiler raskt og med den opprinnelige feilen i stedet for på en kryptisk timeout.
     */
    fun sjekkAtApplikasjonenLever() {
        applikasjonsfeil?.let {
            throw IllegalStateException(
                "E2E-applikasjonen har krasjet og er ikke lenger i stand til å behandle meldinger. " +
                    "Feilen kan ha skjedd i en tidligere test - se årsaken under.",
                it,
            )
        }
        check(applikasjonstråd.isAlive) {
            "E2E-applikasjonen er ikke lenger i live og behandler ingen meldinger. Den ble sannsynligvis stoppet " +
                "av exception i en river i en tidligere test - se etter den første feilen i loggen."
        }
    }

    @Synchronized
    private fun ensureStarted() {
        if (!started) {
            ventTilIsreadyGir200()
            // Venter på livssyklusmeldingene fra rapids and rivers
            TestcontainersRapid.Klient(startOffset = 0).use { rapid ->
                rapid.konsumerMelding(timeoutSekunder = 30) { it["@event_name"].stringValue() == "application_up" }
                rapid.konsumerMelding(timeoutSekunder = 30) { it["@event_name"].stringValue() == "application_ready" }
            }
            started = true
        }
    }

    fun reset() {
        ensureStarted()
        sjekkAtApplikasjonenLever()
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
        gosysWiremock.resetRequests()
    }

    private val applikasjonstråd by lazy {
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
                            "AZURE_APP_CLIENT_ID" to CLIENT_ID,
                            "AZURE_APP_CLIENT_SECRET" to "en-hemmelighet",
                            "AZURE_OPENID_CONFIG_ISSUER" to mockOAuth2Server.issuerUrl("default").toString(),
                            "AZURE_OPENID_CONFIG_JWKS_URI" to mockOAuth2Server.jwksUrl("default").toString(),
                            "HTTP_PORT" to httpPort.toString(),
                            "NAIS_APP_NAME" to CLIENT_ID,
                            "NAIS_APP_IMAGE" to "navikt/sp-forsikring:latest",
                            "RAPID_APP_NAME" to CLIENT_ID,
                            "KAFKA_CONSUMER_GROUP_ID" to KAFKA_CONSUMER_GROUP_ID,
                            "KAFKA_RAPID_TOPIC" to "tbd.rapid.v1",
                            "KAFKA_RESET_POLICY" to "earliest",
                        ),
                    kafkaConfig = TestcontainersRapid.kafkaConfig,
                )
            } catch (feil: Throwable) {
                applikasjonsfeil = feil
                feil.printStackTrace()
                throw feil
            }
        }
    }

    private fun ventTilIsreadyGir200() {
        val timeoutTidspunkt = Instant.now().plus(Duration.ofMinutes(2))
        while (Instant.now() < timeoutTidspunkt) {
            check(applikasjonstråd.isAlive) { "Applikasjonen stoppet under oppstart" }
            if (isreadyGir200()) return
            Thread.sleep(100)
        }
        error("Applikasjonen brukte for lang tid på å starte opp")
    }

    private fun isreadyGir200(): Boolean =
        runCatching {
            Request
                .get("$baseUrl/isready")
                .connectTimeout(Timeout.ofSeconds(1))
                .responseTimeout(Timeout.ofSeconds(1))
                .execute()
                .handleResponse { response -> response.code == 200 }
        }.getOrDefault(false)
}
