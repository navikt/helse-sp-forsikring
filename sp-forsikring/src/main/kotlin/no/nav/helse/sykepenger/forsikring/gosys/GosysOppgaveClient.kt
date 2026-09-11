package no.nav.helse.sykepenger.forsikring.gosys

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.retry.retryBlocking
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.util.Timeout
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.*

class GosysOppgaveClient(
    private val baseUrl: String,
    private val tokenClient: AccessTokenProvider,
    private val gosysScope: String,
) {
    private val objectMapper = jacksonObjectMapper()

    fun opprettOppgave(
        personident: String,
        uuid: String,
        beskrivelse: String,
    ) {
        retryBlocking {
            val url = "$baseUrl/api/v1/oppgaver"
            val xCorrelationId = UUID.randomUUID().toString()
            val requestBody =
                objectMapper.writeValueAsString(
                    mapOf(
                        "personident" to personident,
                        "uuid" to uuid,
                        "aktivDato" to LocalDate.now(),
                        "prioritet" to "NORM",
                        "oppgavetype" to "VURD_HENV",
                        "tema" to "FOS",
                        "behandlingstype" to "ae0221",
                        "beskrivelse" to beskrivelse,
                    ),
                )
            loggInfo("Gjør HTTP POST $url", "X-Correlation-ID" to xCorrelationId, "Request body" to requestBody)

            val bearerToken = tokenClient.machineToken(gosysScope)
            val (responseCode, responseBody) =
                Request
                    .post(url)
                    .connectTimeout(Timeout.ofSeconds(10))
                    .responseTimeout(Timeout.ofSeconds(30))
                    .setHeader("Accept", ContentType.APPLICATION_JSON.mimeType)
                    .setHeader("Authorization", "Bearer $bearerToken")
                    .setHeader("X-Correlation-ID", xCorrelationId)
                    .bodyString(requestBody, ContentType.APPLICATION_JSON)
                    .execute()
                    .handleResponse { it.code to it.entity?.let(EntityUtils::toString) }

            val message = "Fikk HTTP $responseCode i svar fra Gosys"
            if (responseCode !in listOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT)) {
                loggError(message, "Response body" to responseBody)
                error(message)
            } else {
                loggInfo(message, "Response body" to responseBody)
            }
        }
    }
}
