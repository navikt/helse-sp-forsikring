package no.nav.helse.sykepenger.forsikring

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.time.LocalDate
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SykepengeforsikringRiverTest {
    private var mocketResultatSupplier: () -> SykepengeforsikringResultat? = { null }
    private val sykepengeforsikringDao = object : InfotrygdForsikringDao {
        override fun hentFullstendigeForsikringer(fødselsnummer: String): List<InfotrygdForsikringDao.RåForsikringDto> {
            TODO("Not yet implemented")
        }
    }

    private val rapid =
        TestRapid()
            .apply {
                SykepengeforsikringRiver(this, ReplikabaseForsikringDao(TestcontainersDatabase.dataSource))
            }

    @BeforeEach
    fun reset() {
        mocketResultatSupplier = { null }
        rapid.reset()
    }

    @Test
    fun `publiserer løsning når sykepengeforsikring finnes`() {
        mocketResultatSupplier = { SykepengeforsikringResultat(forsikret = true) }

        rapid.sendTestMessage(behov)

        assertEquals(1, rapid.inspektør.size)
        assertEquals(true, rapid.inspektør.message(0)["@løsning"]["Sykepengeforsikring"]["forsikret"].booleanValue())
    }

    @Test
    fun `publiserer ikke løsning ved feil`() {
        mocketResultatSupplier = { error("Tjeneste utilgjengelig") }

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
