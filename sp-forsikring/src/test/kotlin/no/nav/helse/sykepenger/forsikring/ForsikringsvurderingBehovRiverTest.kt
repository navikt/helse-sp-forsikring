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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ForsikringsvurderingBehovRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid = TestRapid().apply {
        val forsikringsvurderingRepository = no.nav.helse.sykepenger.forsikring.forsikringsvurdering.PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource)
        val replikabaseDao = no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao(TestcontainersReplikadatabase.dataSource)
        val oppslagService = no.nav.helse.sykepenger.forsikring.oppslag.OppslagService(replikabaseDao)
        val forsikringsvurderingService = no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)
        ForsikringsvurderingBehovRiver(
            rapidsConnection = this,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
            forsikringsvurderingService = forsikringsvurderingService,
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
        val testmelding = """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "Forsikringsvurdering" : {
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
    fun `løsning har en forsikringsvurderingId som er en UUID`() {
        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Forsikringsvurdering" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Forsikringsvurdering" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        val forsikringsvurderingId = løsningMelding["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"]?.asText()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId" }
        assertDoesNotThrow("forsikringsvurderingId \"${forsikringsvurderingId}\" kunne ikke tolkes som en UUID") {
            UUID.fromString(forsikringsvurderingId)
        }
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
    fun `Når vurderingen feiler så sendes det ikke ut noe svar`(yrkesaktivitetstype: String, særskiltGruppe: String?, IF10_TYPE: Char?) {
        IF10_TYPE?.let { insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }
        val antallOppslagFør = TestcontainersSpForsikringDatabase.countAlleOppslag()
        val antallForsikringsvurderingerFør = TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger()

        assertDoesNotThrow {
            rapid.sendTestMessage(
                """
                {
                    "@behov": [ "Forsikringsvurdering" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "Forsikringsvurdering" : {
                        "spesielleYrkesgrupper": [ ${særskiltGruppe?.let { "\"$it\"" }.orEmpty()} ],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
            )
        }

        assertEquals(0, rapid.inspektør.size)
        assertEquals(antallOppslagFør, TestcontainersSpForsikringDatabase.countAlleOppslag())
        assertEquals(antallForsikringsvurderingerFør, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
    }

    @Test
    fun `feiler når dekninger har ulike grader`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '1') // grad=80, fraDag=1
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2') // grad=100, fraDag=17
        val antallOppslagFør = TestcontainersSpForsikringDatabase.countAlleOppslag()
        val antallForsikringsvurderingerFør = TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger()

        assertDoesNotThrow {
            rapid.sendTestMessage(
                """
                {
                    "@behov": [ "Forsikringsvurdering" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Forsikringsvurdering" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
            )
        }

        assertEquals(0, rapid.inspektør.size)
        assertEquals(antallOppslagFør, TestcontainersSpForsikringDatabase.countAlleOppslag())
        assertEquals(antallForsikringsvurderingerFør, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
    }

    @Test
    fun `forsikringsvurdering og oppslag lagres ned i databasen`() {
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
                    "@behov": [ "Forsikringsvurdering" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Forsikringsvurdering" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        val forsikringsvurderingId = løsningMelding["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"]?.asText()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId" }
        assertEquals(1, TestcontainersSpForsikringDatabase.countOppslag(forsikringsvurderingId))
        assertEquals(2, TestcontainersSpForsikringDatabase.countOppslagIF_VEDFRIVT_10(forsikringsvurderingId))
        assertEquals(4, TestcontainersSpForsikringDatabase.countOppslagIF_FKONTO_12(forsikringsvurderingId))
    }

    @Test
    fun `ekskluderingsårsaker lagres for forsikringer som ikke er kandidater`() {
        // seq=1: virkningsdato etter skjæringstidspunkt
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2', IF10_VIRKDATO = 20260102)
        TestcontainersReplikadatabase.insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF12_BETDATO_SEQ = 1, IF12_BETDATO = 20260101)
        // seq=2: opphørt på skjæringstidspunkt
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2', IF10_FORSTOM = 20251231)
        TestcontainersReplikadatabase.insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF12_BETDATO_SEQ = 1, IF12_BETDATO = 20260101)
        // seq=3: aldri betalt
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 3, IF10_TYPE = '2')
        // seq=4: virkningsdato lenge etter skjæringstidspunkt
        TestcontainersReplikadatabase.insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 4, IF10_TYPE = '2', IF10_VIRKDATO = 20260601)
        TestcontainersReplikadatabase.insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 4, IF12_BETDATO_SEQ = 1, IF12_BETDATO = 20260101)

        rapid.sendTestMessage(
            """
                {
                    "@behov": [ "Forsikringsvurdering" ],
                    "fødselsnummer": "01020312345",
                    "yrkesaktivitetstype": "SELVSTENDIG",
                    "Forsikringsvurdering" : {
                        "spesielleYrkesgrupper": [],
                        "skjæringstidspunkt": "2026-01-01"
                    }
                }
            """.trimIndent()
        )

        assertEquals(1, rapid.inspektør.size)
        val forsikringsvurderingId = rapid.inspektør.message(0)["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asText()
        val ekskluderinger = TestcontainersSpForsikringDatabase.hentEkskluderinger(UUID.fromString(forsikringsvurderingId))
        assertEquals(4, ekskluderinger.size)
        assertEquals("SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO", ekskluderinger[1])
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskluderinger[2])
        assertEquals("ALDRI_BETALT", ekskluderinger[3])
        assertEquals("SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO", ekskluderinger[4])
    }

    private fun insertBetaltVedfrivt(
        IF01_AGNR_FNR: Long,
        IF10_FORSFOM_SEQ: Int = 0,
        IF10_TYPE: Char = '1',
        IF10_FORSFOM: Int = 0,
        IF10_VIRKDATO: Int = 20260101,
        IF10_FORSTOM: Int = 0,
        IF10_GODKJ: Char = 'J'
    ) {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_TYPE = IF10_TYPE,
            IF10_FORSFOM = IF10_FORSFOM,
            IF10_VIRKDATO = IF10_VIRKDATO,
            IF10_FORSTOM = IF10_FORSTOM,
            IF10_GODKJ = IF10_GODKJ,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
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
