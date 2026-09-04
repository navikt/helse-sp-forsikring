package no.nav.helse.sykepenger.forsikring.subsumsjon

import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertIndividuellForsikring
import no.nav.sykepenger.libs.testing.assertions.assertJsonEquals
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.util.*
import kotlin.test.Test

internal class SubsumsjonsmeldingerTest {
    @ParameterizedTest(name = "{0}-forsikring sender § 8-36 ledd 1 bokstav {1}", quoteTextArguments = false)
    @CsvSource(
        "SELVSTENDIG_80_PROSENT_FRA_DAG_1, a",
        "SELVSTENDIG_100_PROSENT_FRA_DAG_17, b",
        "SELVSTENDIG_100_PROSENT_FRA_DAG_1, c",
    )
    fun `individuelle forsikring for selvstendig næringsdrivende`(
        type: IndividuellForsikringType,
        forventetBokstav: Char,
    ) {
        // Given
        val identitetsnummer = Identitetsnummer.fraString(lagIdentitetsnummer())
        val vedtaksperiodeId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-07-01"),
                            type = type,
                        ),
                    ),
            )

        // When:
        val meldinger =
            forsikringsvurdering.tilSubsumsjonsmeldinger(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                versjonAvKode = "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
            )

        // Then:
        assertEquals(1, meldinger.size)
        val melding = meldinger.single()

        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${melding.id}",
                    "@opprettet": "${melding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${melding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${melding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${melding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-10-01",
                        "paragraf": "8-36",
                        "ledd": 1,
                        "bokstav": "$forventetBokstav",
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "SELVSTENDIG",
                            "spesielleYrkesgrupper": []
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = melding.tilJson(),
        )
    }

    @Test
    fun `FRILANSER_100_PROSENT_FRA_DAG_1-forsikring sender § 8-39 ledd 1 punktum 1`() {
        // Given
        val identitetsnummer = Identitetsnummer.fraString(lagIdentitetsnummer())
        val vedtaksperiodeId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS,
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-07-01"),
                            type = IndividuellForsikringType.FRILANSER_100_PROSENT_FRA_DAG_1,
                        ),
                    ),
            )

        // When:
        val meldinger =
            forsikringsvurdering.tilSubsumsjonsmeldinger(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                versjonAvKode = "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
            )

        // Then:
        assertEquals(1, meldinger.size)
        val melding = meldinger.single()

        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${melding.id}",
                    "@opprettet": "${melding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${melding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${melding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${melding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-01-01",
                        "paragraf": "8-39",
                        "ledd": 1,
                        "punktum": 1,
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "FRILANS",
                            "spesielleYrkesgrupper": []
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = melding.tilJson(),
        )
    }

    @ParameterizedTest(
        name = "Kollektiv {0}-forsikring for {1} sender § 8-36 ledd 1 bokstav {2} og ledd 4",
        quoteTextArguments = false,
    )
    @CsvSource(
        "FISKER_BLAD_B, FISKER_BLAD_B, c",
        "JORDBRUKER, JORDBRUKER, b",
        "JORDBRUKER, REINDRIFTER, b",
    )
    fun `kollektiv forsikring`(
        type: KollektivForsikring,
        spesiellYrkesgruppe: SpesiellYrkesgruppe,
        forventetBokstav: Char,
    ) {
        // Given
        val identitetsnummer = Identitetsnummer.fraString(lagIdentitetsnummer())
        val vedtaksperiodeId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                identitetsnummer = identitetsnummer,
                spesielleYrkesgrupper = setOf(spesiellYrkesgruppe),
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer = emptyList(),
                kollektivForsikring = type,
            )

        // When:
        val meldinger =
            forsikringsvurdering.tilSubsumsjonsmeldinger(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                versjonAvKode = "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
            )

        // Then:
        assertEquals(2, meldinger.size)
        val kollektivMelding = meldinger.first()
        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${kollektivMelding.id}",
                    "@opprettet": "${kollektivMelding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${kollektivMelding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${kollektivMelding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${kollektivMelding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-10-01",
                        "paragraf": "8-36",
                        "ledd": 4,
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "SELVSTENDIG",
                            "spesielleYrkesgrupper": ["$spesiellYrkesgruppe"]
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = kollektivMelding.tilJson(),
        )
        val dekningMelding = meldinger.last()
        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${dekningMelding.id}",
                    "@opprettet": "${dekningMelding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${dekningMelding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${dekningMelding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${dekningMelding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-10-01",
                        "paragraf": "8-36",
                        "ledd": 1,
                        "bokstav": "$forventetBokstav",
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "SELVSTENDIG",
                            "spesielleYrkesgrupper": ["$spesiellYrkesgruppe"]
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = dekningMelding.tilJson(),
        )
    }

    @Test
    fun `jordbruker med tilleggsforsikring sender § 8-36 ledd 1 bokstav c og ledd 4`() {
        // Given
        val identitetsnummer = Identitetsnummer.fraString(lagIdentitetsnummer())
        val vedtaksperiodeId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                identitetsnummer = identitetsnummer,
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            virkningsdato = LocalDate.parse("2025-07-01"),
                            type = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                        ),
                    ),
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
            )

        // When:
        val meldinger =
            forsikringsvurdering.tilSubsumsjonsmeldinger(
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                versjonAvKode = "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
            )

        // Then:
        assertEquals(2, meldinger.size)
        val kollektivMelding = meldinger.first()
        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${kollektivMelding.id}",
                    "@opprettet": "${kollektivMelding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${kollektivMelding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${kollektivMelding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${kollektivMelding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-10-01",
                        "paragraf": "8-36",
                        "ledd": 4,
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "SELVSTENDIG",
                            "spesielleYrkesgrupper": ["JORDBRUKER"]
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = kollektivMelding.tilJson(),
        )
        val dekningMelding = meldinger.last()
        assertJsonEquals(
            expectedJson =
                """
                {
                    "@event_name": "subsumsjon",
                    "@id": "${dekningMelding.id}",
                    "@opprettet": "${dekningMelding.opprettet.toString().trimEnd('0')}",
                    "@opprettetUTC": "${dekningMelding.opprettetUTC}",
                    "fødselsnummer": "${identitetsnummer.value}",
                    "subsumsjon": {
                        "id": "${dekningMelding.id}",
                        "eventName": "subsumsjon",
                        "tidsstempel": "${dekningMelding.opprettet.toString().trimEnd('0')}",
                        "versjon": "1.1.0",
                        "kilde": "sp-forsikring",
                        "versjonAvKode": "docker.pkg.github.com/navikt/helse-sp-forsikring:abc123",
                        "fodselsnummer": "${identitetsnummer.value}",
                        "vedtaksperiodeId": "$vedtaksperiodeId",
                        "behandlingId": "$behandlingId",
                        "sporing": {
                            "vedtaksperiode": [ "$vedtaksperiodeId" ]
                        },
                        "lovverk": "folketrygdloven",
                        "lovverksversjon": "2019-10-01",
                        "paragraf": "8-36",
                        "ledd": 1,
                        "bokstav": "c",
                        "input": {
                            "skjæringstidspunkt": "2026-01-01",
                            "yrkesaktivitetstype": "SELVSTENDIG",
                            "spesielleYrkesgrupper": ["JORDBRUKER"]
                        },
                        "output": { "forsikringsvurderingId": "${forsikringsvurdering.id.value}" },
                        "utfall": "VILKAR_BEREGNET"
                    }
                }
                """.trimIndent(),
            actualJson = dekningMelding.tilJson(),
        )
    }
}
