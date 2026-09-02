package no.nav.helse.sykepenger.forsikring.api

import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils

object FlexApiClient {
    fun postForsikringsvurdering(
        baseUrl: String,
        identitetsnummer: String,
        yrkesaktivitetstype: String,
        spesielleYrkesgrupper: Set<String>,
        skjæringstidspunkt: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .post("$baseUrl/api/forsikringsvurdering")
            .bodyString(
                """
                {
                    "identitetsnummer": "$identitetsnummer",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "spesielleYrkesgrupper": [ ${spesielleYrkesgrupper.joinToString(",") { "\"$it\"" }} ],
                    "skjæringstidspunkt": "$skjæringstidspunkt"
                }
                """.trimIndent(),
                ContentType.APPLICATION_JSON,
            ).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
