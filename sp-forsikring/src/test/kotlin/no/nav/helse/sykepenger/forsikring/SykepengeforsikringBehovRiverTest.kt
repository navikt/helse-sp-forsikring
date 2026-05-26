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
import no.nav.helse.sykepenger.forsikring.oppslag.NavKjøptForsikring
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
                    "spesielleYrkesgrupper": [],
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
                        "spesielleYrkesgrupper": [],
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

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2} -> {3} fra dag {4}", quoteTextArguments = false)
    @CsvSource(
        "SELVSTENDIG, , 1, 80, 1",
        "SELVSTENDIG, , 2, 100, 17",
        "SELVSTENDIG, , 3, 100, 1",
        "SELVSTENDIG, JORDBRUKER, 4, 100, 1",
        "SELVSTENDIG, REINDRIFTER, 4, 100, 1",
        "FRILANS, , 5, 100, 1",
        // Kollektive forsikringer
        "SELVSTENDIG, JORDBRUKER, , 100, 17",
        "SELVSTENDIG, REINDRIFTER, , 100, 17",
        "ARBEIDSTAKER, FISKER_BLAD_B, , 100, 1",
        "SELVSTENDIG, FISKER_BLAD_B, , 100, 1",
    )
    fun `gir løsning med forsikring`(yrkesaktivitetstype: String, særskiltGruppe: String?, IF10_TYPE: Char?, grad: Int, fraDag: Int) {
        IF10_TYPE?.let { TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [ ${særskiltGruppe?.let { "\"$it\"" }.orEmpty()} ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": $grad, "fraDag": $fraDag } } """)
    }

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2}", quoteTextArguments = false)
    @CsvSource(
        "ARBEIDSTAKER, , ",
        "SELVSTENDIG, , ",
        "FRILANS, , ",
    )
    fun `gir løsning uten forsikring`(yrkesaktivitetstype: String, særskiltGruppe: String?, IF10_TYPE: Char?) {
        IF10_TYPE?.let { TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [ ${særskiltGruppe?.let { "\"$it\"" }.orEmpty()} ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": false } """)
    }

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2}", quoteTextArguments = false)
    @CsvSource(
        "ARBEIDSTAKER, , 1",
        "ARBEIDSTAKER, , 2",
        "ARBEIDSTAKER, , 3",
        "ARBEIDSTAKER, , 4",
        "ARBEIDSTAKER, , 5",
        "SELVSTENDIG, , 4",
        "FRILANS, , 1",
        "FRILANS, , 2",
        "FRILANS, , 3",
        "FRILANS, , 4"
    )
    fun `feiler ved ugyldig kombinasjon`(yrkesaktivitetstype: String, særskiltGruppe: String?, IF10_TYPE: Char?) {
        IF10_TYPE?.let { TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }

        assertThrows<NavKjøptForsikring.Valideringsfeil> {
            rapid.sendTestMessage(
                """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [ ${særskiltGruppe?.let { "\"$it\"" }.orEmpty()} ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
            )
        }
    }

    @Test
    fun `velger dekning med lavest fraDag når det finnes flere dekninger med samme grad`() {
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2') // grad=100, fraDag=17
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '3') // grad=100, fraDag=1

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 1 } }""")
    }

    @Test
    fun `eliminerer forsikring hvor skjæringstidspunkt er etter tom`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20251231
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": false }""")
    }

    @Test
    fun `beholder forsikring hvor skjæringstidspunkt er lik tom`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20260101
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } }""")
    }

    @Test
    fun `beholder forsikring hvor skjæringstidspunkt er før tom`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20260102
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } }""")
    }

    @Test
    fun `beholder forsikring uten tom`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 0
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } }""")
    }

    @Test
    fun `eliminerer forsikring hvor virkningsdato er etter skjæringstidspunkt`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260102
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": false }""")
    }

    @Test
    fun `beholder forsikring hvor virkningsdato er lik skjæringstidspunkt`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } }""")
    }

    @Test
    fun `beholder forsikring hvor virkningsdato er før skjæringstidspunkt`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20251231
        )

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        forventLøsning("""{ "harForsikring": true, "dekning": { "grad": 100, "fraDag": 17 } }""")
    }

    @Test
    fun `feiler når dekninger har ulike grader`() {
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '1') // grad=80, fraDag=1
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2') // grad=100, fraDag=17

        assertThrows<IllegalStateException> {
            rapid.sendTestMessage(
                """
                {
                    "@behov": [ "Sykepengeforsikring" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Sykepengeforsikring" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
            )
        }

        assertEquals(0, rapid.inspektør.size)
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
            IF10_TYPE = '2'
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF10_TYPE = '3'
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
                        "spesielleYrkesgrupper": [],
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
