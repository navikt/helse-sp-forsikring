package no.nav.helse.sykepenger.forsikring.rettighetskategori

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.util.Timeout
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.*

class RettighetskategoriClient(
    private val baseUrl: String,
    private val tokenClient: AccessTokenProvider,
    private val scope: String,
) {
    private val objectMapper = jacksonObjectMapper()

    fun hent(
        rettighetskategoriId: UUID,
        identitetsnummer: Identitetsnummer,
    ): Rettighetskategori {
        val url = "$baseUrl/api/rettighetskategorier/$rettighetskategoriId"
        val requestBody = objectMapper.writeValueAsString(mapOf("fødselsnummer" to identitetsnummer.value))
        loggInfo("Gjør HTTP POST $url")

        val bearerToken = tokenClient.machineToken(scope)
        val (responseCode, responseBody) =
            Request
                .post(url)
                .connectTimeout(Timeout.ofSeconds(10))
                .responseTimeout(Timeout.ofSeconds(30))
                .setHeader("Accept", ContentType.APPLICATION_JSON.mimeType)
                .setHeader("Authorization", "Bearer $bearerToken")
                .bodyString(requestBody, ContentType.APPLICATION_JSON)
                .execute()
                .handleResponse { it.code to it.entity?.let(EntityUtils::toString) }

        val message = "Fikk HTTP $responseCode i svar fra sp-rettighetskategori"
        if (responseCode != HttpStatus.SC_OK || responseBody == null) {
            loggError(message, "Response body" to responseBody)
            error(message)
        }
        loggInfo(message)

        val respons = objectMapper.readValue<RettighetskategoriRespons>(responseBody)
        return Rettighetskategori(
            hovedkategori = Rettighetskategori.Hovedkategori.valueOf(respons.hovedkategori),
            tilleggskategorier =
                respons.tilleggskategorier
                    .mapNotNull { navn -> Rettighetskategori.Tilleggskategori.entries.find { it.name == navn } }
                    .toSet(),
        )
    }

    private data class RettighetskategoriRespons(
        val hovedkategori: String,
        val tilleggskategorier: List<String>,
    )
}
