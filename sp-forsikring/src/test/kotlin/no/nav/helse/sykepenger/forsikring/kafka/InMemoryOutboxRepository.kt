package no.nav.helse.sykepenger.forsikring.kafka

class InMemoryOutboxRepository : OutboxRepository {
    private val outbox = mutableListOf<OutboxRepository.OutboxKonvolutt>()

    override fun popFirst(): OutboxRepository.OutboxKonvolutt? = outbox.firstOrNull()

    override fun push(konvolutt: OutboxRepository.OutboxKonvolutt) {
        outbox.add(konvolutt)
    }

    fun alle() = outbox.toList()
}
