package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.tilInfotrygdFødselsnummer
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.ServerSocket
import java.time.LocalDate
import java.util.*

private const val CLIENT_ID = "sp-forsikring-junit"

private val testJsonMapper = ObjectMapper().registerModule(JavaTimeModule())

private fun String.somJson(): JsonNode = testJsonMapper.readTree(this)

private fun JsonNode.asTextOrNull(feltnavn: String): String? = this[feltnavn]?.takeUnless { it.isNull }?.asText()

private fun assertFolketrygdlovenreferanse(
    forventetKapittel: Int,
    forventetParagrafIKapittel: Int,
    forventetLedd: Int?,
    forventetBokstav: String?,
    faktisk: JsonNode?,
) {
    assertNotNull(faktisk) { "Forventet folketrygdlovenreferanse, fikk null" }
    requireNotNull(faktisk)
    assertEquals(forventetKapittel, faktisk["kapittel"].asInt())
    assertEquals(forventetParagrafIKapittel, faktisk["paragrafIKapittel"].asInt())
    assertEquals(forventetLedd, faktisk["ledd"].takeUnless { it.isNull }?.asInt())
    assertEquals(forventetBokstav, faktisk["bokstav"].takeUnless { it.isNull }?.asText())
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForsikringsvurderingApiTest {
    private val mockOAuth2Server = MockOAuth2Server().also(MockOAuth2Server::start)

    private val port = ServerSocket(0).use { it.localPort }
    private val serverUrl = "http://localhost:$port"

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                forsikringsvurderingService = ForsikringsvurderingService(TestcontainersReplikadatabase.dataSource),
                clientId = CLIENT_ID,
                issuerUrl = mockOAuth2Server.issuerUrl("default").toString(),
                jwkProviderUri = mockOAuth2Server.jwksUrl("default").toString(),
            )
        }.start(wait = false)

    @BeforeEach
    fun reset() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
    }

    @AfterAll
    fun teardown() {
        embeddedServer.stop()
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når ingen forsikringer finnes i replikabasen`() {
        val (statusCode, body) = postForsikringsvurdering(token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @ParameterizedTest
    @ValueSource(chars = ['1', '3', '4', '5'])
    fun `returnerer harForsikringMedDekningIVentetid true når bruker har dag-1-forsikring`(type: Char) {
        val identitetsnummer = lagIdentitetsnummer()
        // Forsikringen er ikke betalt, og skal derfor telle med uten at typen må passe med søknadstypen
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_TYPE = type,
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(identitetsnummer = identitetsnummer.value, token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":true")) { "Forventet true, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når bruker kun har forsikring fra dag 17`() {
        val identitetsnummer = lagIdentitetsnummer()
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(identitetsnummer = identitetsnummer.value, token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når forsikringen er opphørt på skjæringstidspunktet`() {
        val identitetsnummer = lagIdentitetsnummer()
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
            IF10_FORSTOM = 20251231,
        )

        val (statusCode, body) = postForsikringsvurdering(identitetsnummer = identitetsnummer.value, token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når virkningsdato er etter skjæringstidspunktet`() {
        val identitetsnummer = lagIdentitetsnummer()
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260102,
        )

        val (statusCode, body) = postForsikringsvurdering(identitetsnummer = identitetsnummer.value, token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @Test
    fun `returnerer 400 når identitetsnummer ikke er 11 siffer`() {
        val (statusCode, body) = postForsikringsvurdering(identitetsnummer = "1234", token = bearerToken())

        assertEquals(400, statusCode)
        assertTrue(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
    }

    @Test
    fun `returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = postForsikringsvurdering(token = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token med feil audience`() {
        val (statusCode, _) = postForsikringsvurdering(token = bearerToken(audience = "feil-audience"))

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token fra feil issuer`() {
        val (statusCode, _) = postForsikringsvurdering(token = bearerToken(issuerId = "feil-issuer"))

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med brukertoken fordi flex-apiet kun er for maskin-til-maskin`() {
        val (statusCode, _) = postForsikringsvurdering(token = brukertoken())

        assertEquals(401, statusCode)
    }

    @Test
    fun `GET forsikringsvurderinger slipper gjennom brukertoken fordi spesialist-apiet også brukes av innloggede brukere`() {
        val (statusCode, _) = getForsikringsvurdering(UUID.randomUUID().toString(), brukertoken())

        assertEquals(404, statusCode)
    }

    @Test
    fun `GET forsikringsvurderinger returnerer samlet dekning for individuell forsikring`() {
        val identitetsnummer = lagIdentitetsnummer()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                identitetsnummer = identitetsnummer,
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(identitetsnummer.value, json["identitetsnummer"].asText())
        assertNotNull(json.asTextOrNull("vurdertTidspunkt")) { "Forventet vurdertTidspunkt, fikk: $body" }
        assertEquals(80, json["samletDekning"]["grad"].asInt())
        assertEquals(1, json["samletDekning"]["fraDag"].asInt())
        assertTrue(json["kollektivForsikring"].isNull) { "Forventet ingen kollektiv forsikring, fikk: $body" }

        val forsikring = json["individuelleForsikringer"].single()
        assertEquals(IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.navn, forsikring["navn"].asText())
        assertEquals("2025-06-01", forsikring.asTextOrNull("virkningsdato"))
        assertNull(forsikring.asTextOrNull("opphørsdato"))
        assertTrue(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=true, fikk: $body" }
        assertEquals("Lagt til grunn", forsikring["konklusjon"]["forklaring"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = forsikring["dekningFolketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer fraDag 17 for dag-17-forsikring`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(100, json["samletDekning"]["grad"].asInt())
        assertEquals(17, json["samletDekning"]["fraDag"].asInt())
        assertEquals(IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17.navn, json["individuelleForsikringer"].single()["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "b",
            faktisk = json["individuelleForsikringer"].single()["dekningFolketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ingen dekning og tom liste når bruker ikke har forsikringer`() {
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-01-01"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }
        assertTrue(json["kollektivForsikring"].isNull) { "Forventet kollektivForsikring=null, fikk: $body" }
        assertTrue(json["individuelleForsikringer"].isEmpty) { "Forventet ingen individuelle forsikringer, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer kollektiv forsikring`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(100, json["samletDekning"]["grad"].asInt())
        assertEquals(1, json["samletDekning"]["fraDag"].asInt())

        val kollektivForsikring = json["kollektivForsikring"]
        assertEquals(KollektivForsikring.FISKER_BLAD_B.navn, kollektivForsikring["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "c",
            faktisk = kollektivForsikring["dekningFolketrygdlovenreferanse"],
        )
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 4,
            forventetBokstav = null,
            faktisk = kollektivForsikring["kollektivFolketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som aldri er betalt`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-06-01"),
                            erBetaltNoenGang = false,
                            konklusjon = VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }

        val forsikring = json["individuelleForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        val konklusjon = forsikring["konklusjon"]
        assertEquals("Forsikringen er innvilget, men ikke betalt ennå", konklusjon["forklaring"].asText())
        assertTrue(konklusjon["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i konklusjonen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som ikke passer med søknadstypen`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-06-01"),
                            konklusjon = VurdertIndividuellForsikring.Konklusjon.PASSER_IKKE_MED_SØKNADSTYPE,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurdering.id.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }

        val forsikring = json["individuelleForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        val konklusjon = forsikring["konklusjon"]
        assertEquals("Forsikringen passer ikke med søknadstypen", konklusjon["forklaring"].asText())
        assertTrue(konklusjon["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i konklusjonen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som opphørte før skjæringstidspunktet`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-06-01"),
                            opphører = true,
                            opphørsdato = LocalDate.parse("2025-12-31"),
                            konklusjon = VurdertIndividuellForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }

        val forsikring = json["individuelleForsikringer"].single()
        assertEquals("2025-06-01", forsikring.asTextOrNull("virkningsdato"))
        assertEquals("2025-12-31", forsikring.asTextOrNull("opphørsdato"))
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        val konklusjon = forsikring["konklusjon"]
        assertEquals("Forsikringen opphørte før skjæringstidspunktet", konklusjon["forklaring"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 37,
            forventetLedd = null,
            forventetBokstav = null,
            faktisk = konklusjon["folketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som ikke var gyldig ennå`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2026-01-15"),
                            konklusjon =
                                VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val forsikring = body.somJson()["individuelleForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        assertEquals(
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
            forsikring["konklusjon"]["forklaring"].asText(),
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer både gjeldende og ekskludert forsikring`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.parse("2023-01-01"),
                            opphører = true,
                            opphørsdato = LocalDate.parse("2024-12-31"),
                            konklusjon = VurdertIndividuellForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT,
                        ),
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(80, json["samletDekning"]["grad"].asInt())

        val forsikringer = json["individuelleForsikringer"].associateBy { it["navn"].asText() }
        assertEquals(2, forsikringer.size) { "Forventet to individuelle forsikringer, fikk: $body" }

        val gjeldende = forsikringer.getValue(IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.navn)
        assertTrue(gjeldende["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=true, fikk: $body" }

        val ekskludert = forsikringer.getValue(IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17.navn)
        assertFalse(ekskludert["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        assertEquals("Forsikringen opphørte før skjæringstidspunktet", ekskludert["konklusjon"]["forklaring"].asText())
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 404 når id ikke finnes`() {
        val (statusCode, body) = getForsikringsvurdering(UUID.randomUUID().toString(), bearerToken())

        assertEquals(404, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"status\":404")) { "Forventet ProblemDetail-body med status 404, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 400 når id ikke er en UUID`() {
        val (statusCode, body) = getForsikringsvurdering("ikke-en-uuid", bearerToken())

        assertEquals(400, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) = getForsikringsvurdering(UUID.randomUUID().toString(), token = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `GET forsikringsvurderinger returnerer både kollektiv og individuell tilleggsforsikring`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(100, json["samletDekning"]["grad"].asInt())
        assertEquals(1, json["samletDekning"]["fraDag"].asInt())
        assertEquals(
            IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1.navn,
            json["individuelleForsikringer"].single()["navn"].asText(),
        )
        assertFalse(json["kollektivForsikring"].isNull) { "Forventet kollektiv forsikring, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som ikke var gyldig ennå mer enn 28 dager etter skjæringstidspunktet`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2026-01-30"),
                            konklusjon =
                                VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val forsikring = body.somJson()["individuelleForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        assertEquals(
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
            forsikring["konklusjon"]["forklaring"].asText(),
        )
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID,
        claims: Map<String, Any> = mapOf("idtyp" to "app"),
    ): String =
        mockOAuth2Server
            .issueToken(
                issuerId = issuerId,
                audience = audience,
                claims = claims,
            ).serialize()

    private fun brukertoken(): String = bearerToken(claims = mapOf("NAVident" to "A123456"))

    private fun getForsikringsvurdering(
        forsikringsvurderingId: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .get("$serverUrl/forsikringsvurderinger/$forsikringsvurderingId")
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }

    private fun postForsikringsvurdering(
        identitetsnummer: String = lagIdentitetsnummer().value,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        spesielleYrkesgrupper: Set<String> = emptySet(),
        skjæringstidspunkt: String = "2026-01-01",
        token: String?,
    ): Pair<Int, String> =
        FlexApiClient.postForsikringsvurdering(
            baseUrl = serverUrl,
            identitetsnummer = identitetsnummer,
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            skjæringstidspunkt = skjæringstidspunkt,
            token = token,
        )
}
