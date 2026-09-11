package no.nav.helse.sykepenger.forsikring.gosys

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.retry.retry
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import java.time.LocalDate
import java.util.*

class GosysOppgaveClient(
    private val baseUrl: String,
    private val tokenClient: AccessTokenProvider,
    private val httpClient: HttpClient,
    private val gosysScope: String,
) {
    suspend fun opprettOppgave(
        personident: String,
        uuid: String,
        beskrivelse: String,
    ) {
        retry {
            val url = "$baseUrl/api/v1/oppgaver"
            val request =
                OpprettOppgaveRequest(
                    personident = personident,
                    uuid = uuid,
                    aktivDato = LocalDate.now(),
                    prioritet = "NORM",
                    oppgavetype = "VURD_HENV",
                    tema = "FOS",
                    behandlingstype = "ae0221",
                    beskrivelse = beskrivelse,
                )
            val xCorrelationId = UUID.randomUUID().toString()
            loggInfo("Gjør HTTP POST $url", "request" to request.toString(), "X-Correlation-ID" to xCorrelationId)
            val response =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    val bearerToken = withContext(Dispatchers.IO) { tokenClient.machineToken(gosysScope) }
                    bearerAuth(bearerToken)
                    setBody(request)
                    header("X-Correlation-ID", xCorrelationId)
                }

            val message = "Fikk HTTP ${response.status} i svar fra Gosys"
            if (response.status !in listOf(HttpStatusCode.Created, HttpStatusCode.Conflict)) {
                loggError(message, "response" to response.bodyAsText())
                error(message)
            } else {
                loggInfo(message, "response" to response.bodyAsText())
            }
        }
    }
}
