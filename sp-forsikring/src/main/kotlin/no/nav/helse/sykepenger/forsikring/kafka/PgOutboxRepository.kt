package no.nav.helse.sykepenger.forsikring.kafka

import kotliquery.TransactionalSession
import kotliquery.queryOf

class PgOutboxRepository(
    private val transaction: TransactionalSession,
) : OutboxRepository {
    override fun leggTil(
        key: String,
        melding: String,
    ) {
        transaction.run(
            queryOf(
                // language=postgresql
                """
                INSERT INTO outbox(key, melding) 
                VALUES (:key, :melding::jsonb);
                """.trimIndent(),
                mapOf(
                    "key" to key,
                    "melding" to melding,
                ),
            ).asUpdate,
        )
    }

    fun pop(): OutboxKonvolutt? {
        val konvolutt = hent() ?: return null
        fjern(konvolutt.id)
        return konvolutt
    }

    override fun hent(): OutboxKonvolutt? =
        transaction.run(
            queryOf(
                // language=postgresql
                """
                SELECT * 
                FROM outbox
                ORDER BY id 
                LIMIT 1
                FOR UPDATE;
                """.trimIndent(),
            ).map { row ->
                OutboxKonvolutt(
                    id = row.long("id"),
                    key = row.string("key"),
                    melding = row.string("melding"),
                )
            }.asSingle,
        )

    override fun fjern(id: Long) {
        transaction.run(
            queryOf(
                // language=postgresql
                """
                DELETE FROM outbox
                WHERE id = :id
                """.trimIndent(),
                mapOf("id" to id),
            ).asUpdate,
        )
    }

    data class OutboxKonvolutt(
        val id: Long,
        val key: String,
        val melding: String,
    )
}
