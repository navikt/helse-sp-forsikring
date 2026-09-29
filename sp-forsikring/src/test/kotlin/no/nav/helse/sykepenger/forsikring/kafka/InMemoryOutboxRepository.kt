package no.nav.helse.sykepenger.forsikring.kafka

import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

class InMemoryOutboxRepository : OutboxRepository {
    private val outbox = mutableListOf<OutboxRepository.OutboxKonvolutt>()

    @Synchronized
    override fun popFirst(): OutboxRepository.OutboxKonvolutt? = outbox.removeFirstOrNull()

    @Synchronized
    override fun push(konvolutt: OutboxRepository.OutboxKonvolutt) {
        outbox.add(konvolutt)
    }

    @Synchronized
    fun alle() = outbox.toList()

    @Synchronized
    fun meldinger(): List<JsonNode> = outbox.map { objectMapper.readTree(it.melding) }

    @Synchronized
    fun tøm() {
        outbox.clear()
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
