package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.sykepenger.forsikring.kafka.lib.LenientEnum
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class VedtakFattetMelding(
    @JsonProperty("@id")
    val id: UUID,
    val fødselsnummer: String,
    val behandlingId: UUID,
    val forsikringsvurderingId: UUID?,
    val vedtakFattetTidspunkt: LocalDateTime,
    val utbetalingsdager: List<Utbetalingsdag>,
) {
    data class Utbetalingsdag(
        val type: LenientEnum<Type>,
        val beløpTilBruker: Int,
        val dato: LocalDate,
        val dekningsgrad: Int,
    ) {
        enum class Type {
            Ventetidsdag,
        }
    }
}
