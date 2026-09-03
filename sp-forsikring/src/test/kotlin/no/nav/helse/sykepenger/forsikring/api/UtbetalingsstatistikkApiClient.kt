package no.nav.helse.sykepenger.forsikring.api

import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.time.LocalDate

object UtbetalingsstatistikkApiClient {
    fun getUtbetalingsstatistiskk(
        baseUrl: String,
        fom: LocalDate,
        tom: LocalDate,
        token: String?,
    ): Pair<Int, String> =
        Request
            .get("$baseUrl/api/utbetalinger/utbetaltesummer?fom=$fom&tom=$tom")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
