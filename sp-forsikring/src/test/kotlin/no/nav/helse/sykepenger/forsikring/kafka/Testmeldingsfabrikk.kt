package no.nav.helse.sykepenger.forsikring.kafka

import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object Testmeldingsfabrikk {
    private val objectMapper = jacksonObjectMapper()

    fun lagVedtakFattetMelding(
        eventName: String,
        yrkesaktivitetstype: String,
        fødselsnummer: String,
        behandlingId: UUID,
        førstegangsbehandling: Boolean,
        sykepengegrunnlag: Int,
        skjæringstidspunkt: LocalDate,
        forsikringsvurderingId: String?,
        vedtakFattetTidspunkt: LocalDateTime,
        utbetalingsdagerFom: LocalDate,
        utbetalingsdagerTom: LocalDate,
        utbetalingsdagerUtbetalingIVentetid: Boolean,
        utbetalingsdagerDekningsgrad: Int,
        utbetalingsdagerEndretDekningsgradFraOgMed: LocalDate?,
        utbetalingsdagerEndretDekningsgrad: Int?,
        utbetalingsdagerBeløpTilBruker: Int,
    ): JsonNode {
        val meldingId = UUID.randomUUID()
        val testmelding =
            """
                {
                  "@event_name": "$eventName",
                  "yrkesaktivitetstype": "$yrkesaktivitetstype",
                  "@id": "$meldingId",
                  "fødselsnummer": "$fødselsnummer",
                  "behandlingId": "$behandlingId",
                  "tags": [ ${"\"Førstegangsbehandling\"".takeIf { førstegangsbehandling }.orEmpty()} ],
                  "sykepengegrunnlag": ${BigDecimal.valueOf(sykepengegrunnlag.toLong()).setScale(1)},
                  "skjæringstidspunkt": "$skjæringstidspunkt",
                  ${forsikringsvurderingId?.let { """"forsikringsvurderingId": "$it",""" }.orEmpty()}
                  "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
                  "utbetalingsdager": ${
                generateSequence(utbetalingsdagerFom) { dato ->
                    dato.plusDays(1L).takeUnless { it > utbetalingsdagerTom }
                }
                    .joinToString(prefix = "[", separator = ",", postfix = "]") { dato ->
                        val erVentetid = dato < skjæringstidspunkt.plusDays(16)
                        val erHelg = dato.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        val type =
                            when {
                                erVentetid -> "Ventetidsdag"
                                erHelg -> "NavHelgDag"
                                else -> "NavDag"
                            }
                        val beløpTilBruker =
                            when {
                                erHelg -> 0
                                erVentetid && !utbetalingsdagerUtbetalingIVentetid -> 0
                                else -> utbetalingsdagerBeløpTilBruker
                            }
                        val dekningsgrad =
                            if (utbetalingsdagerEndretDekningsgradFraOgMed != null && dato >= utbetalingsdagerEndretDekningsgradFraOgMed) {
                                checkNotNull(utbetalingsdagerEndretDekningsgrad) {
                                    "Må angi utbetalingsdagerEndretDekningsgrad når utbetalingsdagerEndretDekningsgradFraOgMed er satt"
                                }
                            } else {
                                utbetalingsdagerDekningsgrad
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
                  "extraFields": { "willBe": "ignored" }
                }
            """.trimIndent()
        return objectMapper.readTree(testmelding)
    }
}
