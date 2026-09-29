package no.nav.helse.sykepenger.forsikring.kafka

import kotliquery.TransactionalSession

fun interface OutboxRepositoryFactory {
    fun lag(transaction: TransactionalSession): OutboxRepository
}
