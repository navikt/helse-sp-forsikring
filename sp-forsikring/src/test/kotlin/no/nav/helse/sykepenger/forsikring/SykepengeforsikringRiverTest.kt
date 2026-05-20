package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SykepengeforsikringRiverTest {
    private val sykepengeforsikringService = mockk<SykepengeforsikringService>()

    private val rapid =
        TestRapid()
            .apply {
                SykepengeforsikringRiver(this, sykepengeforsikringService)
            }

    @BeforeEach
    fun reset() {
        clearAllMocks()
        rapid.reset()
    }

    @Test
    fun `publiserer løsning når sykepengeforsikring finnes`() {
        every { sykepengeforsikringService.hentSykepengeforsikring(any(), any()) } returns SykepengeforsikringResultat(forsikret = true)

        rapid.sendTestMessage(behov)

        assertEquals(1, rapid.inspektør.size)
        assertEquals(true, rapid.inspektør.message(0)["@løsning"]["Sykepengeforsikring"]["forsikret"].booleanValue())
    }

    @Test
    fun `publiserer ikke løsning ved feil`() {
        every { sykepengeforsikringService.hentSykepengeforsikring(any(), any()) } throws RuntimeException("Tjeneste utilgjengelig")

        rapid.sendTestMessage(behov)

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer melding som allerede har løsning`() {
        rapid.sendTestMessage(behovMedLøsning)

        assertEquals(0, rapid.inspektør.size)
    }

    @Language("JSON")
    val behov = """
        {
          "@event_name": "behov",
          "@behov": ["Sykepengeforsikring"],
          "@id": "2dad52b1-f58e-4c26-bb24-970705cdea67",
          "@opprettet": "2020-05-05T11:16:12.678539",
          "hendelseId": "c2d3ce2e-abeb-4c27-a7d3-e45f23ef26f7",
          "fødselsnummer": "12345678901",
          "orgnummer": "987654321"
        }
    """

    @Language("JSON")
    val behovMedLøsning = """
        {
          "@event_name": "behov",
          "@behov": ["Sykepengeforsikring"],
          "@løsning": {},
          "@id": "2dad52b1-f58e-4c26-bb24-970705cdea67",
          "@opprettet": "2020-05-05T11:16:12.678539",
          "hendelseId": "c2d3ce2e-abeb-4c27-a7d3-e45f23ef26f7",
          "fødselsnummer": "12345678901"
        }
    """
}
