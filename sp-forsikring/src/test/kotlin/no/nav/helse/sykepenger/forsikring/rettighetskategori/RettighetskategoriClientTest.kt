package no.nav.helse.sykepenger.forsikring.rettighetskategori

import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import kotlin.test.assertEquals

class RettighetskategoriClientTest {
    private val wiremock = RettighetskategoriWiremock()
    private val client = wiremock.client

    @BeforeEach
    fun beforeEach() {
        wiremock.reset()
    }

    @Test
    fun `henter rettighetskategorien med ID i stien, fødselsnummer i bodyen og maskintoken`() {
        val rettighetskategoriId = UUID.randomUUID()
        val fødselsnummer = lagIdentitetsnummer()
        wiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = fødselsnummer,
            hovedkategori = "SELVSTENDIG_NAERINGSDRIVENDE",
            tilleggskategorier = listOf("JORDBRUKER"),
        )

        val rettighetskategori = client.hent(rettighetskategoriId, Identitetsnummer.fraString(fødselsnummer))

        assertEquals(
            Rettighetskategori(
                hovedkategori = Rettighetskategori.Hovedkategori.SELVSTENDIG_NAERINGSDRIVENDE,
                tilleggskategorier = setOf(Rettighetskategori.Tilleggskategori.JORDBRUKER),
            ),
            rettighetskategori,
        )
        val request = wiremock.loggedRequests().single()
        assertEquals("Bearer ${RettighetskategoriWiremock.ACCESS_TOKEN}", request.getHeader("Authorization"))
        assertJsonEquals(expectedJson = """{ "fødselsnummer": "$fødselsnummer" }""", actualJson = request.bodyAsString)
    }

    @Test
    fun `ignorerer tilleggskategorier vi ikke kjenner`() {
        val rettighetskategoriId = UUID.randomUUID()
        val fødselsnummer = lagIdentitetsnummer()
        wiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = fødselsnummer,
            hovedkategori = "SELVSTENDIG_NAERINGSDRIVENDE",
            tilleggskategorier = listOf("REINDRIFTER", "JORDBRUKER"),
        )

        val rettighetskategori = client.hent(rettighetskategoriId, Identitetsnummer.fraString(fødselsnummer))

        assertEquals(setOf(Rettighetskategori.Tilleggskategori.JORDBRUKER), rettighetskategori.tilleggskategorier)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 404, 500, 503])
    fun `kaster exception når sp-rettighetskategori svarer med feil`(status: Int) {
        val rettighetskategoriId = UUID.randomUUID()
        wiremock.stubFeil(rettighetskategoriId, status)

        assertThrows<IllegalStateException> {
            client.hent(rettighetskategoriId, Identitetsnummer.fraString(lagIdentitetsnummer()))
        }
    }

    @Test
    fun `kaster exception når hovedkategorien er ukjent`() {
        val rettighetskategoriId = UUID.randomUUID()
        val fødselsnummer = lagIdentitetsnummer()
        wiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = fødselsnummer,
            hovedkategori = "FRILANSER",
            tilleggskategorier = emptyList(),
        )

        assertThrows<Exception> {
            client.hent(rettighetskategoriId, Identitetsnummer.fraString(fødselsnummer))
        }
    }
}
