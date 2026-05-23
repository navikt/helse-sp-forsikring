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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class SykepengeforsikringBehovRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid = TestRapid().apply {
        SykepengeforsikringBehovRiver(
            rapidsConnection = this,
            replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
        rapid.reset()
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun shutdown() {
            TestcontainersReplikadatabase.shutdown()
            TestcontainersSpForsikringDatabase.shutdown()
        }
    }

    @Test
    fun `løsningmelding er lik behovsmeldingen, sett bort fra løsning-feltet og rapids and rivers-genererte felter`() {
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

        forventLøsning("""{ "harForsikring": false } """)
    }

    @Test
    fun `selvstendig næringsdrivende med forsikring med 80 prosent fra dag 1 fungerer`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 80, "fraDag": 1 } } """)
    }

    @Test
    fun `frilanser med forsikring for selvstendig næringsdrivende med 80 prosent fra dag 1 gir ingen forsikring`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '1'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "FRILANSER",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": false } """)
    }

    @Test
    fun `selvstendig næringsdrivende med forsikring med 100 prosent fra dag 17 fungerer`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """)
    }

    @Test
    fun `selvstendig næringsdrivende med forsikring med 100 prosent fra dag 1 fungerer`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """)
    }

    @Test
    fun `frilanser med forsikring med 100 prosent fra dag 1 fungerer`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '5'
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "FRILANSER",
                    "Sykepengeforsikring" : {
                        "særskilteGrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """)
    }

    @Test
    fun `selvstendig næringsdrivende med forsikring for frilanser gir ingen forsikring`() {
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

        forventLøsning("""{ "harForsikring": false } """)
    }

    @Test
    fun `jordbruker har kollektiv forsikring når hen ikke har en nav-kjøpt forsikring`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """)
    }

    @Test
    fun `jordbruker med tilleggsforsikring med 100 prosent fra dag 1 fungerer`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """)
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
    fun `reindrifter har kollektiv forsikring når hen ikke har en nav-kjøpt forsikring`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } } """)
    }

    @Test
    fun `reindrifter med tilleggsforsikring med 100 prosent fra dag 1 fungerer`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """)
    }

    @Test
    fun `fisker på blad B har kollektiv forsikring når hen ikke har en nav-kjøpt forsikring`() {
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

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } } """)
    }

    private fun forventLøsning(forventetLøsningUtenOppslagId: String) {
        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = forventetLøsningUtenOppslagId,
            actualJsonNode = løsningMelding["@løsning"]["Sykepengeforsikring"],
            bortsettFraProperties = setOf("oppslagId")
        )
    }

    @Test
    fun `oppslag og grunnlagsdata lagres ned i databasen`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF10_TYPE = '1'
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF10_TYPE = '2'
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF12_BETDATO_SEQ = 111
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF12_BETDATO_SEQ = 222
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF12_BETDATO_SEQ = 333
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF12_BETDATO_SEQ = 444
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
        val oppslagId = løsningMelding["@løsning"]["Sykepengeforsikring"]["oppslagId"]?.asText()
        assertNotNull(oppslagId) { "Manglet oppslagId" }
        assertEquals(1, TestcontainersSpForsikringDatabase.countOppslag(oppslagId))
        assertEquals(2, TestcontainersSpForsikringDatabase.countOppslagIF_VEDFRIVT_10(oppslagId))
        assertEquals(4, TestcontainersSpForsikringDatabase.countOppslagIF_FKONTO_12(oppslagId))
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
