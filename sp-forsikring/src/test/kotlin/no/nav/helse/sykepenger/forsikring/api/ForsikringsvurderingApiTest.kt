package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.shared.testsupport.Infotrygdforsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTSKJÆRINGSTIDSPUNKT
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreForsikringsvurdering
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.hc.client5.http.fluent.Request
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.ServerSocket
import java.time.LocalDate
import java.util.*

private const val CLIENT_ID = "sp-forsikring-junit"

private const val INFOTRYGD_FØDSELSNUMMER = 3020112345L

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
        // Forsikringen er ikke betalt, og skal derfor telle med uten at typen valideres mot yrkesaktivitet
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_TYPE = type,
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":true")) { "Forventet true, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når bruker kun har forsikring fra dag 17`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) = postForsikringsvurdering(token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når forsikringen er opphørt på skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
            IF10_FORSTOM = 20251231,
        )

        val (statusCode, body) = postForsikringsvurdering(token = bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når virkningsdato er etter skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260102,
        )

        val (statusCode, body) = postForsikringsvurdering(token = bearerToken())

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
    fun `GET forsikringsvurderinger returnerer samlet dekning for nav-kjøpt forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(TESTFØDSELSNUMMER, json["identitetsnummer"].asText())
        assertEquals(80, json["samletDekning"]["grad"].asInt())
        assertEquals(1, json["samletDekning"]["fraDag"].asInt())
        assertTrue(json["kollektivForsikring"].isNull) { "Forventet ingen kollektiv forsikring, fikk: $body" }

        val forsikring = json["navKjøpteForsikringer"].single()
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", forsikring["navn"].asText())
        assertEquals(TESTSKJÆRINGSTIDSPUNKT.toString(), forsikring.asTextOrNull("virkningsdato"))
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
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(100, json["samletDekning"]["grad"].asInt())
        assertEquals(17, json["samletDekning"]["fraDag"].asInt())
        assertEquals("100 % fra 17. dag (Nav-kjøpt)", json["navKjøpteForsikringer"].single()["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "b",
            faktisk = json["navKjøpteForsikringer"].single()["dekningFolketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ingen dekning og tom liste når bruker ikke har forsikringer`() {
        val forsikringsvurderingId = lagreForsikringsvurdering()

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }
        assertTrue(json["kollektivForsikring"].isNull) { "Forventet kollektivForsikring=null, fikk: $body" }
        assertTrue(json["navKjøpteForsikringer"].isEmpty) { "Forventet ingen nav-kjøpte forsikringer, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer kollektiv forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(100, json["samletDekning"]["grad"].asInt())
        assertEquals(1, json["samletDekning"]["fraDag"].asInt())

        val kollektivForsikring = json["kollektivForsikring"]
        assertEquals("100 % fra 1. dag (Kollektiv)", kollektivForsikring["navn"].asText())
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
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(erBetalt = false)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }

        val forsikring = json["navKjøpteForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        val konklusjon = forsikring["konklusjon"]
        assertEquals("Forsikringen er innvilget, men ikke betalt ennå", konklusjon["forklaring"].asText())
        assertTrue(konklusjon["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i konklusjonen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger forklarer forsikring som opphørte før skjæringstidspunktet`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            virkningsdato = LocalDate.of(2025, 6, 1),
                            opphørsdato = LocalDate.of(2025, 12, 31),
                        ),
                    ),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["samletDekning"].isNull) { "Forventet samletDekning=null, fikk: $body" }

        val forsikring = json["navKjøpteForsikringer"].single()
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
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(virkningsdato = TESTSKJÆRINGSTIDSPUNKT.plusDays(14))),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val forsikring = body.somJson()["navKjøpteForsikringer"].single()
        assertFalse(forsikring["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=false, fikk: $body" }
        assertEquals(
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
            forsikring["konklusjon"]["forklaring"].asText(),
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer både gjeldende og ekskludert forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 0,
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.of(2023, 1, 1),
                            opphørsdato = LocalDate.of(2024, 12, 31),
                        ),
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 1,
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                        ),
                    ),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(80, json["samletDekning"]["grad"].asInt())

        val forsikringer = json["navKjøpteForsikringer"].associateBy { it["navn"].asText() }
        assertEquals(2, forsikringer.size) { "Forventet to nav-kjøpte forsikringer, fikk: $body" }

        val gjeldende = forsikringer.getValue("80 % fra 1. dag (Nav-kjøpt)")
        assertTrue(gjeldende["lagtTilGrunn"].asBoolean()) { "Forventet lagtTilGrunn=true, fikk: $body" }

        val ekskludert = forsikringer.getValue("100 % fra 17. dag (Nav-kjøpt)")
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
    fun `GET forsikringsvurderinger returnerer bakoverkompatible felter for nav-kjøpt forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["harForsikring"].asBoolean()) { "Forventet harForsikring=true, fikk: $body" }
        assertEquals("Individuell", json.asTextOrNull("forsikringskategori"))
        assertEquals(80, json["dekning"]["grad"].asInt())
        assertEquals(1, json["dekning"]["fraDag"].asInt())
        assertTrue(json["ekskluderteForsikringer"].isEmpty) { "Forventet ingen ekskluderte forsikringer, fikk: $body" }

        val gjeldendeForsikring = json["gjeldendeForsikring"]
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", gjeldendeForsikring["navn"].asText())
        assertEquals(80, gjeldendeForsikring["dekningsgrad"].asInt())
        assertTrue(gjeldendeForsikring["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=true, fikk: $body" }
        assertEquals(TESTSKJÆRINGSTIDSPUNKT.toString(), gjeldendeForsikring.asTextOrNull("virkningsdato"))
        assertNull(gjeldendeForsikring.asTextOrNull("opphørsdato"))
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = gjeldendeForsikring["folketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer dekningIVentetid false for dag-17-forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val gjeldendeForsikring = body.somJson()["gjeldendeForsikring"]
        assertEquals(100, gjeldendeForsikring["dekningsgrad"].asInt())
        assertFalse(gjeldendeForsikring["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=false, fikk: $body" }
        assertEquals("100 % fra 17. dag (Nav-kjøpt)", gjeldendeForsikring["navn"].asText())
    }

    @Test
    fun `GET forsikringsvurderinger returnerer bakoverkompatible felter for kollektiv forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["harForsikring"].asBoolean()) { "Forventet harForsikring=true, fikk: $body" }
        assertEquals("Kollektiv", json.asTextOrNull("forsikringskategori"))
        assertEquals(100, json["dekning"]["grad"].asInt())
        assertEquals(1, json["dekning"]["fraDag"].asInt())
        assertTrue(json["ekskluderteForsikringer"].isEmpty) { "Forventet ingen ekskluderte forsikringer, fikk: $body" }
        assertTrue(json["gjeldendeForsikring"].isNull) {
            "gjeldendeForsikring gjaldt kun nav-kjøpte forsikringer i det gamle API-et, fikk: $body"
        }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer forsikringskategori Individuell når bruker har kollektiv og nav-kjøpt tilleggsforsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["harForsikring"].asBoolean()) { "Forventet harForsikring=true, fikk: $body" }
        assertEquals("Individuell", json.asTextOrNull("forsikringskategori"))
        assertEquals(100, json["dekning"]["grad"].asInt())
        assertEquals(1, json["dekning"]["fraDag"].asInt())
        assertEquals("100 % fra 1. dag (Nav-kjøpt)", json["gjeldendeForsikring"]["navn"].asText())
        assertFalse(json["kollektivForsikring"].isNull) { "Forventet kollektiv forsikring i nytt felt, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer tomme bakoverkompatible felter når bruker ikke har forsikringer`() {
        val forsikringsvurderingId = lagreForsikringsvurdering()

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertFalse(json["harForsikring"].asBoolean()) { "Forventet harForsikring=false, fikk: $body" }
        assertTrue(json["forsikringskategori"].isNull) { "Forventet forsikringskategori=null, fikk: $body" }
        assertTrue(json["dekning"].isNull) { "Forventet dekning=null, fikk: $body" }
        assertTrue(json["ekskluderteForsikringer"].isEmpty) { "Forventet ingen ekskluderte forsikringer, fikk: $body" }
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskludert forsikring som opphørte før skjæringstidspunktet`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            virkningsdato = LocalDate.of(2025, 6, 1),
                            opphørsdato = LocalDate.of(2025, 12, 31),
                        ),
                    ),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertFalse(json["harForsikring"].asBoolean()) { "Forventet harForsikring=false, fikk: $body" }
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", ekskludert["navn"].asText())
        assertEquals(80, ekskludert["dekningsgrad"].asInt())
        assertTrue(ekskludert["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=true, fikk: $body" }
        assertEquals("2025-06-01", ekskludert.asTextOrNull("virkningsdato"))
        assertEquals("2025-12-31", ekskludert.asTextOrNull("opphørsdato"))
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = ekskludert["folketrygdlovenreferanse"],
        )

        val ekskluderingsbegrunnelse = ekskludert["ekskluderingsbegrunnelse"]
        assertEquals("Forsikringen opphørte før skjæringstidspunktet", ekskluderingsbegrunnelse["forklaring"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 37,
            forventetLedd = null,
            forventetBokstav = null,
            faktisk = ekskluderingsbegrunnelse["folketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskluderingsårsak ALDRI_BETALT`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(erBetalt = false)),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val ekskludert = body.somJson()["ekskluderteForsikringer"].single()
        assertEquals("ALDRI_BETALT", ekskludert["ekskluderingsårsak"].asText())
        val ekskluderingsbegrunnelse = ekskludert["ekskluderingsbegrunnelse"]
        assertEquals("Forsikringen er innvilget, men ikke betalt ennå", ekskluderingsbegrunnelse["forklaring"].asText())
        assertTrue(ekskluderingsbegrunnelse["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskluderingsårsak SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(virkningsdato = TESTSKJÆRINGSTIDSPUNKT.plusDays(14))),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val ekskludert = body.somJson()["ekskluderteForsikringer"].single()
        assertEquals("SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO", ekskludert["ekskluderingsårsak"].asText())
        assertEquals(
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
            ekskludert["ekskluderingsbegrunnelse"]["forklaring"].asText(),
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskluderingsårsak SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(virkningsdato = TESTSKJÆRINGSTIDSPUNKT.plusDays(29))),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val ekskludert = body.somJson()["ekskluderteForsikringer"].single()
        assertEquals("SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO", ekskludert["ekskluderingsårsak"].asText())
        assertEquals(
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet",
            ekskludert["ekskluderingsbegrunnelse"]["forklaring"].asText(),
        )
    }

    @Test
    fun `GET forsikringsvurderinger skiller gjeldendeForsikring fra ekskluderteForsikringer`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 0,
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.of(2023, 1, 1),
                            opphørsdato = LocalDate.of(2024, 12, 31),
                        ),
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 1,
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                        ),
                    ),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", json["gjeldendeForsikring"]["navn"].asText())

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("100 % fra 17. dag (Nav-kjøpt)", ekskludert["navn"].asText())
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskludert["ekskluderingsårsak"].asText())
    }

    @Test
    fun `GET forsikringsvurderinger har samme verdi i utfasede og nye felter`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer =
                    listOf(
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 0,
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.of(2023, 1, 1),
                            opphørsdato = LocalDate.of(2024, 12, 31),
                        ),
                        Infotrygdforsikring(
                            forsikringssekvensnummer = 1,
                            type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                        ),
                    ),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId.value.toString(), bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertEquals(json["samletDekning"], json["dekning"])
        assertEquals(json.asTextOrNull("vurdertTidspunkt"), json.asTextOrNull("dataHentetTidspunkt"))

        val navKjøpteForsikringer = json["navKjøpteForsikringer"].associateBy { it["navn"].asText() }
        val gjeldende = navKjøpteForsikringer.getValue(json["gjeldendeForsikring"]["navn"].asText())
        assertTrue(gjeldende["lagtTilGrunn"].asBoolean()) { "gjeldendeForsikring må være lagt til grunn, fikk: $body" }

        val ekskludert = json["ekskluderteForsikringer"].single()
        val tilsvarendeNyForsikring = navKjøpteForsikringer.getValue(ekskludert["navn"].asText())
        assertFalse(tilsvarendeNyForsikring["lagtTilGrunn"].asBoolean()) {
            "ekskluderteForsikringer må ikke være lagt til grunn, fikk: $body"
        }
        assertEquals(
            tilsvarendeNyForsikring["konklusjon"]["forklaring"].asText(),
            ekskludert["ekskluderingsbegrunnelse"]["forklaring"].asText(),
        )
        assertEquals(
            tilsvarendeNyForsikring["konklusjon"]["folketrygdlovenreferanse"],
            ekskludert["ekskluderingsbegrunnelse"]["folketrygdlovenreferanse"],
        )
        assertEquals(
            tilsvarendeNyForsikring["dekningFolketrygdlovenreferanse"],
            ekskludert["folketrygdlovenreferanse"],
        )
    }

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID,
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

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
        identitetsnummer: String = TESTFØDSELSNUMMER,
        yrkesaktivitetstype: String = "SELVSTENDIG",
        spesielleYrkesgrupper: Set<String> = emptySet(),
        skjæringstidspunkt: String = TESTSKJÆRINGSTIDSPUNKT.toString(),
        token: String?,
    ): Pair<Int, String> =
        Request
            .post("$serverUrl/api/forsikringsvurdering")
            .bodyString(
                """
                {
                    "identitetsnummer": "$identitetsnummer",
                    "yrkesaktivitetstype": "$yrkesaktivitetstype",
                    "spesielleYrkesgrupper": [ ${spesielleYrkesgrupper.joinToString(",") { "\"$it\"" }} ],
                    "skjæringstidspunkt": "$skjæringstidspunkt"
                }
                """.trimIndent(),
                ContentType.APPLICATION_JSON,
            ).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
