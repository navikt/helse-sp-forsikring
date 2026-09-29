package no.nav.helse.sykepenger.forsikring.kafka

interface OutboxRepository {
    fun pop(): OutboxKonvolutt?

    fun leggTil(konvolutt: OutboxKonvolutt)

    fun leggTil(
        key: String,
        melding: String,
    ) = leggTil(OutboxKonvolutt(key = key, melding = melding))

    data class OutboxKonvolutt(
        val key: String,
        val melding: String,
    )
}
