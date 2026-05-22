package no.nav.helse.sykepenger.forsikring

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InfotrygdSykepengeforsikringerRiverTest {
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
    fun `Tomt svar når det ikke finnes noen forsikring`() {
        rapid.sendTestMessage(testmelding("01020312345"))

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = """
                {
                    "@behov": ["InfotrygdSykepengeforsikringer"],
                    "fødselsnummer": "01020312345",
                    "@løsning": {
                        "InfotrygdSykepengeforsikringer": []
                    }
                }
            """,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter
        )
    }

    @Test
    fun `Returnerer forsikring uten tilhørende fkonto12-rader`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_VIRKDATO = 20240101,
            IF10_FORSTOM = 20241231,
            IF10_GODKJ = 'J',
            IF10_TYPE = '1',
            IF10_PREMGRL = 816000,
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )

        rapid.sendTestMessage(testmelding("01020312345"))

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = """
                {
                    "@behov": ["InfotrygdSykepengeforsikringer"],
                    "fødselsnummer": "01020312345",
                    "@løsning": {
                        "InfotrygdSykepengeforsikringer": [
                            {
                                "IF01_KODE": "1",
                                "IF01_AGNR_FNR": 3020112345,
                                "IF10_FORSFOM_SEQ": 0,
                                "IF10_GODKJ": "J",
                                "IF10_FORSFOM": 0,
                                "IF10_VIRKDATO": 20240101,
                                "IF10_TYPE": "1",
                                "IF10_SELVFOM": " ",
                                "IF10_KOMBI": " ",
                                "IF10_PREMGRL": 816000,
                                "IF10_FOM": 0,
                                "IF10_PREMIE": 0,
                                "IF10_GML_PREMGRL": 0,
                                "IF10_GML_FOM": 0,
                                "IF10_GML_PREMIE": 0,
                                "IF10_FRIFOM": 0,
                                "IF10_FORSTOM": 20241231,
                                "IF10_OPPHGR": " ",
                                "IF10_VARSEL": 0,
                                "IF10_TERM_KV": " ",
                                "IF10_TERM_AAR": " ",
                                "IF10_VARSEL_BELOEP": 0,
                                "IF10_BETALT_BELOEP": 0,
                                "IF10_PURR": 0,
                                "IF10_TKNR_BOST": 0,
                                "IF10_TKNR_BEH": 0,
                                "OPPRETTET": "1970-01-01T00:00:00Z",
                                "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                "KILDE_IF": " ",
                                "ID_VED": 0,
                                "OPPDATERT": null,
                                "IF_FKONTO_12_rader": []
                            }
                        ]
                    }
                }
            """,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter
        )
    }

    @Test
    fun `Returnerer forsikring med tilhørende fkonto12-rader`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 1,
            IF10_VIRKDATO = 20240101,
            IF10_FORSTOM = 20241231,
            IF10_GODKJ = 'J',
            IF10_TYPE = '2',
            IF10_PREMGRL = 500000,
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_KODE = '1',
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 1,
            IF12_BETDATO_SEQ = 1,
            IF12_FOM = 20240101,
            IF12_TOM = 20240630,
            IF12_BET_KODE = 'A',
            IF12_BELOEP = BigDecimal("1234.50"),
            IF12_BETDATO = 20240115,
            ID_KONT = BigDecimal("1001"),
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_KODE = '1',
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 1,
            IF12_BETDATO_SEQ = 2,
            IF12_FOM = 20240701,
            IF12_TOM = 20241231,
            IF12_BET_KODE = 'B',
            IF12_BELOEP = BigDecimal("2000.00"),
            IF12_BETDATO = 20240715,
            ID_KONT = BigDecimal("1002"),
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )

        rapid.sendTestMessage(testmelding("01020312345"))

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = """
                {
                    "@behov": ["InfotrygdSykepengeforsikringer"],
                    "fødselsnummer": "01020312345",
                    "@løsning": {
                        "InfotrygdSykepengeforsikringer": [
                            {
                                "IF01_KODE": "1",
                                "IF01_AGNR_FNR": 3020112345,
                                "IF10_FORSFOM_SEQ": 1,
                                "IF10_GODKJ": "J",
                                "IF10_FORSFOM": 0,
                                "IF10_VIRKDATO": 20240101,
                                "IF10_TYPE": "2",
                                "IF10_SELVFOM": " ",
                                "IF10_KOMBI": " ",
                                "IF10_PREMGRL": 500000,
                                "IF10_FOM": 0,
                                "IF10_PREMIE": 0,
                                "IF10_GML_PREMGRL": 0,
                                "IF10_GML_FOM": 0,
                                "IF10_GML_PREMIE": 0,
                                "IF10_FRIFOM": 0,
                                "IF10_FORSTOM": 20241231,
                                "IF10_OPPHGR": " ",
                                "IF10_VARSEL": 0,
                                "IF10_TERM_KV": " ",
                                "IF10_TERM_AAR": " ",
                                "IF10_VARSEL_BELOEP": 0,
                                "IF10_BETALT_BELOEP": 0,
                                "IF10_PURR": 0,
                                "IF10_TKNR_BOST": 0,
                                "IF10_TKNR_BEH": 0,
                                "OPPRETTET": "1970-01-01T00:00:00Z",
                                "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                "KILDE_IF": " ",
                                "ID_VED": 0,
                                "OPPDATERT": null,
                                "IF_FKONTO_12_rader": [
                                    {
                                        "IF12_BETDATO_SEQ": 1,
                                        "IF12_FOM": 20240101,
                                        "IF12_TOM": 20240630,
                                        "IF12_BET_KODE": "A",
                                        "IF12_FRIUKER": null,
                                        "IF12_BELOEP": 1234.50,
                                        "IF12_BETDATO": 20240115,
                                        "OPPRETTET": "1970-01-01T00:00:00Z",
                                        "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                        "KILDE_IF": " ",
                                        "ID_KONT": 1001,
                                        "OPPDATERT": null
                                    },
                                    {
                                        "IF12_BETDATO_SEQ": 2,
                                        "IF12_FOM": 20240701,
                                        "IF12_TOM": 20241231,
                                        "IF12_BET_KODE": "B",
                                        "IF12_FRIUKER": null,
                                        "IF12_BELOEP": 2000.00,
                                        "IF12_BETDATO": 20240715,
                                        "OPPRETTET": "1970-01-01T00:00:00Z",
                                        "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                        "KILDE_IF": " ",
                                        "ID_KONT": 1002,
                                        "OPPDATERT": null
                                    }
                                ]
                            }
                        ]
                    }
                }
            """,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter
        )
    }

    @Test
    fun `Returnerer ikke forsikringer for andre fødselsnumre`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020154321L,
            IF10_VIRKDATO = 20240101,
            IF10_FORSTOM = 20241231,
            IF10_GODKJ = 'J',
            IF10_TYPE = '1',
            IF10_PREMGRL = 816000,
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )

        rapid.sendTestMessage(testmelding("01020312345"))

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = """
                {
                    "@behov": ["InfotrygdSykepengeforsikringer"],
                    "fødselsnummer": "01020312345",
                    "@løsning": {
                        "InfotrygdSykepengeforsikringer": []
                    }
                }
            """,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter
        )
    }

    @Test
    fun `Returnerer alle forsikringer inkludert de som ikke er godkjent`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF10_VIRKDATO = 20240101,
            IF10_FORSTOM = 20241231,
            IF10_GODKJ = 'J',
            IF10_TYPE = '1',
            IF10_PREMGRL = 816000,
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 1,
            IF10_VIRKDATO = 20230101,
            IF10_FORSTOM = 20231231,
            IF10_GODKJ = 'N',
            IF10_TYPE = '2',
            IF10_PREMGRL = 0,
            OPPRETTET = Instant.EPOCH,
            ENDRET_I_KILDE = Instant.EPOCH
        )

        rapid.sendTestMessage(testmelding("01020312345"))

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = """
                {
                    "@behov": ["InfotrygdSykepengeforsikringer"],
                    "fødselsnummer": "01020312345",
                    "@løsning": {
                        "InfotrygdSykepengeforsikringer": [
                            {
                                "IF01_KODE": "1",
                                "IF01_AGNR_FNR": 3020112345,
                                "IF10_FORSFOM_SEQ": 0,
                                "IF10_GODKJ": "J",
                                "IF10_FORSFOM": 0,
                                "IF10_VIRKDATO": 20240101,
                                "IF10_TYPE": "1",
                                "IF10_SELVFOM": " ",
                                "IF10_KOMBI": " ",
                                "IF10_PREMGRL": 816000,
                                "IF10_FOM": 0,
                                "IF10_PREMIE": 0,
                                "IF10_GML_PREMGRL": 0,
                                "IF10_GML_FOM": 0,
                                "IF10_GML_PREMIE": 0,
                                "IF10_FRIFOM": 0,
                                "IF10_FORSTOM": 20241231,
                                "IF10_OPPHGR": " ",
                                "IF10_VARSEL": 0,
                                "IF10_TERM_KV": " ",
                                "IF10_TERM_AAR": " ",
                                "IF10_VARSEL_BELOEP": 0,
                                "IF10_BETALT_BELOEP": 0,
                                "IF10_PURR": 0,
                                "IF10_TKNR_BOST": 0,
                                "IF10_TKNR_BEH": 0,
                                "OPPRETTET": "1970-01-01T00:00:00Z",
                                "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                "KILDE_IF": " ",
                                "ID_VED": 0,
                                "OPPDATERT": null,
                                "IF_FKONTO_12_rader": []
                            },
                            {
                                "IF01_KODE": "1",
                                "IF01_AGNR_FNR": 3020112345,
                                "IF10_FORSFOM_SEQ": 1,
                                "IF10_GODKJ": "N",
                                "IF10_FORSFOM": 0,
                                "IF10_VIRKDATO": 20230101,
                                "IF10_TYPE": "2",
                                "IF10_SELVFOM": " ",
                                "IF10_KOMBI": " ",
                                "IF10_PREMGRL": 0,
                                "IF10_FOM": 0,
                                "IF10_PREMIE": 0,
                                "IF10_GML_PREMGRL": 0,
                                "IF10_GML_FOM": 0,
                                "IF10_GML_PREMIE": 0,
                                "IF10_FRIFOM": 0,
                                "IF10_FORSTOM": 20231231,
                                "IF10_OPPHGR": " ",
                                "IF10_VARSEL": 0,
                                "IF10_TERM_KV": " ",
                                "IF10_TERM_AAR": " ",
                                "IF10_VARSEL_BELOEP": 0,
                                "IF10_BETALT_BELOEP": 0,
                                "IF10_PURR": 0,
                                "IF10_TKNR_BOST": 0,
                                "IF10_TKNR_BEH": 0,
                                "OPPRETTET": "1970-01-01T00:00:00Z",
                                "ENDRET_I_KILDE": "1970-01-01T00:00:00Z",
                                "KILDE_IF": " ",
                                "ID_VED": 0,
                                "OPPDATERT": null,
                                "IF_FKONTO_12_rader": []
                            }
                        ]
                    }
                }
            """,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties = generiskeFelter
        )
    }

    private fun testmelding(fødselsnummer: String) = """
        {
            "@behov": ["InfotrygdSykepengeforsikringer"],
            "fødselsnummer": "$fødselsnummer"
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
