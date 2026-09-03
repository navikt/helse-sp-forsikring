package no.nav.helse.sykepenger.forsikring.e2e

import com.github.navikt.tbd_libs.rapids_and_rivers.asInstant
import com.github.navikt.tbd_libs.test.assertJsonEquals
import com.github.navikt.tbd_libs.test.assertMindreEnnNSekunderSiden
import com.github.navikt.tbd_libs.testdata.TestPerson
import com.github.navikt.tbd_libs.testdata.des
import com.github.navikt.tbd_libs.testdata.jan
import com.github.navikt.tbd_libs.testdata.sep
import no.nav.helse.sykepenger.forsikring.api.FlexApiClient
import no.nav.helse.sykepenger.forsikring.api.SpesialistApiClient
import no.nav.helse.sykepenger.forsikring.api.UtbetalingsstatistikkApiClient
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.replikabase.tilInfotrygdFødselsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersRapid
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.tilInfotrygddato
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.*

@Isolated
class SelvstendigHundreProsentFraDagEnE2ETest {
    private val person = TestPerson()
    private val skjæringstidspunkt = 1 sep 2026
    private val vedtaksperiodeId = "${UUID.randomUUID()}"
    private val behandlingId = "${UUID.randomUUID()}"

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            E2ETestApplication.start()
        }
    }

    @BeforeEach
    fun setUp() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `fra søknad til beregnet periode`() {
        // Brukeren har en forsikring i Infotrygd
        opprettBetaltForsikring(virkningsdato = 1 sep 2026, type = '3', premiegrunnlag = 12345)

        forventAtUtbetalingsstatistikkApietSvarerMed(
            fom = 1 jan 2026,
            tom = 31 des 2026,
            expectedJson =
                """
                {
                  "fom" : "2026-01-01",
                  "perForsikringstype" : [ {
                    "navn" : "Fisker Blad B 100 % fra 1. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Frilanser 100 % fra 1. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Jordbruker 100 % fra 17. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Jordbruker tilleggsforsikring 100 % fra 1. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Selvstendig næringsdrivende 100 % fra 17. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  }, {
                    "navn" : "Selvstendig næringsdrivende 80 % fra 1. dag",
                    "totalt" : 0.0,
                    "utbetaltIVentetid" : 0.0,
                    "utbetaltUtenomVentetid" : 0.0
                  } ],
                  "tom" : "2026-12-31"
                }
                """.trimIndent(),
        )

        // Flex sjekker om det er noen vits i å søke i ventetiden
        forventAtFlexApietSvarerMed("""{ "harForsikringMedDekningIVentetid": true }""")

        // Så kommer behovet fra Spleis for sykefraværstilfellet
        val forsikringsvurderingId = utførForsikringsvurderingBehov()

        // Og så behovet for data til beregningen av en periode
        forventLøsningPåForsikringsvurderingResultatBehov(
            forsikringsvurderingId = forsikringsvurderingId,
            expectedJson =
                """
                {
                  "dekning" : {
                    "grad" : 100,
                    "iVentetid" : true
                  },
                  "forsikringsvurderingId" : "$forsikringsvurderingId",
                  "harForsikring" : true,
                  "harForsikringSomIkkePasserMedSøknadstype" : false,
                  "harIndividuellForsikring" : true,
                  "opphørsdato" : null,
                  "villeHattForsikringOmDenVarBetalt" : false
                }
                """.trimIndent(),
        )

        // Saksbehandler går inn og ser på vurderingen i Speil
        forventAtSpeilApietSvarerMed(
            forsikringsvurderingId = forsikringsvurderingId,
            expectedJson =
                """
                {
                  "identitetsnummer" : "${person.identitetsnummer}",
                  "individuelleForsikringer" : [ {
                    "dekningFolketrygdlovenreferanse" : {
                      "bokstav" : "c",
                      "kapittel" : 8,
                      "ledd" : 1,
                      "paragrafIKapittel" : 36
                    },
                    "konklusjon" : {
                      "folketrygdlovenreferanse" : null,
                      "forklaring" : "Lagt til grunn"
                    },
                    "lagtTilGrunn" : true,
                    "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "${1 sep 2026}"
                  } ],
                  "kollektivForsikring" : null,
                  "samletDekning" : {
                    "fraDag" : 1,
                    "grad" : 100
                  }
                }
                """.trimIndent(),
        )
    }

    private fun utførForsikringsvurderingBehov(): String {
        val offsetFørBehov = TestcontainersRapid.nesteOffset()
        sendForsikringsvurderingBehov()
        val løsning =
            TestcontainersRapid.ventPåMelding(fraOffset = offsetFørBehov) { melding ->
                melding.path("@løsning").hasNonNull("Forsikringsvurdering")
            }
        val forsikringsvurderingId = løsning["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].stringValue()
        assertNotNull(forsikringsvurderingId) { "Manglet forsikringsvurderingId i løsningen: $løsning" }
        return forsikringsvurderingId
    }

    private fun forventLøsningPåForsikringsvurderingResultatBehov(
        forsikringsvurderingId: String,
        @Language("JSON") expectedJson: String,
    ) {
        val offsetFørBehov = TestcontainersRapid.nesteOffset()
        sendForsikringsvurderingResultatBehov(forsikringsvurderingId)
        val løsning =
            TestcontainersRapid.ventPåMelding(fraOffset = offsetFørBehov) { melding ->
                melding.path("@løsning").hasNonNull("ForsikringsvurderingResultat")
            }
        assertJsonEquals(
            expectedJson = expectedJson,
            actualJsonNode = løsning["@løsning"]["ForsikringsvurderingResultat"],
        )
    }

    private fun forventAtSpeilApietSvarerMed(
        forsikringsvurderingId: String,
        @Language("JSON") expectedJson: String,
    ) {
        val forsikringsvurderingApiSvar = getForsikringsvurdering(forsikringsvurderingId)
        assertJsonEquals(
            expectedJson =
            expectedJson,
            actualJsonNode = forsikringsvurderingApiSvar,
            bortsettFraStier = setOf("vurdertTidspunkt"),
        )
        assertMindreEnnNSekunderSiden(
            sekunder = 30,
            actual =
                forsikringsvurderingApiSvar["vurdertTidspunkt"]
                    .asInstant()
                    .atZone(
                        ZoneId.of("Europe/Oslo"),
                    ).toLocalDateTime(),
        )
    }

    private fun forventAtFlexApietSvarerMed(
        @Language("JSON") expectedJson: String,
    ) {
        val flexForsikringsvurdering = postFlexForsikringsvurdering()
        assertJsonEquals(
            expectedJson = expectedJson,
            actualJsonNode = flexForsikringsvurdering,
        )
    }

    private fun forventAtUtbetalingsstatistikkApietSvarerMed(
        fom: LocalDate,
        tom: LocalDate,
        @Language("JSON") expectedJson: String,
    ) {
        val utbetalingsstatistikk = getUtbetalingsstatistikk(fom = fom, tom = tom)
        assertJsonEquals(
            expectedJson = expectedJson,
            actualJsonNode = utbetalingsstatistikk,
        )
    }

    private fun sendForsikringsvurderingBehov() {
        val meldingId = UUID.randomUUID()
        val now = Instant.now()
        val localNow = now.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()
        val forårsaketAvId = UUID.randomUUID()
        val testmelding =
            """
            {
              "@id": "$meldingId",
              "@behov": [ "Forsikringsvurdering" ],
              "@behovId": "${UUID.randomUUID()}",
              "@opprettet": "$localNow",
              "@event_name": "behov",
              "behandlingId": "$behandlingId",
              "@opprettetUTC": "$now",
              "fødselsnummer": "${person.identitetsnummer}",
              "@forårsaket_av": {
                "id": "$forårsaketAvId",
                "behov": [ "Sykepengehistorikk" ],
                "opprettet": "$localNow",
                "event_name": "behov"
              },
              "vedtaksperiodeId": "$vedtaksperiodeId",
              "system_read_count": 1,
              "meldingsreferanseId": "$forårsaketAvId",
              "organisasjonsnummer": "SELVSTENDIG",
              "yrkesaktivitetstype": "SELVSTENDIG",
              "Forsikringsvurdering": {
                "skjæringstidspunkt": "$skjæringstidspunkt",
                "spesielleYrkesgrupper": [ ]
              },
              "system_participating_services": [
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/helse-spleis-spleis:2026.09.02-10.09-3235925@sha256:8f3eda3e0eaf25ad269c1711264cdf276e4b333aa2e3683f9fff5c4ec20d2585",
                  "service": "helse-spleis",
                  "instance": "helse-spleis-abc123def-feesh"
                },
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "service": "sp-forsikring",
                  "instance": "sp-forsikring-fed456cba0-awooo",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/helse-sp-forsikring:2026.09.02-12.09-a2e7a4b1a439@sha256:127023655bf8c9e7468af6f146c8bd4014df4c8f2ed79d3b8ca85735514b1ce1"
                }
              ],
              "@sendt": "$now"
            }                
            """.trimIndent()
        TestcontainersRapid.sendPåRapid(key = person.identitetsnummer, melding = testmelding)
    }

    private fun sendForsikringsvurderingResultatBehov(forsikringsvurderingId: String) {
        val meldingId = UUID.randomUUID()
        val now = Instant.now()
        val localNow = now.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()
        val forårsaketAvId = UUID.randomUUID()
        val testmelding =
            """
            {
              "@id": "$meldingId",
              "@behov": [ "ForsikringsvurderingResultat" ],
              "@behovId": "${UUID.randomUUID()}",
              "@opprettet": "$localNow",
              "@event_name": "behov",
              "behandlingId": "$behandlingId",
              "@opprettetUTC": "$now",
              "fødselsnummer": "${person.identitetsnummer}",
              "@forårsaket_av": {
                "id": "$forårsaketAvId",
                "behov": [ "Forsikringsvurdering" ],
                "opprettet": "$localNow",
                "event_name": "behov"
              },
              "vedtaksperiodeId": "$vedtaksperiodeId",
              "system_read_count": 1,
              "meldingsreferanseId": "$forårsaketAvId",
              "organisasjonsnummer": "SELVSTENDIG",
              "yrkesaktivitetstype": "SELVSTENDIG",
              "ForsikringsvurderingResultat": {
                "forsikringsvurderingId": "$forsikringsvurderingId"
              },
              "system_participating_services": [
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/helse-spleis-spleis:2026.09.02-10.09-3235925@sha256:8f3eda3e0eaf25ad269c1711264cdf276e4b333aa2e3683f9fff5c4ec20d2585",
                  "service": "helse-spleis",
                  "instance": "helse-spleis-abc123def-feesh"
                },
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "service": "sp-forsikring",
                  "instance": "sp-forsikring-fed456cba0-awooo",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/helse-sp-forsikring:2026.09.02-12.09-a2e7a4b1a439@sha256:127023655bf8c9e7468af6f146c8bd4014df4c8f2ed79d3b8ca85735514b1ce1"
                }
              ],
              "@sendt": "$now"
            }
            """.trimIndent()
        TestcontainersRapid.sendPåRapid(key = person.identitetsnummer, melding = testmelding)
    }

    var vedfrivtSeq = 0
    var fkontoSeq = 0

    private fun opprettBetaltForsikring(
        virkningsdato: LocalDate,
        type: Char,
        premiegrunnlag: Int,
        opphørsdato: LocalDate? = null,
        opphørsgrunn: String? = null,
    ) {
        val vedfrivtId = vedfrivtSeq++
        val nå = Instant.now()
        val IF01_KODE = '1'
        val IF01_AGNR_FNR = Identitetsnummer.fraString(person.identitetsnummer).tilInfotrygdFødselsnummer()
        val IF10_FORSFOM_SEQ = vedfrivtId
        val forsikringFom = virkningsdato.minusDays(28L)
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_GODKJ = 'J',
            IF10_FORSFOM = forsikringFom.tilInfotrygddato(),
            IF10_VIRKDATO = virkningsdato.tilInfotrygddato(),
            IF10_TYPE = type,
            IF10_SELVFOM = " ",
            IF10_KOMBI = 'N',
            IF10_PREMGRL = premiegrunnlag,
            IF10_FOM = 0,
            IF10_PREMIE = 0,
            IF10_GML_PREMGRL = 0,
            IF10_GML_FOM = 0,
            IF10_GML_PREMIE = 0,
            IF10_FRIFOM = 0,
            IF10_FORSTOM = opphørsdato?.tilInfotrygddato() ?: 0,
            IF10_OPPHGR = opphørsgrunn ?: " ",
            IF10_VARSEL = 0,
            IF10_TERM_KV = ' ',
            IF10_TERM_AAR = " ",
            IF10_VARSEL_BELOEP = 0,
            IF10_BETALT_BELOEP = 0,
            IF10_PURR = 0,
            IF10_TKNR_BOST = 0,
            IF10_TKNR_BEH = 0,
            OPPRETTET = nå,
            ENDRET_I_KILDE = nå,
            KILDE_IF = " ",
            ID_VED = BigDecimal.valueOf(vedfrivtId.toLong()),
            OPPDATERT = nå,
        )
        val fkontoId = fkontoSeq++

        val betalingFomYearMonth = YearMonth.of(forsikringFom.year, 1 + (6 * (forsikringFom.month.value / 7)))
        val betalingTomYearMonth = betalingFomYearMonth.plusMonths(5)
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = fkontoId,
            IF12_FOM = betalingFomYearMonth.atDay(1).tilInfotrygddato(),
            IF12_TOM = betalingTomYearMonth.atEndOfMonth().tilInfotrygddato(),
            IF12_BET_KODE = 'B',
            IF12_FRIUKER = null,
            IF12_BELOEP = null,
            IF12_BETDATO = forsikringFom.plusDays(14).tilInfotrygddato(),
            OPPRETTET = nå,
            ENDRET_I_KILDE = nå,
            KILDE_IF = " ",
            ID_KONT = BigDecimal.valueOf(fkontoId.toLong()),
            OPPDATERT = nå,
        )
    }

    private val objectMapper = jacksonObjectMapper()

    private fun postFlexForsikringsvurdering(): JsonNode =
        forvent200OgTolkJson(
            FlexApiClient
                .postForsikringsvurdering(
                    baseUrl = E2ETestApplication.baseUrl,
                    identitetsnummer = person.identitetsnummer,
                    yrkesaktivitetstype = "SELVSTENDIG",
                    spesielleYrkesgrupper = emptySet(),
                    skjæringstidspunkt = skjæringstidspunkt.toString(),
                    token = m2mToken(),
                ),
        )

    private fun getForsikringsvurdering(forsikringsvurderingId: String): JsonNode =
        forvent200OgTolkJson(
            SpesialistApiClient
                .getForsikringsvurdering(
                    baseUrl = E2ETestApplication.baseUrl,
                    forsikringsvurderingId = forsikringsvurderingId,
                    token = m2mToken(),
                ),
        )

    private fun getUtbetalingsstatistikk(
        fom: LocalDate,
        tom: LocalDate,
    ): JsonNode =
        forvent200OgTolkJson(
            UtbetalingsstatistikkApiClient
                .getUtbetalingsstatistiskk(
                    baseUrl = E2ETestApplication.baseUrl,
                    fom = fom,
                    tom = tom,
                    token = m2mToken(),
                ),
        )

    private fun forvent200OgTolkJson(statusAndBody: Pair<Int, String>): JsonNode {
        val (status, body) = statusAndBody
        assertEquals(200, status, "Status var ikke 200. Body var: $body")
        return runCatching {
            objectMapper.readTree(body)
        }.onFailure {
            assertEquals(null, it, "Klarte ikke tolke svar som JSON. Body var: $body")
        }.getOrThrow()
    }

    private fun m2mToken(): String =
        E2ETestApplication.mockOAuth2Server
            .issueToken(issuerId = "default", audience = E2ETestApplication.CLIENT_ID, claims = mapOf("idtyp" to "app"))
            .serialize()
}
