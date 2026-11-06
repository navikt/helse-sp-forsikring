package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.rettighetskategori.RettighetskategoriWiremock
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class ForsikringsvurderingBehovRiverTest {
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
        val testmelding = behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN())

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
    fun `løsning har en forsikringsvurderingId som er en UUID`() {
        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))

        assertEquals(1, outbox.alle().size)
        val løsningMelding = outbox.meldinger()[0]
        val forsikringsvurderingId = løsningMelding["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"]?.asString()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId" }
        assertDoesNotThrow("forsikringsvurderingId \"${forsikringsvurderingId}\" kunne ikke tolkes som en UUID") {
            UUID.fromString(forsikringsvurderingId)
        }
    }

    @Test
    fun `individuell forsikring som ikke passer med søknadstypen ekskluderes i stedet for å feile`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_TYPE = '4')
        val antallRåkopierFør = TestcontainersSpForsikringDatabase.countAlleRåkopier()
        val antallForsikringsvurderingerFør = TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger()

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))

        assertEquals(1, outbox.alle().size)
        val forsikringsvurderingId = outbox.meldinger()[0]["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"]?.asString()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId" }

        assertEquals(antallRåkopierFør + 1, TestcontainersSpForsikringDatabase.countAlleRåkopier())
        assertEquals(antallForsikringsvurderingerFør + 1, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
        assertEquals(
            mapOf(0 to "PASSER_IKKE_MED_SØKNADSTYPE"),
            TestcontainersSpForsikringDatabase.hentEkskluderinger(UUID.fromString(forsikringsvurderingId)),
        )
    }

    @Test
    fun `feiler når dekninger har ulike grader`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '1') // grad=80, fraDag=1
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2') // grad=100, fraDag=17
        val antallRåkopierFør = TestcontainersSpForsikringDatabase.countAlleRåkopier()
        val antallForsikringsvurderingerFør = TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger()

        assertDoesNotThrow {
            rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))
        }

        assertEquals(0, outbox.alle().size)
        assertEquals(antallRåkopierFør, TestcontainersSpForsikringDatabase.countAlleRåkopier())
        assertEquals(antallForsikringsvurderingerFør, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
    }

    @Test
    fun `svarer ikke på behov når bruker har flere gyldige individuelle forsikringer`() {
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 1, IF10_TYPE = '2') // grad=100, fraDag=17
        insertBetaltVedfrivt(IF01_AGNR_FNR = 3020112345L, IF10_FORSFOM_SEQ = 2, IF10_TYPE = '3') // grad=100, fraDag=1

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))

        // Vurderingen er ugyldig, og riveren hopper over meldingen i stedet for å svare med en løsning
        assertEquals(0, outbox.alle().size)
    }

    @Test
    fun `forsikringsvurdering og råkopi lagres ned i databasen`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF10_TYPE = '2',
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF10_TYPE = '3',
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF12_BETDATO_SEQ = 111,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 123,
            IF12_BETDATO_SEQ = 222,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF12_BETDATO_SEQ = 333,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 3020112345L,
            IF10_FORSFOM_SEQ = 456,
            IF12_BETDATO_SEQ = 444,
        )

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))

        assertEquals(1, outbox.alle().size)
        val løsningMelding = outbox.meldinger()[0]
        val forsikringsvurderingId = løsningMelding["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"]?.asString()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId" }
        assertEquals(1, TestcontainersSpForsikringDatabase.countRåkopi(forsikringsvurderingId))
        assertEquals(2, TestcontainersSpForsikringDatabase.countRåkopiIF_VEDFRIVT_10(forsikringsvurderingId))
        assertEquals(4, TestcontainersSpForsikringDatabase.countRåkopiIF_FKONTO_12(forsikringsvurderingId))
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

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = stubRettighetskategoriSN()))

        assertEquals(1, outbox.alle().size)
        val forsikringsvurderingId = outbox.meldinger()[0]["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asString()
        val ekskluderinger = TestcontainersSpForsikringDatabase.hentEkskluderinger(UUID.fromString(forsikringsvurderingId))
        assertEquals(4, ekskluderinger.size)
        assertEquals("SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO", ekskluderinger[1])
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskluderinger[2])
        assertEquals("ALDRI_BETALT", ekskluderinger[3])
        assertEquals("SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO", ekskluderinger[4])
    }

    @Test
    fun `vurderer som selvstendig uten spesielle yrkesgrupper og lagrer rettighetskategori-ID-en`() {
        val rettighetskategoriId = stubRettighetskategoriSN()

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = rettighetskategoriId))

        val forsikringsvurdering = hentLagretForsikringsvurdering()
        assertEquals(Yrkesaktivitetstype.SELVSTENDIG, forsikringsvurdering.input.yrkesaktivitetstype)
        assertEquals(emptySet(), forsikringsvurdering.input.spesielleYrkesgrupper)
        assertEquals(rettighetskategoriId, forsikringsvurdering.input.rettighetskategoriId)
    }

    @Test
    fun `tilleggskategorien jordbruker gir spesiell yrkesgruppe jordbruker`() {
        val rettighetskategoriId = stubRettighetskategoriSN(tilleggskategorier = listOf("JORDBRUKER"))

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = rettighetskategoriId))

        val forsikringsvurdering = hentLagretForsikringsvurdering()
        assertEquals(Yrkesaktivitetstype.SELVSTENDIG, forsikringsvurdering.input.yrkesaktivitetstype)
        assertEquals(setOf(SpesiellYrkesgruppe.JORDBRUKER), forsikringsvurdering.input.spesielleYrkesgrupper)
    }

    @Test
    fun `ser bort fra yrkesaktivitetstype og spesielleYrkesgrupper i behovet`() {
        val rettighetskategoriId = stubRettighetskategoriSN()

        rapid.sendTestMessage(
            behov(
                fødselsnummer = "01020312345",
                rettighetskategoriId = rettighetskategoriId,
                yrkesaktivitetstype = "ARBEIDSTAKER",
                spesielleYrkesgrupper = listOf("JORDBRUKER"),
            ),
        )

        val forsikringsvurdering = hentLagretForsikringsvurdering()
        assertEquals(Yrkesaktivitetstype.SELVSTENDIG, forsikringsvurdering.input.yrkesaktivitetstype)
        assertEquals(emptySet(), forsikringsvurdering.input.spesielleYrkesgrupper)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ARBEIDSTAKER", "ARBEIDSTAKER_OG_SELVSTENDIG_NAERINGSDRIVENDE"])
    fun `gir ingen løsning når hovedkategorien ikke er selvstendig næringsdrivende`(hovedkategori: String) {
        val rettighetskategoriId = UUID.randomUUID()
        rettighetskategoriWiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            hovedkategori = hovedkategori,
            tilleggskategorier = emptyList(),
        )

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = rettighetskategoriId))

        assertIngenLøsningOgIngenForsikringsvurdering()
    }

    @ParameterizedTest
    @ValueSource(ints = [404, 500])
    fun `gir ingen løsning når sp-rettighetskategori svarer med feil`(status: Int) {
        val rettighetskategoriId = UUID.randomUUID()
        rettighetskategoriWiremock.stubFeil(rettighetskategoriId, status)

        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = rettighetskategoriId))

        assertIngenLøsningOgIngenForsikringsvurdering()
    }

    @Test
    fun `gir ingen løsning når rettighetskategoriId mangler i behovet`() {
        rapid.sendTestMessage(behov(fødselsnummer = "01020312345", rettighetskategoriId = null))

        assertIngenLøsningOgIngenForsikringsvurdering()
        assertEquals(emptyList(), rettighetskategoriWiremock.loggedRequests())
    }

    private fun stubRettighetskategoriSN(tilleggskategorier: List<String> = emptyList()): UUID {
        val rettighetskategoriId = UUID.randomUUID()
        rettighetskategoriWiremock.stubRettighetskategori(
            rettighetskategoriId = rettighetskategoriId,
            fødselsnummer = "01020312345",
            hovedkategori = "SELVSTENDIG_NAERINGSDRIVENDE",
            tilleggskategorier = tilleggskategorier,
        )
        return rettighetskategoriId
    }

    private fun behov(
        fødselsnummer: String,
        rettighetskategoriId: UUID?,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        spesielleYrkesgrupper: List<String> = emptyList(),
    ): String =
        // language=json
        """
        {
            "@behov": [ "Forsikringsvurdering" ],
            "fødselsnummer": "$fødselsnummer",
            "yrkesaktivitetstype": "$yrkesaktivitetstype",
            "vedtaksperiodeId": "${UUID.randomUUID()}",
            "behandlingId": "${UUID.randomUUID()}",
            "Forsikringsvurdering" : {
                ${rettighetskategoriId?.let { "\"rettighetskategoriId\": \"$it\"," }.orEmpty()}
                "spesielleYrkesgrupper": [ ${spesielleYrkesgrupper.joinToString { "\"$it\"" }} ],
                "skjæringstidspunkt": "2026-01-01"
            }
        }
        """.trimIndent()

    private fun hentLagretForsikringsvurdering(): Forsikringsvurdering {
        val løsninger = outbox.meldinger().filter { it.has("@løsning") }
        assertEquals(1, løsninger.size)
        val forsikringsvurderingId = løsninger.single()["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].asString()
        val forsikringsvurdering =
            TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
                ForsikringsvurderingRepository(transaction).hent(Forsikringsvurdering.Id.fromString(forsikringsvurderingId))
            }
        return assertNotNull(forsikringsvurdering)
    }

    private fun assertIngenLøsningOgIngenForsikringsvurdering() {
        assertEquals(0, outbox.alle().size)
        assertEquals(0, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
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
}
