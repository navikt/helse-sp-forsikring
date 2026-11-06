package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.rettighetskategori.RettighetskategoriWiremock
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase.insertFkonto12
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase.insertVedfrivt
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
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
    private val outbox = InMemoryOutboxRepository()
    private val rettighetskategoriWiremock = RettighetskategoriWiremock()
    private val rapid =
        TestRapid().apply {
            ForsikringsvurderingBehovRiver(
                rapidsConnection = this,
                replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                versjonAvKode = "",
                outboxRepositoryFactory = { outbox },
                rettighetskategoriClient = rettighetskategoriWiremock.client,
            )
            ForsikringsvurderingResultatBehovRiver(
                rapidsConnection = this,
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                outboxRepositoryFactory = { outbox },
            )
        }

    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
        outbox.tøm()
        rettighetskategoriWiremock.reset()
    }

    @Test
    fun `løsningmelding er lik behovsmeldingen, sett bort fra løsning-feltet og rapids and rivers-genererte felter`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        val testmelding = forsikringsvurderingResultatBehovMelding(forsikringsvurderingId)
        rapid.sendTestMessage(testmelding)

        assertEquals(1, outbox.alle().size)
        assertJsonEquals(
            expectedJson = testmelding,
            actualJsonNode = outbox.meldinger()[0],
            bortsettFraStier =
                TestRapid.GENERERTE_JSONSTIER +
                    setOf(
                        "@løsning",
                        "@id",
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

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": null,
                "harIndividuellForsikring": true
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

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": "2026-05-31",
                "harIndividuellForsikring": true
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `returnerer løsning uten forsikring basert på forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": false,
                "villeHattForsikringOmDenVarBetalt": false,
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `løsning inneholder forsikringsvurderingId`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        assertEquals(1, outbox.alle().size)
        val returnertId = outbox.meldinger()[0]["@løsning"]["ForsikringsvurderingResultat"]["forsikringsvurderingId"]?.asString()
        assertNotNull(returnertId) { "Manglet forsikringsvurderingId i løsning" }
        assertEquals(forsikringsvurderingId, returnertId)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er true når eneste forsikring aldri er betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(true)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er true selv om bruker også har en gyldig betalt forsikring`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2')
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(true)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når forsikringen er betalt`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når forsikringen ble ekskludert av en annen årsak og fortsatt ikke betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2', IF10_FORSTOM = 20251231)

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `villeHattForsikringOmDenVarBetalt er false når bruker ikke har noen forsikringer`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `harForsikringSomIkkePasserMedSøknadstype er true når forsikringen ikke passer med yrkesaktivitetstypen`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '5')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventHarForsikringSomIkkePasserMedSøknadstype(true)
        forventVilleHattForsikringOmDenVarBetalt(false)
    }

    @Test
    fun `harForsikringSomIkkePasserMedSøknadstype er true når jordbrukerforsikring mangler spesiell yrkesgruppe`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '4')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventHarForsikringSomIkkePasserMedSøknadstype(true)
    }

    @Test
    fun `harForsikringSomIkkePasserMedSøknadstype er false når forsikringen passer med søknadstypen`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventHarForsikringSomIkkePasserMedSøknadstype(false)
    }

    @Test
    fun `harForsikringSomIkkePasserMedSøknadstype er false når forsikringen ble ekskludert av en annen årsak`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '5', IF10_FORSTOM = 20251231)

        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventHarForsikringSomIkkePasserMedSøknadstype(false)
    }

    @Test
    fun `harForsikringSomIkkePasserMedSøknadstype er false når bruker ikke har noen forsikringer`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventHarForsikringSomIkkePasserMedSøknadstype(false)
    }

    @Test
    fun `returnerer løsning med harIndividuellForsikring false for JORDBRUKER`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345", tilleggskategorier = listOf("JORDBRUKER"))

        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)

        forventLøsning(
            """
            {
                "forsikringsvurderingId": "$forsikringsvurderingId",
                "harForsikring": true,
                "villeHattForsikringOmDenVarBetalt": false,
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": {
                    "iVentetid": false,
                    "grad": 100
                },
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        assertEquals(0, outbox.alle().size)
    }

    @Test
    fun `ignorerer melding som allerede har løsning`() {
        val forsikringsvurderingId = opprettVurdering(fødselsnummer = "01020312345")

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

        assertEquals(0, outbox.alle().size)
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

        assertEquals(0, outbox.alle().size)
    }

    @ParameterizedTest(name = "tilleggskategori {0} infotrygd-type {1} -> {2} fra dag {3} kategori {4}", quoteTextArguments = false)
    @CsvSource(
        ", 1, 80, true, true",
        ", 2, 100, false, true",
        ", 3, 100, true, true",
        "JORDBRUKER, 4, 100, true, true",
        // Kollektiv forsikring
        "JORDBRUKER, , 100, false, false",
    )
    fun `gir løsning med forsikring`(
        tilleggskategori: String?,
        IF10_TYPE: Char?,
        grad: Int,
        iVentetid: Boolean,
        harIndividuellForsikring: Boolean,
    ) {
        IF10_TYPE?.let { insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = it) }
        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = listOfNotNull(tilleggskategori),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": $grad, "iVentetid": $iVentetid },
                "opphørsdato": null,
                "harIndividuellForsikring": $harIndividuellForsikring
            }
            """.trimIndent()
        }
    }

    @Test
    fun `gir løsning uten forsikring`() {
        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
            }
            """.trimIndent()
        }
    }

    @Test
    fun `gir løsning uten forsikring dersom bruker kun har en ikke-godkjent forsikring`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '1', IF10_GODKJ = 'N')

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": "2026-01-01",
                "harIndividuellForsikring": true
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": "2026-01-02",
                "harIndividuellForsikring": true
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "harIndividuellForsikring": true
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "harIndividuellForsikring": true
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "harIndividuellForsikring": true
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring som aldri er betalt`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '2')

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring med betdato null`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF10_TYPE = '2')
        insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF12_BETDATO_SEQ = 1, IF12_BETDATO = null)

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
            }
            """.trimIndent()
        }
    }

    @Test
    fun `eliminerer forsikring med betdato 0`() {
        insertVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF10_TYPE = '2')
        insertFkonto12(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 0, IF12_BETDATO_SEQ = 1, IF12_BETDATO = 0)

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": null,
                "opphørsdato": null,
                "harIndividuellForsikring": false
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "harIndividuellForsikring": true
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

        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            tilleggskategorier = emptyList(),
        )

        testForsikringsvurderingOgForventResultat(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "01020312345",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering" : {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
                "harForsikringSomIkkePasserMedSøknadstype": false,
                "dekning": { "grad": 100, "iVentetid": false },
                "opphørsdato": null,
                "harIndividuellForsikring": true
            }
            """.trimIndent()
        }
    }

    private fun stubRettighetskategoriSN(
        rettighetskategoriId: UUID,
        fødselsnummer: String,
        tilleggskategorier: List<String>,
    ) {
        rettighetskategoriWiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = fødselsnummer,
            hovedkategori = "SELVSTENDIG_NAERINGSDRIVENDE",
            tilleggskategorier = tilleggskategorier,
        )
    }

    private fun opprettVurdering(
        fødselsnummer: String,
        tilleggskategorier: List<String> = emptyList(),
    ): String {
        val rettighetskategoriId = UUID.randomUUID()
        stubRettighetskategoriSN(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = fødselsnummer,
            tilleggskategorier = tilleggskategorier,
        )
        rapid.sendTestMessage(
            """
            {
                "@behov": [ "Forsikringsvurdering" ],
                "fødselsnummer": "$fødselsnummer",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "vedtaksperiodeId": "${UUID.randomUUID()}",
                "behandlingId": "${UUID.randomUUID()}",
                "Forsikringsvurdering": {
                    "rettighetskategoriId": "$rettighetskategoriId",
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
        val forsikringsvurderingId = outbox.meldinger().last()["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asString()
        outbox.tøm()
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
            },
            "fødselsnummer": "01020312345"
        }
        """.trimIndent()

    private fun forventLøsning(forventetLøsning: String) {
        assertEquals(1, outbox.alle().size)
        val løsningMelding = outbox.meldinger()[0]
        assertJsonEquals(
            expectedJson = forventetLøsning,
            actualJsonNode = løsningMelding["@løsning"]["ForsikringsvurderingResultat"],
        )
    }

    private fun forventVilleHattForsikringOmDenVarBetalt(forventet: Boolean) {
        assertEquals(1, outbox.alle().size)
        val faktisk =
            outbox
                .meldinger()[0]["@løsning"]["ForsikringsvurderingResultat"]["villeHattForsikringOmDenVarBetalt"]
                ?.asBoolean()
        assertNotNull(faktisk) { "Manglet villeHattForsikringOmDenVarBetalt i løsning" }
        assertEquals(forventet, faktisk)
    }

    private fun forventHarForsikringSomIkkePasserMedSøknadstype(forventet: Boolean) {
        assertEquals(1, outbox.alle().size)
        val faktisk =
            outbox
                .meldinger()[0]["@løsning"]["ForsikringsvurderingResultat"]["harForsikringSomIkkePasserMedSøknadstype"]
                ?.asBoolean()
        assertNotNull(faktisk) { "Manglet harForsikringSomIkkePasserMedSøknadstype i løsning" }
        assertEquals(forventet, faktisk)
    }

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
