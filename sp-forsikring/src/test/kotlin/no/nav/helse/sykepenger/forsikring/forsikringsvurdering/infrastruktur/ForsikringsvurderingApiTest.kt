package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.infrastruktur

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
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

    private val forsikringsvurderingRepository = PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource)
    private val replikabaseDao = ReplikabaseDao(TestcontainersReplikadatabase.dataSource)
    private val oppslagService = OppslagService(replikabaseDao)
    private val forsikringsvurderingService = ForsikringsvurderingService(forsikringsvurderingRepository, oppslagService)

    private val embeddedServer =
        embeddedServer(CIO, port = port) {
            forsikringsvurderingApi(
                replikabaseDataSource = TestcontainersReplikadatabase.dataSource,
                spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
                forsikringsvurderingRepository = forsikringsvurderingRepository,
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
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når ingen forsikringer finnes i replikabasen`() {
        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @ParameterizedTest
    @ValueSource(chars = ['1', '3', '4', '5'])
    fun `returnerer harForsikringMedDekningIVentetid true når bruker har aktiv dag-1-forsikring`(type: Char) {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = type,
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":true")) { "Forventet harForsikringMedDekningIVentetid=true, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når bruker kun har forsikring fra dag 17`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )

        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når forsikringen er opphørt på skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
            IF10_FORSTOM = 20251231,
        )

        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer harForsikringMedDekningIVentetid false når virkningsdato er etter skjæringstidspunktet`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260102,
        )

        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikringMedDekningIVentetid\":false")) { "Forventet harForsikringMedDekningIVentetid=false, fikk: $body" }
    }

    @Test
    fun `returnerer 400 når identitetsnummer ikke er 11 siffer`() {
        val (statusCode, body) =
            postForsikringsvurdering(
                identitetsnummer = "1234",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(),
            )

        assertEquals(400, statusCode)
        assertTrue(body.contains("\"status\":400")) { "Forventet ProblemDetail-body med status 400, fikk: $body" }
    }

    @Test
    fun `returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = null,
            )

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token med feil audience`() {
        val (statusCode, _) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(audience = "feil-audience"),
            )

        assertEquals(401, statusCode)
    }

    @Test
    fun `returnerer 401 med token fra feil issuer`() {
        val (statusCode, _) =
            postForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = "2026-01-01",
                token = bearerToken(issuerId = "feil-issuer"),
            )

        assertEquals(401, statusCode)
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med korrekt forsikringsvurdering med dekning`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) =
            getForsikringsvurdering(
                forsikringsvurderingId = forsikringsvurderingId,
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"identitetsnummer\":\"12345678901\"")) { "Forventet identitetsnummer, fikk: $body" }
        assertTrue(body.contains("\"harForsikring\":true")) { "Forventet harForsikring=true, fikk: $body" }
        assertTrue(body.contains("\"grad\":80")) { "Forventet grad=80, fikk: $body" }
        assertTrue(body.contains("\"fraDag\":1")) { "Forventet fraDag=1, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med harForsikring false og ingen dekning`() {
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) =
            getForsikringsvurdering(
                forsikringsvurderingId = forsikringsvurderingId,
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"harForsikring\":false")) { "Forventet harForsikring=false, fikk: $body" }
        assertTrue(body.contains("\"dekning\":null")) { "Forventet dekning=null, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 200 med fraDag 17 for dag-17-forsikring`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) =
            getForsikringsvurdering(
                forsikringsvurderingId = forsikringsvurderingId,
                token = bearerToken(),
            )

        assertEquals(200, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"fraDag\":17")) { "Forventet fraDag=17, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 404 når id ikke finnes`() {
        val (statusCode, body) =
            getForsikringsvurdering(
                forsikringsvurderingId = UUID.randomUUID().toString(),
                token = bearerToken(),
            )

        assertEquals(404, statusCode) { "Body was: $body" }
        assertTrue(body.contains("\"status\":404")) { "Forventet ProblemDetail-body med status 404, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer 401 uten autentiseringstoken`() {
        val (statusCode, _) =
            getForsikringsvurdering(
                forsikringsvurderingId = UUID.randomUUID().toString(),
                token = null,
            )

        assertEquals(401, statusCode)
    }

    @Test
    fun `GET forsikringsvurderinger returnerer gjeldendeForsikring og tom liste med ekskluderteForsikringer`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["ekskluderteForsikringer"].isEmpty) { "Forventet ingen ekskluderte forsikringer, fikk: $body" }

        val gjeldendeForsikring = json["gjeldendeForsikring"]
        assertNotNull(gjeldendeForsikring) { "Forventet gjeldendeForsikring, fikk: $body" }
        assertEquals("2026-01-01", gjeldendeForsikring.asTextOrNull("virkningsdato"))
        assertNull(gjeldendeForsikring.asTextOrNull("opphørsdato"))
        assertEquals(80, gjeldendeForsikring["dekningsgrad"].asInt())
        assertTrue(gjeldendeForsikring["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=true, fikk: $body" }
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", gjeldendeForsikring["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = gjeldendeForsikring["folketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer gjeldendeForsikring uten dekning i ventetid for dag-17-forsikring`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val gjeldendeForsikring = body.somJson()["gjeldendeForsikring"]
        assertNotNull(gjeldendeForsikring) { "Forventet gjeldendeForsikring, fikk: $body" }
        assertEquals(100, gjeldendeForsikring["dekningsgrad"].asInt())
        assertEquals(false, gjeldendeForsikring["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=false, fikk: $body" }
        assertEquals("100 % fra 17. dag (Nav-kjøpt)", gjeldendeForsikring["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "b",
            faktisk = gjeldendeForsikring["folketrygdlovenreferanse"],
        )
    }

    @Test
    fun `GET forsikringsvurderinger returnerer null gjeldendeForsikring og tom liste når bruker ikke har forsikringer`() {
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }
        assertTrue(json["ekskluderteForsikringer"].isEmpty) { "Forventet ingen ekskluderte forsikringer, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskludert forsikring med årsak ALDRI_BETALT`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }

        val ekskluderteForsikringer = json["ekskluderteForsikringer"]
        assertEquals(1, ekskluderteForsikringer.size()) { "Forventet én ekskludert forsikring, fikk: $body" }
        val ekskludert = ekskluderteForsikringer.first()
        assertEquals("ALDRI_BETALT", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("2026-01-01", ekskludert.asTextOrNull("virkningsdato"))
        assertNull(ekskludert.asTextOrNull("opphørsdato"))
        assertEquals(80, ekskludert["dekningsgrad"].asInt())
        assertTrue(ekskludert["dekningIVentetid"].asBoolean()) { "Forventet dekningIVentetid=true, fikk: $body" }
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", ekskludert["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = ekskludert["folketrygdlovenreferanse"],
        )
        val ekskluderingsbegrunnelse = ekskludert["ekskluderingsbegrunnelse"]
        assertEquals("Forsikringen er innvilget, men ikke betalt ennå", ekskluderingsbegrunnelse["forklaring"].asText())
        assertTrue(ekskluderingsbegrunnelse["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i begrunnelsen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskludert forsikring med årsak OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
            IF10_FORSTOM = 20251231,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20250601,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("2025-06-01", ekskludert.asTextOrNull("virkningsdato"))
        assertEquals("2025-12-31", ekskludert.asTextOrNull("opphørsdato"))
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", ekskludert["navn"].asText())
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
    fun `GET forsikringsvurderinger returnerer ekskludert forsikring med årsak SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260115,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260115,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("2026-01-15", ekskludert.asTextOrNull("virkningsdato"))
        val ekskluderingsbegrunnelse = ekskludert["ekskluderingsbegrunnelse"]
        assertEquals("Forsikringen var ikke ennå gyldig på skjæringstidspunktet", ekskluderingsbegrunnelse["forklaring"].asText())
        assertTrue(ekskluderingsbegrunnelse["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i begrunnelsen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer ekskludert forsikring med årsak SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20260601,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260601,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()
        assertTrue(json["gjeldendeForsikring"].isNull) { "Forventet gjeldendeForsikring=null, fikk: $body" }

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("2026-06-01", ekskludert.asTextOrNull("virkningsdato"))
        val ekskluderingsbegrunnelse = ekskludert["ekskluderingsbegrunnelse"]
        assertEquals("Forsikringen var ikke ennå gyldig på skjæringstidspunktet", ekskluderingsbegrunnelse["forklaring"].asText())
        assertTrue(ekskluderingsbegrunnelse["folketrygdlovenreferanse"].isNull) { "Forventet ingen referanse i begrunnelsen, fikk: $body" }
    }

    @Test
    fun `GET forsikringsvurderinger returnerer både gjeldendeForsikring og ekskluderteForsikringer`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20230101,
            IF10_FORSTOM = 20241231,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20230101,
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 1,
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250101,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = 56341278901L,
            IF10_FORSFOM_SEQ = 1,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20250101,
        )
        val forsikringsvurderingId =
            opprettForsikringsvurdering(
                identitetsnummer = "12345678901",
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            )

        val (statusCode, body) = getForsikringsvurdering(forsikringsvurderingId, bearerToken())

        assertEquals(200, statusCode) { "Body was: $body" }
        val json = body.somJson()

        val gjeldendeForsikring = json["gjeldendeForsikring"]
        assertNotNull(gjeldendeForsikring) { "Forventet gjeldendeForsikring, fikk: $body" }
        assertEquals("2025-01-01", gjeldendeForsikring.asTextOrNull("virkningsdato"))
        assertNull(gjeldendeForsikring.asTextOrNull("opphørsdato"))
        assertEquals(80, gjeldendeForsikring["dekningsgrad"].asInt())
        assertEquals("80 % fra 1. dag (Nav-kjøpt)", gjeldendeForsikring["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "a",
            faktisk = gjeldendeForsikring["folketrygdlovenreferanse"],
        )

        val ekskludert = json["ekskluderteForsikringer"].single()
        assertEquals("OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT", ekskludert["ekskluderingsårsak"].asText())
        assertEquals("2023-01-01", ekskludert.asTextOrNull("virkningsdato"))
        assertEquals("2024-12-31", ekskludert.asTextOrNull("opphørsdato"))
        assertEquals(100, ekskludert["dekningsgrad"].asInt())
        assertEquals("100 % fra 17. dag (Nav-kjøpt)", ekskludert["navn"].asText())
        assertFolketrygdlovenreferanse(
            forventetKapittel = 8,
            forventetParagrafIKapittel = 36,
            forventetLedd = 1,
            forventetBokstav = "b",
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

    private fun bearerToken(
        issuerId: String = "default",
        audience: String = CLIENT_ID,
    ): String = mockOAuth2Server.issueToken(issuerId = issuerId, audience = audience).serialize()

    private fun opprettForsikringsvurdering(
        identitetsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): String {
        val behovJson = """{"fødselsnummer":"$identitetsnummer","@behov":["Forsikringsvurdering"]}"""
        return TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
            forsikringsvurderingService
                .gjørVurdering(
                    session = transaction,
                    behovJson = behovJson,
                    skjæringstidspunkt = skjæringstidspunkt,
                    fødselsnummer = identitetsnummer,
                    spesielleYrkesgrupper = emptySet(),
                    yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
                ).id.value
                .toString()
        }
    }

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
        identitetsnummer: String,
        skjæringstidspunkt: String,
        token: String?,
    ): Pair<Int, String> =
        Request
            .post("$serverUrl/api/forsikringsvurdering")
            .bodyString(
                """{ "identitetsnummer": "$identitetsnummer", "skjæringstidspunkt": "$skjæringstidspunkt" }""",
                ContentType.APPLICATION_JSON,
            ).apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .execute()
            .handleResponse { response -> response.code to (EntityUtils.toString(response.entity) ?: "") }
}
