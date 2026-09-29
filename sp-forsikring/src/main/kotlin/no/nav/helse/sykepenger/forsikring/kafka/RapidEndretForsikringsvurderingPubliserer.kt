package no.nav.helse.sykepenger.forsikring.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.sykepenger.libs.logging.loggInfo
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

object RapidEndretForsikringsvurderingPubliserer : EndretForsikringsvurderingPubliserer {
    override fun publiser(
        outboxRepository: OutboxRepository,
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
        forsikringsvurderingId: UUID,
    ) {
        val melding =
            EndretForsikringsvurderingMelding(
                fødselsnummer = identitetsnummer.value,
                skjæringstidspunkt = skjæringstidspunkt.toString(),
                forsikringsvurderingId = forsikringsvurderingId.toString(),
            ).tilJson()

        loggInfo(
            "Legger endret forsikringsvurdering melding i outbox",
            "melding" to melding,
        )
        outboxRepository.leggTil(identitetsnummer.value, melding)
    }
}

data class EndretForsikringsvurderingMelding(
    val fødselsnummer: String,
    val skjæringstidspunkt: String,
    val forsikringsvurderingId: String,
) {
    @JsonProperty("@event_name")
    val event_name: String = "endret_forsikringsvurdering"

    @JsonProperty("@id")
    val id: String = UUID.randomUUID().toString()

    @JsonProperty("@opprettetUTC")
    val opprettetUTC: Instant = Instant.now()

    @JsonProperty("@opprettet")
    val opprettet: LocalDateTime = opprettetUTC.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()

    fun tilJson(): String = objectMapper.writeValueAsString(this)

    companion object {
        private val objectMapper =
            jacksonMapperBuilder()
                .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
                .build()
    }
}
