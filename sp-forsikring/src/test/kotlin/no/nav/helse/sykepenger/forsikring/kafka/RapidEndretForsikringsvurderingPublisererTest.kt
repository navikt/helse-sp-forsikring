package no.nav.helse.sykepenger.forsikring.kafka

import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

internal class RapidEndretForsikringsvurderingPublisererTest {
    private val publiserer = RapidEndretForsikringsvurderingPubliserer
    private val outboxRepository =
        object : OutboxRepository {
            val outbox = mutableListOf<OutboxRepository.OutboxKonvolutt>()

            override fun hent(): OutboxRepository.OutboxKonvolutt? = outbox.maxByOrNull { it.id }

            override fun leggTil(
                key: String,
                melding: String,
            ) {
                outbox.add(OutboxRepository.OutboxKonvolutt(outbox.size.toLong(), key, melding))
            }

            override fun fjern(id: Long) {
                outbox.removeIf { it.id == id }
            }
        }

    @Test
    fun `publiserer melding med forventet innhold`() {
        val forsikringsvurderingId = UUID.randomUUID()

        val identitetsnummer = Identitetsnummer("01020312345")
        publiserer.publiser(
            outboxRepository = outboxRepository,
            identitetsnummer = identitetsnummer,
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            forsikringsvurderingId = forsikringsvurderingId,
        )

        assertEquals(1, outboxRepository.outbox.size)

        val konvolutt = outboxRepository.outbox[0]
        val melding = konvolutt.melding.somJson()
        assertEquals(identitetsnummer.value, konvolutt.key)
        assertEquals("endret_forsikringsvurdering", melding["@event_name"].asString())
        assertEquals(identitetsnummer.value, melding["fødselsnummer"].asString())
        assertEquals("2026-01-01", melding["skjæringstidspunkt"].asString())
        assertEquals(forsikringsvurderingId.toString(), melding["forsikringsvurderingId"].asString())
    }
}

private val testJsonMapper = jacksonObjectMapper()

private fun String.somJson(): JsonNode = testJsonMapper.readTree(this)
