package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.util.UUID.fromString
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class SykepengeforsikringRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid = TestRapid().apply {
        SykepengeforsikringRiver(
            rapidsConnection = this,
            infotrygdForsikringDao = ReplikabaseForsikringDao(TestcontainersReplikadatabase.dataSource)
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.clear()
        rapid.reset()
    }

    @Test
    fun `Sender melding i det hele tatt`() {
        rapid.sendTestMessage(testmelding("01020312345", LocalDate.parse("2026-01-01")))

        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `Sender melding som er lik som den vi fikk inn som behov`() {
        val testmelding = testmelding("01020312345", LocalDate.parse("2026-01-01"))
        rapid.sendTestMessage(testmelding)

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter + "@løsning"
        )
    }

    @Test
    fun `løsning har en oppslagId med forventet format`() {
        rapid.sendTestMessage(testmelding("01020312345", LocalDate.parse("2026-01-01")))

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        val oppslagId = løsningMelding["@løsning"]["Sykepengeforsikring"]["oppslagId"]?.asText()
        assertNotNull(oppslagId) { "Manglet oppslagId" }
        assertDoesNotThrow("oppslagId \"${oppslagId}\" kunne ikke tolkes som en UUID") {
            fromString(oppslagId)
        }
    }

    @Test
    fun `løsning er INGEN_FORSIKRING når det ikke finnes noen forsikring`() {
        rapid.sendTestMessage(testmelding("01020312345", LocalDate.parse("2026-01-01")))

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertEquals("INGEN_FORSIKRING", løsningMelding["@løsning"]["Sykepengeforsikring"]["konklusjon"]?.asText())
    }

    private fun testmelding(fødselsnummer: String, skjæringstidspunkt: LocalDate) = """
        {
            "@behov": ["Sykepengeforsikring"],
            "fødselsnummer": "$fødselsnummer",
            "Sykepengeforsikring" : {
                "skjæringstidspunkt": "$skjæringstidspunkt"
            }
        }
    """.trimIndent()

    private val generiskeFelter = listOf(
        "@id",
        "@opprettet",
        "system_read_count",
        "system_participating_services",
        "@forårsaket_av"
    )

    private fun assertJsonEquals(
        expectedJson: String,
        actualJsonNode: JsonNode,
        bortsettFraProperties: List<String> = emptyList()
    ) {
        val expected = objectMapper.readTree(expectedJson).deepSortedObjectNodeCopy()
            .apply { bortsettFraProperties.forEach { remove(it) } }
        val actual = actualJsonNode.deepSortedObjectNodeCopy()
            .apply { bortsettFraProperties.forEach { remove(it) } }
        assertEquals(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual)
        )
    }

    private fun JsonNode.sortedDeep(): JsonNode =
        when (this) {
            is ObjectNode ->
                objectMapper.createObjectNode().also { sorted ->
                    properties().asSequence()
                        .sortedBy { (name, _) -> name }
                        .forEach { (name, value) -> sorted.set<JsonNode>(name, value.sortedDeep()) }
                }
            is ArrayNode ->
                objectMapper.createArrayNode().also { sortedArray ->
                    forEach { sortedArray.add(it.sortedDeep()) }
                }
            else -> this.deepCopy()
        }

    private fun JsonNode.deepSortedObjectNodeCopy(): ObjectNode = sortedDeep() as ObjectNode
}
