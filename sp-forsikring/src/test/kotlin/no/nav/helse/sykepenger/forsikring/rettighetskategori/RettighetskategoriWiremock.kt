package no.nav.helse.sykepenger.forsikring.rettighetskategori

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.every
import io.mockk.mockk
import java.util.*

class RettighetskategoriWiremock {
    private val server =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)

    /**
     * En ekte klient som snakker med wiremock-stubben, slik at tester kan sjekke hva som faktisk ble sendt til
     * sp-rettighetskategori.
     */
    val client: RettighetskategoriClient by lazy {
        RettighetskategoriClient(
            baseUrl = baseUrl(),
            tokenClient = mockk<AccessTokenProvider> { every { machineToken(any()) } returns ACCESS_TOKEN },
            scope = SCOPE,
        )
    }

    /**
     * Svarer med rettighetskategorien bare når både ID-en og fødselsnummeret stemmer. Ellers svarer WireMock 404,
     * slik sp-rettighetskategori gjør for ukjente ID-er og andre personer.
     */
    fun stubRettighetskategori(
        rettighetskategoriId: UUID,
        fødselsnummer: String,
        hovedkategori: String,
        tilleggskategorier: List<String>,
    ) {
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .withRequestBody(equalToJson("""{ "fødselsnummer": "$fødselsnummer" }"""))
                .willReturn(
                    okJson(
                        """
                        {
                          "id": "$rettighetskategoriId",
                          "skjæringstidspunkt": "2026-01-01",
                          "hovedkategori": "$hovedkategori",
                          "tilleggskategorier": [ ${tilleggskategorier.joinToString { "\"$it\"" }} ],
                          "kilde": "AUTOMATISK",
                          "vurderingstidspunkt": "2026-01-01T12:00:00Z"
                        }
                        """.trimIndent(),
                    ),
                ),
        )
    }

    fun stubFeil(
        rettighetskategoriId: UUID,
        status: Int,
    ) {
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""{ "type": "about:blank", "status": $status }"""),
                ),
        )
    }

    fun loggedRequests(): List<LoggedRequest> = server.findAll(postRequestedFor(urlPathMatching("/api/rettighetskategorier/.*")))

    fun reset() {
        server.resetAll()
    }

    fun baseUrl(): String = server.baseUrl()

    companion object {
        const val ACCESS_TOKEN = "rettighetskategori-test-token"
        const val SCOPE = "api://test.tbd.sp-rettighetskategori/.default"
    }
}
