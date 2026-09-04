package no.nav.helse.sykepenger.forsikring.e2e

import com.github.navikt.tbd_libs.rapids_and_rivers.asInstant
import com.github.navikt.tbd_libs.test.assertJsonEquals
import com.github.navikt.tbd_libs.test.assertMindreEnnNSekunderSiden
import com.github.navikt.tbd_libs.testdata.TestPerson
import com.github.navikt.tbd_libs.testdata.des
import com.github.navikt.tbd_libs.testdata.jan
import no.nav.helse.sykepenger.forsikring.api.FlexApiClient
import no.nav.helse.sykepenger.forsikring.api.SpesialistApiClient
import no.nav.helse.sykepenger.forsikring.api.UtbetalingsstatistikkApiClient
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersRapid
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.Isolated
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.*
import kotlin.test.fail

data class Sykefraværstilfelle(
    val skjæringstidspunkt: LocalDate,
    val vedtaksperioder: List<Vedtaksperiode>,
) {
    data class Vedtaksperiode(
        val fom: LocalDate,
        val tom: LocalDate,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
    )
}

@Isolated
abstract class AbstractE2ETest(
    protected val yrkesaktivitetstype: String,
    protected val spesiellYrkesgruppe: String? = null,
    skjæringstidspunkt: LocalDate,
) {
    protected val testPerson = TestPerson()
    protected val sykefraværstilfelle =
        Sykefraværstilfelle(
            skjæringstidspunkt = skjæringstidspunkt,
            vedtaksperioder =
                listOf(
                    Sykefraværstilfelle.Vedtaksperiode(
                        fom = skjæringstidspunkt,
                        tom = YearMonth.from(skjæringstidspunkt).atEndOfMonth(),
                        vedtaksperiodeId = UUID.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                    ),
                    Sykefraværstilfelle.Vedtaksperiode(
                        fom = skjæringstidspunkt.plusMonths(1L),
                        tom = YearMonth.from(skjæringstidspunkt.plusMonths(1L)).atEndOfMonth(),
                        vedtaksperiodeId = UUID.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                    ),
                ),
        )
    protected val førsteVedtaksperiode = sykefraværstilfelle.vedtaksperioder.first()
    protected val andreVedtaksperiode = sykefraværstilfelle.vedtaksperioder[1]
    protected val rapid = TestcontainersRapid.Klient(sjekkAtApplikasjonenLever = E2ETestApplication::sjekkAtApplikasjonenLever)

    @BeforeEach
    fun setUp() {
        E2ETestApplication.reset()
    }

    @AfterEach
    fun tearDown() {
        rapid.close()
    }

    protected fun saksbehandlerSjekkerForsikringsvurderingISpeil(
        forsikringsvurderingId: String,
        @Language("JSON") forventetResponse: String,
    ) {
        val forsikringsvurderingApiSvar = getForsikringsvurdering(forsikringsvurderingId)
        assertJsonEquals(
            expectedJson =
            forventetResponse,
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

    private val objectMapper = jacksonObjectMapper()

    protected fun lagForsikringsvurderingBehovMelding(): JsonNode {
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
              "behandlingId": "${førsteVedtaksperiode.behandlingId}",
              "@opprettetUTC": "$now",
              "fødselsnummer": "${testPerson.identitetsnummer}",
              "@forårsaket_av": {
                "id": "$forårsaketAvId",
                "behov": [ "Sykepengehistorikk" ],
                "opprettet": "$localNow",
                "event_name": "behov"
              },
              "vedtaksperiodeId": "${førsteVedtaksperiode.vedtaksperiodeId}",
              "system_read_count": 1,
              "meldingsreferanseId": "$forårsaketAvId",
              "organisasjonsnummer": "$yrkesaktivitetstype",
              "yrkesaktivitetstype": "$yrkesaktivitetstype",
              "Forsikringsvurdering": {
                "skjæringstidspunkt": "${sykefraværstilfelle.skjæringstidspunkt}",
                "spesielleYrkesgrupper": [ ${spesiellYrkesgruppe?.let { "\"$it\"" }.orEmpty()} ]
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
        return objectMapper.readTree(testmelding)
    }

    protected fun publiserMeldingOgVentTilDenErBehandlet(melding: JsonNode) {
        val offset = rapid.send(key = testPerson.identitetsnummer, melding = melding)
        // Venter til applikasjonen faktisk har behandlet meldingen ferdig, så vi slipper race conditions
        TestcontainersRapid.ventTilMeldingErFerdigBehandlet(
            konsumentgruppe = E2ETestApplication.KAFKA_CONSUMER_GROUP_ID,
            offset = offset,
            melding = melding,
            sjekkAtApplikasjonenLever = E2ETestApplication::sjekkAtApplikasjonenLever,
        )
    }

    protected fun lagForsikringsvurderingResultatBehovMelding(
        vedtaksperiode: Sykefraværstilfelle.Vedtaksperiode,
        forsikringsvurderingId: String,
    ): JsonNode {
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
              "behandlingId": "${vedtaksperiode.behandlingId}",
              "@opprettetUTC": "$now",
              "fødselsnummer": "${testPerson.identitetsnummer}",
              "@forårsaket_av": {
                "id": "$forårsaketAvId",
                "behov": [ "Forsikringsvurdering" ],
                "opprettet": "$localNow",
                "event_name": "behov"
              },
              "vedtaksperiodeId": "${vedtaksperiode.vedtaksperiodeId}",
              "system_read_count": 1,
              "meldingsreferanseId": "$forårsaketAvId",
              "organisasjonsnummer": "$yrkesaktivitetstype",
              "yrkesaktivitetstype": "$yrkesaktivitetstype",
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
        return objectMapper.readTree(testmelding)
    }

    private fun postFlexForsikringsvurdering(): JsonNode =
        forvent200OgTolkJson(
            FlexApiClient
                .postForsikringsvurdering(
                    baseUrl = E2ETestApplication.baseUrl,
                    identitetsnummer = testPerson.identitetsnummer,
                    yrkesaktivitetstype = yrkesaktivitetstype,
                    spesielleYrkesgrupper = setOfNotNull(spesiellYrkesgruppe),
                    skjæringstidspunkt = sykefraværstilfelle.skjæringstidspunkt.toString(),
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

    protected fun getUtbetalingsstatistikk(
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

    protected fun spesialistSenderVedtakFattet(
        vedtaksperiode: Sykefraværstilfelle.Vedtaksperiode,
        forsikringsvurderingId: String,
        @Language("JSON") dekning: String,
        dekningsgradIVentetid: Int,
        dekningsgradEtterVentetid: Int,
        sykepengegrunnlag: Int,
        dagbeløpIVentetid: Int,
        dagsbeløpEtterVentetid: Int,
    ) {
        publiserMeldingOgVentTilDenErBehandlet(
            lagVedtakFattetMelding(
                vedtaksperiode = vedtaksperiode,
                forsikringsvurderingId = forsikringsvurderingId,
                dekning = dekning,
                dekningsgradIVentetid = dekningsgradIVentetid,
                dekningsgradEtterVentetid = dekningsgradEtterVentetid,
                sykepengegrunnlag = sykepengegrunnlag,
                dagbeløpIVentetid = dagbeløpIVentetid,
                dagsbeløpEtterVentetid = dagsbeløpEtterVentetid,
            ),
        )
    }

    private fun lagVedtakFattetMelding(
        vedtaksperiode: Sykefraværstilfelle.Vedtaksperiode,
        forsikringsvurderingId: String,
        @Language("JSON") dekning: String,
        dekningsgradIVentetid: Int,
        dekningsgradEtterVentetid: Int,
        sykepengegrunnlag: Int,
        dagbeløpIVentetid: Int,
        dagsbeløpEtterVentetid: Int,
    ): JsonNode {
        val meldingId = UUID.randomUUID()
        val now = Instant.now()
        val localNow = now.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()
        val forårsaketAvId = UUID.randomUUID()
        // language=json
        val testmelding =
            """
            {
              "@event_name": "vedtak_fattet",
              "fødselsnummer": "${testPerson.identitetsnummer}",
              "aktørId": "${testPerson.aktørId}",
              "yrkesaktivitetstype": "$yrkesaktivitetstype",
              "vedtaksperiodeId": "${vedtaksperiode.vedtaksperiodeId}",
              "behandlingId": "${vedtaksperiode.behandlingId}",
              "organisasjonsnummer": "$yrkesaktivitetstype",
              "fom": "${vedtaksperiode.fom}",
              "tom": "${vedtaksperiode.tom}",
              "skjæringstidspunkt": "${sykefraværstilfelle.skjæringstidspunkt}",
              "hendelser": [ "${UUID.randomUUID()}" ],
              "sykepengegrunnlag": ${BigDecimal.valueOf(sykepengegrunnlag.toLong()).setScale(1)},
              "vedtakFattetTidspunkt": "${LocalDateTime.now()}",
              "utbetalingId": "${UUID.randomUUID()}",
              "tags": [ "Førstegangsbehandling", "Personutbetaling", "Innvilget", "EnArbeidsgiver" ],
              "sykepengegrunnlagsfakta": {
                "fastsatt": "EtterHovedregel",
                "6G": 900000.0,
                "tags": [ "6GBegrenset" ],
                "selvstendig": {
                  "beregningsgrunnlag": 1006791.0,
                  "pensjonsgivendeInntekter": [
                    { "årstall": ${vedtaksperiode.fom.year - 1}, "beløp": 654321.0 },
                    { "årstall": ${vedtaksperiode.fom.year - 2}, "beløp": 654321.0 },
                    { "årstall": ${vedtaksperiode.fom.year - 3}, "beløp": 654321.0 }
                  ]
                }
              },
              "begrunnelser": [
                {
                  "type": "Innvilgelse",
                  "begrunnelse": "",
                  "perioder": [ { "fom": "${vedtaksperiode.fom}", "tom": "${vedtaksperiode.tom}" } ]
                }
              ],
              "saksbehandler": { "ident": "A123456", "navn": "A123456" },
              "automatiskFattet": false,
              "dekning": $dekning,
              "forsikringsvurderingId": "$forsikringsvurderingId",
              "utbetalingsdager": ${
                generateSequence(vedtaksperiode.fom) { dato ->
                    dato.plusDays(1L).takeUnless { it > vedtaksperiode.tom }
                }
                    .joinToString(prefix = "[", separator = ",", postfix = "]") { dato ->
                        val erVentetid = dato < sykefraværstilfelle.skjæringstidspunkt.plusDays(16)
                        val erHelg = dato.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        val type =
                            when {
                                erVentetid -> "Ventetidsdag"
                                erHelg -> "NavHelgDag"
                                else -> "NavDag"
                            }
                        val dekningsgrad = if (erVentetid) dekningsgradIVentetid else dekningsgradEtterVentetid
                        val beløpTilBruker =
                            when {
                                erHelg -> 0
                                erVentetid -> dagbeløpIVentetid
                                else -> dagsbeløpEtterVentetid
                            }
                        // language=json
                        """
                        {
                            "dato": "$dato",
                            "type": "$type",
                            "sykdomsgrad": 100,
                            "begrunnelser": [ ],
                            "dekningsgrad": $dekningsgrad,
                            "beløpTilBruker": $beløpTilBruker,
                            "beløpTilArbeidsgiver": 0
                        }
                        """.trimIndent()
                    }
            },
              "@id": "$meldingId",
              "@opprettet": "$localNow",
              "system_read_count": 1,
              "system_participating_services": [
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "service": "spesialist",
                  "instance": "spesialist-abc123def-feesh",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/spesialist:2026.09.03-07.02-ae761cf42afe@sha256:fec16c23771a858c4a1417d69c9375a45e71e0dc75f3ee5fd14f774457efae97"
                },
                {
                  "id": "$meldingId",
                  "time": "$localNow",
                  "service": "sp-forsikring",
                  "instance": "sp-forsikring-fed456cba0-awooo",
                  "image": "europe-north1-docker.pkg.dev/nais-management-233d/tbd/helse-sp-forsikring:2026.09.03-12.47-8f420717699f@sha256:d7a66191a0501919cb85e52efcd10b3168c8bb0cb34df9f335f50f27bfced8ea"
                }
              ],
              "@forårsaket_av": {
                "id": "$forårsaketAvId",
                "opprettet": "$localNow",
                "event_name": "avsluttet_med_vedtak"
              }
            }
            """.trimIndent()
        return objectMapper.readTree(testmelding)
    }

    protected fun detBlirProdusertEnForsikringsvurderingResultatLøsning(
        @Language("JSON") forventetLøsning: String,
    ) {
        assertLøsningPåBehov(
            forventetLøsning = forventetLøsning,
            behovMelding =
                rapid.konsumerMelding {
                    it.path("fødselsnummer").asString() == testPerson.identitetsnummer &&
                        it.path("@event_name").stringValue() == "behov" &&
                        it.path("@løsning").path("ForsikringsvurderingResultat").isMissingNode
                },
            faktiskLøsningMelding =
                rapid.konsumerMelding {
                    it.path("fødselsnummer").asString() == testPerson.identitetsnummer &&
                        it.path("@event_name").stringValue() == "behov" &&
                        it.path("@løsning").path("ForsikringsvurderingResultat").isObject
                },
        )
    }

    protected fun spleisSenderBehovForForsikringsvurderingResultat(
        vedtaksperiode: Sykefraværstilfelle.Vedtaksperiode,
        forsikringsvurderingId: String,
    ): JsonNode {
        val forsikringsvurderingResultatBehovMelding =
            lagForsikringsvurderingResultatBehovMelding(vedtaksperiode, forsikringsvurderingId)
        publiserMeldingOgVentTilDenErBehandlet(forsikringsvurderingResultatBehovMelding)
        return forsikringsvurderingResultatBehovMelding
    }

    protected fun detBlirPublisertEnSubsumsjonsmeldingForSykefraværstilfellet(
        referansedel: String,
        forsikringsvurderingId: String,
    ) {
        val subsumsjonMelding =
            rapid.konsumerMelding {
                it.path("fødselsnummer").asString() == testPerson.identitetsnummer &&
                    it.path("@event_name").stringValue() == "subsumsjon"
            }
        assertJsonEquals(
            expectedJson =
                """
                {
                  "@event_name" : "subsumsjon",
                  "fødselsnummer" : "${testPerson.identitetsnummer}",
                  "subsumsjon" : {
                    "eventName" : "subsumsjon",
                    "versjon" : "1.1.0",
                    "kilde" : "sp-forsikring",
                    "versjonAvKode" : "navikt/sp-forsikring:latest",
                    "fodselsnummer" : "${testPerson.identitetsnummer}",
                    "vedtaksperiodeId" : "${førsteVedtaksperiode.vedtaksperiodeId}",
                    "behandlingId" : "${førsteVedtaksperiode.behandlingId}",
                    "sporing" : {
                      "vedtaksperiode" : [ "${førsteVedtaksperiode.vedtaksperiodeId}" ]
                    },
                    "lovverk" : "folketrygdloven",
                    $referansedel,
                    "input" : {
                      "skjæringstidspunkt" : "${sykefraværstilfelle.skjæringstidspunkt}",
                      "yrkesaktivitetstype" : "$yrkesaktivitetstype",
                      "spesielleYrkesgrupper" : [ ${spesiellYrkesgruppe?.let { "\"$it\"" }.orEmpty()} ]
                    },
                    "output" : {
                      "forsikringsvurderingId" : "$forsikringsvurderingId"
                    },
                    "utfall" : "VILKAR_BEREGNET"
                  }
                }
                """.trimIndent(),
            actualJsonNode = subsumsjonMelding,
            bortsettFraStier =
                setOf(
                    "@id",
                    "@opprettet",
                    "@opprettetUTC",
                    "@forårsaket_av",
                    "subsumsjon.id",
                    "subsumsjon.tidsstempel",
                ),
        )
    }

    protected fun detBlirPublisertEnForsikringsvurderingLøsning(): String {
        val forsikringsvurderingLøsningMelding =
            rapid.konsumerMelding {
                it.path("fødselsnummer").asString() == testPerson.identitetsnummer &&
                    it.path("@event_name").stringValue() == "behov" &&
                    it.path("@løsning").path("Forsikringsvurdering").isObject
            }
        val forsikringsvurderingId =
            runCatching {
                forsikringsvurderingLøsningMelding["@løsning"]["Forsikringsvurdering"]["forsikringsvurderingId"].stringValue()
            }.getOrElse {
                fail(
                    "Fikk ikke tak i forsikringsvurderingId " +
                        "i forventet løsningsmelding på Forsikringsvurdering-behov:\n" +
                        forsikringsvurderingLøsningMelding.toPrettyString(),
                    it,
                )
            }
        val forsikringsvurderingBehovMelding =
            rapid.konsumerMelding {
                it.path("fødselsnummer").asString() == testPerson.identitetsnummer &&
                    it.path("@event_name").stringValue() == "behov" &&
                    it.path("@løsning").path("Forsikringsvurdering").isMissingNode
            }
        assertLøsningPåBehov(
            forventetLøsning = """{ "forsikringsvurderingId" : "$forsikringsvurderingId" }""",
            behovMelding = forsikringsvurderingBehovMelding,
            faktiskLøsningMelding = forsikringsvurderingLøsningMelding,
        )
        return forsikringsvurderingId
    }

    protected fun spleisSenderBehovForForsikringsvurdering() {
        publiserMeldingOgVentTilDenErBehandlet(lagForsikringsvurderingBehovMelding())
    }

    protected fun flexSjekkerOmDetErNoeVitsIÅSøkeIVentetiden(forventetSvar: Boolean) {
        assertJsonEquals(
            expectedJson = """{ "harForsikringMedDekningIVentetid": $forventetSvar }""",
            actualJsonNode = postFlexForsikringsvurdering(),
        )
    }

    protected fun brukerenHarEnBetaltForsikringIInfotrygd(
        virkningsdato: LocalDate,
        infotrygdType: Char,
        premiegrunnlag: Int,
    ) {
        TestcontainersReplikadatabase.opprettBetaltForsikring(
            identitetsnummer = Identitetsnummer.fraString(testPerson.identitetsnummer),
            virkningsdato = virkningsdato,
            type = infotrygdType,
            premiegrunnlag = premiegrunnlag,
        )
    }

    protected fun utbetalingsstatistikkenForIÅrErTom() {
        val iÅr = LocalDate.now().year
        val fom = 1 jan iÅr
        val tom = 31 des iÅr
        assertJsonEquals(
            expectedJsonNode = tomUtbetalingsstatistikk(fom, tom),
            actualJsonNode = getUtbetalingsstatistikk(fom = fom, tom = tom),
        )
    }

    protected fun utbetalingsstatistikkenForIÅrErTomBortsettFra(
        @Language("JSON") vararg oppdatertStatistikkobjekt: String,
    ) {
        val iÅr = LocalDate.now().year
        val fom = 1 jan iÅr
        val tom = 31 des iÅr

        val overrides = oppdatertStatistikkobjekt.map { jacksonObjectMapper().readTree(it) }
        assertJsonEquals(
            expectedJsonNode =
                tomUtbetalingsstatistikk(fom, tom).apply {
                    this["perForsikringstype"].asArray().apply {
                        overrides.forEach { override ->
                            val index = indexOfFirst { it["navn"].stringValue() == override["navn"].stringValue() }
                            check(index >= 0) { "Forsikringstypen ${override["navn"].stringValue()} matcher ikke tom utbetalingsstatistikk" }
                            set(index, override)
                        }
                    }
                },
            actualJsonNode = getUtbetalingsstatistikk(fom = fom, tom = tom),
        )
    }

    private fun tomUtbetalingsstatistikk(
        fom: LocalDate,
        tom: LocalDate,
    ): JsonNode =
        jacksonObjectMapper().readTree(
            // language=json
            """
            {
              "fom" : "$fom",
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
              "tom" : "$tom"
            }
            """.trimIndent(),
        )

    private fun assertLøsningPåBehov(
        @Language("JSON") forventetLøsning: String,
        behovMelding: JsonNode,
        faktiskLøsningMelding: JsonNode,
    ) {
        val behov = behovMelding["@behov"].toList().single().stringValue()
        assertJsonEquals(
            expectedJsonNode = behovMelding,
            actualJsonNode = faktiskLøsningMelding,
            bortsettFraStier =
                setOf(
                    "@id",
                    "@opprettet",
                    "@opprettetUTC",
                    "@forårsaket_av",
                    "system_participating_services",
                    "system_read_count",
                    "@løsning",
                ),
        )
        assertJsonEquals(
            expectedJson = forventetLøsning,
            actualJsonNode = faktiskLøsningMelding["@løsning"][behov],
        )
        // Ingen andre løsninger i løsning-objektet
        assertJsonEquals(
            expectedJson = "{}",
            actualJsonNode = faktiskLøsningMelding["@løsning"],
            bortsettFraStier = setOf(behov),
        )
    }
}
