package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.TestcontainersReplikadatabase.insertFkonto12
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.TestcontainersReplikadatabase.insertVedfrivt
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class ForsikringsvurderingResultatBehovRiverTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val rapid =
        TestRapid().apply {
            val forsikringsvurderingRepository = PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource)
            val replikabaseDao = ReplikabaseDao(TestcontainersReplikadatabase.dataSource)
            val oppslagService = OppslagService(replikabaseDao)
            val forsikringsvurderingService = ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)
            ForsikringsvurderingBehovRiver(
                rapidsConnection = this,
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                forsikringsvurderingService = forsikringsvurderingService,
            )
            ForsikringsvurderingResultatBehovRiver(
                rapidsConnection = this,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
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

        val testmelding = forsikringsvurderingResultatBehovMelding(forsikringsvurderingId)
        rapid.sendTestMessage(testmelding)

        assertEquals(1, rapid.inspektør.size)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJsonNode = rapid.inspektør.message(0),
            bortsettFraProperties =
                setOf(
                    "@løsning",
                    "@id",
                    "@opprettet",
                    "system_read_count",
                    "system_participating_services",
                    "@forårsaket_av",
                ),
        )
    }

    @Test
    fun `returnerer løsning med forsikring basert på forsikringsvurderingId`() {
        insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )
        insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `returnerer løsning med forsikring og opphørsdato basert på forsikringsvurderingId`() {
        insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
            IF10_FORSTOM = 20260531,
        )
        insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": "2026-05-31",
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `returnerer løsning uten forsikring basert på forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `løsning inneholder forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        assertEquals(1, rapid.inspektør.size)
        val returnertId = rapid.inspektør.message(0)["@løsning"]["ForsikringsvurderingResultat"]["forsikringsvurderingId"]?.asText()
        assertNotNull(returnertId) { "Manglet forsikringsvurderingId i løsning" }
        assertEquals(forsikringsvurderingId, returnertId)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er true når eneste forsikring aldri er betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(true)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er true selv om bruker også har en gyldig betalt forsikring`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2')
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(true)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når forsikringen er betalt`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når forsikringen ble ekskludert av en annen årsak og fortsatt ikke betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2', IF10_FORSTOM = 20251231)

        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når bruker ikke har noen forsikringer`() {
        val forsikringsvurderingId = opprettVurdering(yrkesaktivitetstype = "SELVSTENDIG")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `returnerer løsning med KOLLEKTIV forsikringskategori for JORDBRUKER`() {
        val forsikringsvurderingId =
            opprettVurdering(
                yrkesaktivitetstype = "SELVSTENDIG",
                spesielleYrkesgrupper = listOf("JORDBRUKER"),
            )

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": null,
                "forsikringskategori": "KOLLEKTIV"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sender ikke svar hvis forsikringsvurderingId ikke finnes`() {
        val ukjentId = UUID.randomUUID().toString()

        assertDoesNotThrow {
            sendForsikringsvurderingResultatBehov(ukjentId)
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
            """.trimIndent(),
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
            """.trimIndent(),
        )

        assertEquals(0, rapid.inspektør.size)
    }

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2} -> {3} fra dag {4} kategori {5}", quoteTextArguments = false)
    @CsvSource(
        "SELVSTENDIG, , 1, 80, true, NAVKJØPT",
        "SELVSTENDIG, , 2, 100, false, NAVKJØPT",
        "SELVSTENDIG, , 3, 100, true, NAVKJØPT",
        "SELVSTENDIG, JORDBRUKER, 4, 100, true, NAVKJØPT",
        "SELVSTENDIG, REINDRIFTER, 4, 100, true, NAVKJØPT",
        "FRILANS, , 5, 100, true, NAVKJØPT",
        // Kollektive forsikringer
        "SELVSTENDIG, JORDBRUKER, , 100, false, KOLLEKTIV",
        "SELVSTENDIG, REINDRIFTER, , 100, false, KOLLEKTIV",
        "ARBEIDSTAKER, FISKER_BLAD_B, , 100, true, KOLLEKTIV",
        "SELVSTENDIG, FISKER_BLAD_B, , 100, true, KOLLEKTIV",
    )
    fun `gir løsning med forsikring`(
        yrkesaktivitetstype: String,
        særskiltGruppe: String?,
        IF10_TYPE: Char?,
        grad: Int,
        iVentetid: Boolean,
        forsikringskategori: String,
    ) {
        IF10_TYPE?.let { insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": $grad, "iVentetid": $iVentetid },
                "opphørsdato": null,
                "forsikringskategori": "$forsikringskategori"
            }
            """.trimIndent()
        }
    }

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2}", quoteTextArguments = false)
    @CsvSource(
        "ARBEIDSTAKER, , ",
        "SELVSTENDIG, , ",
        "FRILANS, , ",
    )
    fun `gir løsning uten forsikring`(
        yrkesaktivitetstype: String,
        særskiltGruppe: String?,
        IF10_TYPE: Char?,
    ) {
        IF10_TYPE?.let { insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `gir løsning uten forsikring dersom bruker kun har en ikke-godkjent forsikring`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '1', IF10_GODKJ = 'N')

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `velger dekning med lavest fraDag når det finnes flere dekninger med samme grad`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2') // grad=100, fraDag=17
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '3') // grad=100, fraDag=1

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": true },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring hvor skjæringstidspunkt er etter tom`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20251231,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring hvor skjæringstidspunkt er lik tom`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20260101,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": "2026-01-01",
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring hvor skjæringstidspunkt er før tom`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20260102,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": "2026-01-02",
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring uten tom`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSTOM = 0,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring hvor virkningsdato er etter skjæringstidspunkt`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260102,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring hvor virkningsdato er lik skjæringstidspunkt`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring hvor virkningsdato er før skjæringstidspunkt`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20251231,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring som aldri er betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": true,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring med betdato null`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF10_TYPE = '2')
        insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF12_BETDATO_SEQ = 1, IF12_BETDATO = null)

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": true,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring med betdato 0`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF10_TYPE = '2')
        insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF12_BETDATO_SEQ = 1, IF12_BETDATO = 0)

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": true,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring hvor skjæringstidspunkt er innenfor opptjeningstid`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSFOM = 20251201,
            IF10_VIRKDATO = 20260201,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring hvor skjæringstidspunkt er lik start av opptjeningstid`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSFOM = 20260101,
            IF10_VIRKDATO = 20260201,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": null,
                "opphørsdato": null,
                "forsikringskategori": null
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring hvor skjæringstidspunkt er lik virkningsdato (slutt av opptjeningstid)`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSFOM = 20251201,
            IF10_VIRKDATO = 20260101,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    @Test
    fun `beholder forsikring uten opptjeningstid`() {
        insertBetaltVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_TYPE = '2',
            IF10_FORSFOM = 0,
            IF10_VIRKDATO = 20260101,
        )

        testForsikringsvurderingOgForventResultat(
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
            """.trimIndent(),
        ) { forsikringsvurderingId: String ->
            // language=json
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "forsikringskategori": "NAVKJØPT"
            }
            """.trimIndent()
        }
    }

    private fun opprettVurdering(
        yrkesaktivitetstype: String,
        spesielleYrkesgrupper: List<String> = emptyList(),
    ): String {
        rapid.sendTestMessage(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "$yrkesaktivitetstype",
                "Forsikringsvurdering": {
                    "spesielleYrkesgrupper": [ ${spesielleYrkesgrupper.joinToString(",") { "\"$it\"" }} ],
                    "skjæringstidspunkt": "2026-01-01"
                }
            }
            """.trimIndent(),
        )

        return popForsikringsvurderingIdFraLøsning()
    }

    private fun testForsikringsvurderingOgForventResultat(
        @Language("JSON") forsikringsvurderingBehovJson: String,
        forventetForsikringsvurderingResultatLøsning: (String) -> String,
    ) {
        rapid.sendTestMessage(forsikringsvurderingBehovJson)
        val forsikringsvurderingId = popForsikringsvurderingIdFraLøsning()
        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)
        forventLøsning(forventetForsikringsvurderingResultatLøsning(forsikringsvurderingId))
    }

    private fun popForsikringsvurderingIdFraLøsning(): String {
        assertEquals(1, rapid.inspektør.size)
        val forsikringsvurderingId = rapid.inspektør.message(0)["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asText()
        rapid.reset()
        return forsikringsvurderingId
    }

    private fun sendForsikringsvurderingResultatBehov(forsikringsvurderingId: String) {
        rapid.sendTestMessage(forsikringsvurderingResultatBehovMelding(forsikringsvurderingId))
    }

    private fun forsikringsvurderingResultatBehovMelding(forsikringsvurderingId: String): String =
        //language=json
        """
        {
            "@behov": [ "ForsikringsvurderingResultat" ],
            "ForsikringsvurderingResultat" : {
                "forsikringsvurderingId" : "$forsikringsvurderingId"
            }
        }
        """.trimIndent()

    private fun forventLøsning(forventetLøsning: String) {
        assertEquals(1, rapid.inspektør.size)
        val løsningMelding = rapid.inspektør.message(0)
        assertJsonEquals(
            expectedJson = forventetLøsning,
            actualJsonNode = løsningMelding["@løsning"]["ForsikringsvurderingResultat"],
        )
    }

    private fun forventVilleHattForsikringOmDenVarBetalt(forventet: Boolean) {
        assertEquals(1, rapid.inspektør.size)
        val faktisk =
            rapid.inspektør
                .message(0)["@løsning"]["ForsikringsvurderingResultat"]["villeHattForsikringOmDenVarBetalt"]
                ?.asBoolean()
        assertNotNull(faktisk) { "Manglet villeHattForsikringOmDenVarBetalt i løsning" }
        assertEquals(forventet, faktisk)
    }

    private fun assertJsonEquals(
        expectedJson: String,
        actualJsonNode: JsonNode,
        bortsettFraProperties: Set<String> = emptySet(),
    ) {
        val expected =
            objectMapper
                .readTree(expectedJson)
                .deepSortedObjectNodeCopy()
                .apply { bortsettFraProperties.forEach { remove(it) } }
        val actual =
            actualJsonNode
                .deepSortedObjectNodeCopy()
                .apply { bortsettFraProperties.forEach { remove(it) } }
        assertEquals(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
        )
    }

    private fun JsonNode.sortedDeep(): JsonNode =
        when (this) {
            is ObjectNode ->
                objectMapper.createObjectNode().also { sorted ->
                    properties()
                        .asSequence()
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

    private fun insertBetaltVedfrivt(
        IF01_AGNR_FNR: Long,
        IF10_FORSFOM_SEQ: Int = 0,
        IF10_TYPE: Char = '1',
        IF10_FORSFOM: Int = 0,
        IF10_VIRKDATO: Int = 20260101,
        IF10_FORSTOM: Int = 0,
        IF10_GODKJ: Char = 'J',
    ) {
        insertVedfrivt(
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_TYPE = IF10_TYPE,
            IF10_FORSFOM = IF10_FORSFOM,
            IF10_VIRKDATO = IF10_VIRKDATO,
            IF10_FORSTOM = IF10_FORSTOM,
            IF10_GODKJ = IF10_GODKJ,
        )
        insertFkonto12(
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
    }
}
