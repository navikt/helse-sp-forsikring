package no.nav.helse.sykepenger.forsikring.kafka

interface OutboxRepository {
    fun hent(): PgOutboxRepository.OutboxKonvolutt?

    fun leggTil(
        key: String,
        melding: String,
    )

    fun fjern(id: Long)
}
