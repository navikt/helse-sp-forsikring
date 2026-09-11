package no.nav.helse.sykepenger.forsikring.gosys

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.mockk.every
import io.mockk.mockk
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

class GosysWiremock {
    private val GOSYS_OPPGAVER_PATH = "/api/v1/oppgaver"

    private val objectMapper = jacksonObjectMapper()

    val gosysWiremock =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)

    init {
        stubOppgaverRespons(HTTP_CREATED)
    }

    /**
     * En ekte klient som snakker med wiremock-stubben, slik at tester kan sjekke hva som faktisk ble sendt til Gosys.
     */
    val oppgaveClient: GosysOppgaveClient by lazy {
        GosysOppgaveClient(
            baseUrl = baseUrl(),
            tokenClient = mockk<AccessTokenProvider> { every { machineToken(any()) } returns ACCESS_TOKEN },
            httpClient =
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                },
            gosysScope = GOSYS_SCOPE,
        )
    }

    val oppgaver: List<JsonNode>
        get() = loggedPostOppgaverRequests().map { objectMapper.readTree(it.bodyAsString) }

    val sisteOppgave: JsonNode? get() = oppgaver.lastOrNull()

    fun loggedPostOppgaverRequests(): List<LoggedRequest> =
        gosysWiremock.findAll(
            postRequestedFor(urlPathEqualTo(GOSYS_OPPGAVER_PATH)),
        )

    /**
     * Lar testene styre hvilken status Gosys svarer med, for eksempel 409 ved duplikat eller 500 ved feil.
     */
    fun stubOppgaverRespons(status: Int) {
        gosysWiremock.resetMappings()
        gosysWiremock.stubFor(
            post(urlPathEqualTo(GOSYS_OPPGAVER_PATH))
                .willReturn(aResponse().withStatus(status)),
        )
    }

    fun reset() {
        gosysWiremock.resetRequests()
        stubOppgaverRespons(HTTP_CREATED)
    }

    fun baseUrl(): String = gosysWiremock.baseUrl()

    companion object {
        const val ACCESS_TOKEN = "test-token"
        const val GOSYS_SCOPE = "test-scope"
        private const val HTTP_CREATED = 201
    }
}
