package no.nav.helse.sykepenger.forsikring.gosys

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

class GosysWiremock {
    private val GOSYS_OPPGAVER_PATH = "/api/v1/oppgaver"

    private val objectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val gosysWiremock =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)
            .also { wireMockServer ->
                wireMockServer.stubFor(
                    post(urlPathEqualTo(GOSYS_OPPGAVER_PATH))
                        .willReturn(aResponse().withStatus(201)),
                )
            }

    /**
     * En ekte klient som snakker med wiremock-stubben, slik at tester kan sjekke hva som faktisk ble sendt til Gosys.
     */
    val oppgaveClient: GosysOppgaveClient by lazy {
        GosysOppgaveClient(
            baseUrl = baseUrl(),
            tokenClient = mockk<AccessTokenProvider> { every { machineToken(any()) } returns "test-token" },
            httpClient =
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                },
            gosysScope = "test-scope",
        )
    }

    val oppgaver: List<OpprettOppgaveRequest>
        get() =
            loggedPostOppgaverRequests().map {
                objectMapper.readValue(it.bodyAsString, OpprettOppgaveRequest::class.java)
            }

    val sisteOppgave: OpprettOppgaveRequest? get() = oppgaver.lastOrNull()

    fun loggedPostOppgaverRequests(): List<LoggedRequest> =
        gosysWiremock.findAll(
            postRequestedFor(urlPathEqualTo(GOSYS_OPPGAVER_PATH)),
        )

    fun reset() {
        gosysWiremock.resetRequests()
    }

    fun baseUrl(): String = gosysWiremock.baseUrl()
}
