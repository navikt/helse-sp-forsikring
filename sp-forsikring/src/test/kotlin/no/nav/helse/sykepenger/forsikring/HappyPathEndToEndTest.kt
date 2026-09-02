package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.test.assertJsonEquals
import com.github.navikt.tbd_libs.testdata.TestPerson
import no.nav.helse.sykepenger.forsikring.e2e.E2ETestApplication
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.time.LocalDate

@Isolated
class HappyPathEndToEndTest {
    private val skjæringstidspunkt = LocalDate.parse("2026-09-01")
    private val person = TestPerson()

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            E2ETestApplication.start()
        }
    }

    @Test
    fun `går an å kalle flex-API'et`() {
        val flexForsikringsvurdering = postFlexForsikringsvurdering()
        assertJsonEquals(
            expectedJson = """{ "harForsikringMedDekningIVentetid": false }""",
            actualJson = flexForsikringsvurdering,
        )
    }

    private fun postFlexForsikringsvurdering(): String =
        Request
            .post("${E2ETestApplication.baseUrl}/api/forsikringsvurdering")
            .addHeader("Authorization", "Bearer ${flexToken()}")
            .bodyString(
                """
                {
                  "identitetsnummer": "${person.identitetsnummer}",
                  "yrkesaktivitetstype": "SELVSTENDIG",
                  "spesielleYrkesgrupper": [],
                  "skjæringstidspunkt": "$skjæringstidspunkt"
                }
                """.trimIndent(),
                ContentType.APPLICATION_JSON,
            ).execute()
            .handleResponse { response ->
                val body = EntityUtils.toString(response.entity)
                assertEquals(200, response.code, "Body var: $body")
                body
            }

    private fun flexToken(): String =
        E2ETestApplication.mockOAuth2Server
            .issueToken(issuerId = "default", audience = "sp-forsikring-e2e", claims = mapOf("idtyp" to "app"))
            .serialize()
}
