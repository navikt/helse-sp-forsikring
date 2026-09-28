package no.nav.helse.sykepenger.forsikring.api

import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.LocalDate

object EndringssjekkApiClient {
    private val objectMapper =
        jacksonMapperBuilder()
            .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
            .build()

    fun postEndringssjekk(
        baseUrl: String,
        identitetsnummer: String,
        skjæringstidspunkt: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .post("$baseUrl/endringssjekk")
            .bodyString(
                objectMapper.writeValueAsString(
                    EndringssjekkRequest(
                        identitetsnummer = identitetsnummer,
                        skjæringstidspunkt = LocalDate.parse(skjæringstidspunkt),
                    ),
                ),
                ContentType.APPLICATION_JSON,
            ).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
