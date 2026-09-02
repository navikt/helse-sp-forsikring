package no.nav.helse.sykepenger.forsikring.api

import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.io.entity.EntityUtils

object SpesialistApiClient {
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
}
