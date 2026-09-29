package no.nav.helse.sykepenger.forsikring.kafka

class InMemoryOutboxRepository : OutboxRepository {
    private val outbox = mutableListOf<OutboxRepository.OutboxKonvolutt>()

    override fun pop(): OutboxRepository.OutboxKonvolutt? = outbox.firstOrNull()

    override fun leggTil(konvolutt: OutboxRepository.OutboxKonvolutt) {
        outbox.add(konvolutt)
    }

    fun alle() = outbox.toList()
}
