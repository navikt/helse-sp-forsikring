package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class SykepengeforsikringBehovRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid = TestRapid().apply {
        SykepengeforsikringBehovRiver(
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
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `Sender melding som er lik som den vi fikk inn som behov, sett bort fra løsning-feltet`() {
        val testmelding = """
            {
                "@behov": [ "Sykepengeforsikring" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "Sykepengeforsikring" : {
                    "særskilteGrupper": [],
                    "skjæringstidspunkt": "2026-01-01"
                }
            }
        """.trimIndent()

        rapid.sendTestMessage(testmelding)

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter + "@løsning"
        )
    }

    @Test
    fun `løsning har en oppslagId som er en UUID`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        val oppslagId = løsningMelding["@løsning"]["Sykepengeforsikring"]["oppslagId"]?.asText()
        assertNotNull(oppslagId) { "Manglet oppslagId" }
        assertDoesNotThrow("oppslagId \"${oppslagId}\" kunne ikke tolkes som en UUID") {
            UUID.fromString(oppslagId)
        }
    }

    @Test
    fun `løsning når det ikke finnes noen forsikring er uten forsikring`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": false } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning når det finnes en gyldig forsikring med 80 prosent fra dag 1 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '1'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 80, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning når det finnes en gyldig forsikring med 100 prosent fra dag 17 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning når det finnes en gyldig forsikring med 100 prosent fra dag 1 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '3'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning for jordbruker med gyldig tilleggsforsikring med 100 prosent fra dag 1 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '4'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [ "JORDBRUKER" ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `stopper med feil for ikke-jordbruker dersom bruker har tilleggsforsikring for jordbruker`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '4'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `løsning for frilanser med gyldig forsikring med 100 prosent fra dag 1 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '5'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "${
                "FRILANSER"
            }",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `stopper med feil for ikke-frilanser dersom bruker har forsikring for frilanser`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '5'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `løsning for jordbruker som bare har kollektiv forsikring gir riktig informasjon`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [ "JORDBRUKER" ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning for reindrifter med gyldig tilleggsforsikring med 100 prosent fra dag 1 inneholder riktig informasjon`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '4'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [ "REINDRIFTER" ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning for reindrifter som bare har kollektiv forsikring gir riktig informasjon`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [ "REINDRIFTER" ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

    @Test
    fun `løsning for fiskere på blad B gir riktig informasjon`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [ "FISKER_BLAD_B" ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = """{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = listOf("oppslagId")
        )
    }

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
