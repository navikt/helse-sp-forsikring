package no.nav.helse.sykepenger.forsikring.kafka

interface OutboxRepository {
    fun popFirst(): OutboxKonvolutt?

    fun push(konvolutt: OutboxKonvolutt)

    fun push(
        key: String,
        melding: String,
    ) = push(OutboxKonvolutt(key = key, melding = melding))

    data class OutboxKonvolutt(
        val key: String,
        val melding: String,
    )
}
