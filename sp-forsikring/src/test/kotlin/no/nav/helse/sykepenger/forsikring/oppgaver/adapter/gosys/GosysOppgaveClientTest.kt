package no.nav.helse.sykepenger.forsikring.oppgaver.adapter.gosys

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.ok
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.toBigDecimal
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class GosysOppgaveClientTest {

    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `oppretter oppgave med riktig payload for utbetalt fra dag én med 80 prosent dekningsgrad`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Created)
        val client = createGosysOppgaveClient(mockEngine)

        val duplikatkontrollId = UUID.randomUUID()
        val fødselsnummer = "12345678901"
        val skjæringstidspunkt = LocalDate.of(2024, 1, 15)

        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = duplikatkontrollId,
                fødselsnummer = fødselsnummer,
                årsak = Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent,
                skjæringstidspunkt = skjæringstidspunkt
            )
        }

        assertEquals(1, capturedRequests.size)
        val request = capturedRequests.first()

        assertEquals("http://test.no/api/v1/oppgaver", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)

        val requestBody = parseRequestBody(request)
        assertEquals(duplikatkontrollId.toString(), requestBody.uuid)
        assertEquals(LocalDate.now(), requestBody.aktivDato)
        assertEquals(Prioritet.NORM, requestBody.prioritet)
        assertEquals("VURD_HENV", requestBody.oppgavetype)
        assertEquals("FOS", requestBody.tema)
        assertEquals("ae0221", requestBody.behandlingstype)
        assertEquals(fødselsnummer, requestBody.personident)
        assertEquals("Årsak: Det er utbetalt sykepenger fra dag én og vedkommende har 80% dekningsgrad. Skjæringstidspunkt: 15.01.2024.", requestBody.beskrivelse)
    }

    @Test
    fun `oppretter oppgave for sykepengerett opphørt`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Created)
        val client = createGosysOppgaveClient(mockEngine)

        val duplikatkontrollId = UUID.randomUUID()
        val fødselsnummer = "98765432109"
        val skjæringstidspunkt = LocalDate.of(2024, 6, 1)

        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = duplikatkontrollId,
                fødselsnummer = fødselsnummer,
                årsak = Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød,
                skjæringstidspunkt = skjæringstidspunkt
            )
        }

        assertEquals(1, capturedRequests.size)
        val requestBody = parseRequestBody(capturedRequests.first())

        assertEquals("Årsak: Sykepengerett har opphørt som følge av ingen gjenstående dager. Skjæringstidspunkt: 01.06.2024.", requestBody.beskrivelse)
    }

    @Test
    fun `oppretter oppgave for stort avvik mellom sykepengegrunnlag og premiegrunnlag`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Created)
        val client = createGosysOppgaveClient(mockEngine)

        val duplikatkontrollId = UUID.randomUUID()
        val fødselsnummer = "11111111111"
        val skjæringstidspunkt = LocalDate.of(2024, 3, 15)
        val sykepengegrunnlag = BigDecimal("500000")
        val premiegrunnlag = BigDecimal("300000")

        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = duplikatkontrollId,
                fødselsnummer = fødselsnummer,
                årsak = Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(
                    sykepengegrunnlag = sykepengegrunnlag,
                    premiegrunnlag = premiegrunnlag,
                    avviksprosent = "66.67".toBigDecimal()
                ),
                skjæringstidspunkt = skjæringstidspunkt
            )
        }

        assertEquals(1, capturedRequests.size)
        val requestBody = parseRequestBody(capturedRequests.first())

        assertEquals("Årsak: For stort avvik mellom sykepengegrunnlag, 500000.00, og premiegrunnlag, 300000.00. Avviket er 66.67. Skjæringstidspunkt: 15.03.2024.", requestBody.beskrivelse)
    }

    @Test
    fun `håndterer Conflict status kode`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Conflict)
        val client = createGosysOppgaveClient(mockEngine)

        val duplikatkontrollId = UUID.randomUUID()

        // Should not throw exception for Conflict status
        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = duplikatkontrollId,
                fødselsnummer = "12345678901",
                årsak = Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent,
                skjæringstidspunkt = LocalDate.of(2024, 1, 1)
            )
        }

        assertEquals(1, capturedRequests.size)
    }

    @Test
    fun `request inneholder Authorization header med bearer token`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Created)
        val client = createGosysOppgaveClient(mockEngine)

        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = UUID.randomUUID(),
                fødselsnummer = "12345678901",
                årsak = Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent,
                skjæringstidspunkt = LocalDate.of(2024, 1, 1)
            )
        }

        val request = capturedRequests.first()
        val authHeader = request.headers["Authorization"]
        assertEquals("Bearer test-token-123", authHeader)
    }

    @Test
    fun `request inneholder X-Correlation-ID header`() {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val mockEngine = createMockEngine(capturedRequests, HttpStatusCode.Created)
        val client = createGosysOppgaveClient(mockEngine)

        runBlocking {
            client.lagOppgave(
                duplikatkontrollId = UUID.randomUUID(),
                fødselsnummer = "12345678901",
                årsak = Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent,
                skjæringstidspunkt = LocalDate.of(2024, 1, 1)
            )
        }

        val request = capturedRequests.first()
        val correlationId = request.headers["X-Correlation-ID"]
        assertTrue(correlationId != null)
        // Verify it's a valid UUID
        UUID.fromString(correlationId)
    }

    private fun createMockEngine(
        capturedRequests: MutableList<HttpRequestData>,
        responseStatus: HttpStatusCode
    ): MockEngine {
        return MockEngine { request ->
            capturedRequests.add(request)
            respond(
                content = """{"id": 12345}""",
                status = responseStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
    }

    private fun createGosysOppgaveClient(mockEngine: MockEngine): GosysOppgaveClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
        }

        val azureTokenProvider: AzureTokenProvider = mockk {
            every { bearerToken(any()) } returns AzureToken("test-token-123", LocalDateTime.MAX).ok()
        }

        return GosysOppgaveClient(
            baseUrl = "http://test.no",
            tokenClient = azureTokenProvider,
            httpClient = httpClient,
            gosysScope = "test-scope"
        )
    }

    private fun parseRequestBody(request: HttpRequestData): OpprettOppgaveRequest = runBlocking {
        val bodyBytes = request.body.toByteArray()
        objectMapper.readValue(bodyBytes, OpprettOppgaveRequest::class.java)
    }
}
