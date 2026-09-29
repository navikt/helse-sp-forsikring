package no.nav.helse.sykepenger.forsikring.kafka

interface OutboxRepository {
    fun hent(): OutboxKonvolutt?

    fun leggTil(
        key: String,
        melding: String,
    )

    fun fjern(id: Long)

    data class OutboxKonvolutt(
        val id: Long,
        val key: String,
        val melding: String,
    )
}
