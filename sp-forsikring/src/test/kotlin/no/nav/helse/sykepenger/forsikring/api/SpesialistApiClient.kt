package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.time.LocalDate
import java.util.*

object SpesialistApiClient {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

    fun getForsikringsvurdering(
        baseUrl: String,
        forsikringsvurderingId: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .get("$baseUrl/forsikringsvurderinger/$forsikringsvurderingId")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }

    fun postRevurdering(
        baseUrl: String,
        identitetsnummer: String,
        skjæringstidspunkt: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .post("$baseUrl/revurdering")
            .bodyString(
                objectMapper.writeValueAsString(
                    RevurderingRequest(
                        identitetsnummer = identitetsnummer,
                        skjæringstidspunkt = LocalDate.parse(skjæringstidspunkt),
                        vedtaksperiodeId = UUID.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                    ),
                ),
                ContentType.APPLICATION_JSON,
            ).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
