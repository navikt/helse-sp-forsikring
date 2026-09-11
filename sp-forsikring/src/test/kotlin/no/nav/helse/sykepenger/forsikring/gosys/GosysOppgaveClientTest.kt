package no.nav.helse.sykepenger.forsikring.gosys

import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GosysOppgaveClientTest {
    private val gosysWiremock = GosysWiremock()
    private val client = gosysWiremock.oppgaveClient

    @BeforeEach
    fun beforeEach() {
        gosysWiremock.reset()
    }

    @Test
    fun `utfører kall med riktig payload`() {
        // Given:
        val identitetsnummer = lagIdentitetsnummer()
        val uuid = UUID.randomUUID().toString()

        // When:
        client.opprettOppgave(
            personident = identitetsnummer,
            uuid = uuid,
            beskrivelse = "Dette er en test-tekst.",
        )

        // Then:
        val gosysRequests = gosysWiremock.loggedPostOppgaverRequests()
        assertEquals(1, gosysRequests.size)

        val gosysRequest = gosysRequests.single()
        assertTrue(gosysRequest.url.startsWith("/api/v1/oppgaver"))
        assertTrue(gosysRequest.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertEquals("Bearer ${GosysWiremock.ACCESS_TOKEN}", gosysRequest.getHeader("Authorization"))
        // Kaster hvis headeren mangler eller ikke er en gyldig UUID
        UUID.fromString(gosysRequest.getHeader("X-Correlation-ID"))
        assertJsonEquals(
            expectedJson =
                """
                {
                  "personident": "$identitetsnummer",
                  "uuid": "$uuid",
                  "aktivDato": "${LocalDate.now()}",
                  "prioritet": "NORM",
                  "oppgavetype": "VURD_HENV",
                  "tema": "FOS",
                  "behandlingstype": "ae0221",
                  "beskrivelse": "Dette er en test-tekst."
                }
                """.trimIndent(),
            actualJson = gosysRequest.bodyAsString,
        )
    }

    @Test
    fun `håndterer Conflict status kode`() {
        // Given:
        gosysWiremock.stubOppgaverRespons(409)

        // Then:
        assertDoesNotThrow {
            // When:
            client.opprettOppgave(
                personident = lagIdentitetsnummer(),
                uuid = UUID.randomUUID().toString(),
                beskrivelse = "Dette er en test-tekst.",
            )
        }
    }

    @Test
    fun `kaster exception når Gosys svarer med feil`() {
        // Given:
        gosysWiremock.stubOppgaverRespons(500)

        // Then:
        assertThrows<IllegalStateException> {
            // When:
            client.opprettOppgave(
                personident = lagIdentitetsnummer(),
                uuid = UUID.randomUUID().toString(),
                beskrivelse = "Dette er en test-tekst.",
            )
        }
    }
}
