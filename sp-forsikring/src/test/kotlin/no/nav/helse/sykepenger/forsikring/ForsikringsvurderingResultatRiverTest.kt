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

internal class ForsikringsvurderingResultatRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid = TestRapid().apply {
        ForsikringsvurderingBehovRiver(
            rapidsConnection = this,
            replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource
        )
        ForsikringsvurderingResultatRiver(
            rapidsConnection = this,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
        rapid.reset()
    }

    @Test
    fun `løsningmelding er lik behovsmeldingen, sett bort fra løsning-feltet og rapids and rivers-genererte felter`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        val testmelding = """
            {
                "@behov": [ "ForsikringsvurderingResultat" ],
                "ForsikringsvurderingResultat": {
                    "forsikringsvurderingId": "$forsikringsvurderingId"
                }
            }
        """.trimIndent()

        rapid.sendTestMessage(testmelding)

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = setOf(
                "@løsning",
                "@id",
                "@opprettet",
                "system_read_count",
                "system_participating_services",
                "@forårsaket_av"
            )
        )
    }

    @Test
    fun `returnerer løsning med forsikring basert på forsikringsvurderingId`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101
        )

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        rapid.sendTestMessage(behovMelding(forsikringsvurderingId))

        forventLøsning("""{ "harForsikring": true, "dekning": { "iVentetid": false, "grad": 100 } }""")
    }

    @Test
    fun `returnerer løsning uten forsikring basert på forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        rapid.sendTestMessage(behovMelding(forsikringsvurderingId))

        forventLøsning("""{ "harForsikring": false, "dekning": null }""")
    }

    @Test
    fun `løsning inneholder forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        rapid.sendTestMessage(behovMelding(forsikringsvurderingId))

        assertEquals(1, rapid.inspektør.size)
        val returnertId = rapid.inspektør.message(0)["@løsning"]["ForsikringsvurderingResultat"]["forsikringsvurderingId"]?.asText()
        assertNotNull(returnertId) { "Manglet forsikringsvurderingId i løsning" }
        assertEquals(forsikringsvurderingId, returnertId)
    }

    @Test
    fun `sender ikke svar hvis forsikringsvurderingId ikke finnes`() {
        val ukjentId = UUID.randomUUID().toString()

        assertDoesNotThrow {
            rapid.sendTestMessage(behovMelding(ukjentId))
        }

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer melding som allerede har løsning`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        rapid.sendTestMessage(
            """
            {
                "@behov": [ "ForsikringsvurderingResultat" ],
                "@løsning": {},
                "ForsikringsvurderingResultat": {
                    "forsikringsvurderingId": "$forsikringsvurderingId"
                }
            }
            """.trimIndent()
        )

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ignorerer melding med annet behov`() {
        rapid.sendTestMessage(
            """
            {
                "@behov": [ "AnnetBehov" ],
                "AnnetBehov": {
                    "forsikringsvurderingId": "${UUID.randomUUID()}"
                }
            }
            """.trimIndent()
        )

        assertEquals(0, rapid.inspektør.size)
    }

    private fun opprettVurdering(yrkesaktivitetstype: String): String {
        rapid.sendTestMessage(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "$yrkesaktivitetstype",
                "Forsikringsvurdering": {
                    "spesielleYrkesgrupper": [],
                    "skjæringstidspunkt": "2026-01-01"
                }
            }
            """.trimIndent()
        )

        val løsning = rapid.inspektør.message(rapid.inspektør.size - 1)
        val id = løsning["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asText()
        rapid.reset()
        return id
    }

    private fun behovMelding(forsikringsvurderingId: String) = """
        {
            "@behov": [ "ForsikringsvurderingResultat" ],
            "ForsikringsvurderingResultat": {
                "forsikringsvurderingId": "$forsikringsvurderingId"
            }
        }
    """.trimIndent()

    private fun forventLøsning(forventetLøsningUtenOppslagId: String) {
        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = forventetLøsningUtenOppslagId,
            actualJsonNode = løsningMelding["@løsning"]["ForsikringsvurderingResultat"],
            bortsettFraProperties = setOf("forsikringsvurderingId")
        )
    }

    private fun assertJsonEquals(
        expectedJson: String,
        actualJsonNode: JsonNode,
        bortsettFraProperties: Set<String> = emptySet()
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
