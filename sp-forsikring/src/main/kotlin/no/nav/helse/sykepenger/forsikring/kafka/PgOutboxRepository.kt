package no.nav.helse.sykepenger.forsikring.kafka

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.kafka.OutboxRepository.OutboxKonvolutt

class PgOutboxRepository(
    private val transaction: TransactionalSession,
) : OutboxRepository {
    override fun push(konvolutt: OutboxKonvolutt) {
        transaction.run(
            queryOf(
                // language=postgresql
                """
                INSERT INTO outbox(key, melding) 
                VALUES (:key, :melding::jsonb);
                """.trimIndent(),
                mapOf(
                    "key" to konvolutt.key,
                    "melding" to konvolutt.melding,
                ),
            ).asUpdate,
        )
    }

    override fun popFirst(): OutboxKonvolutt? {
        val (id, konvolutt) =
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
                    row.long("id") to
                        OutboxKonvolutt(
                            key = row.string("key"),
                            melding = row.string("melding"),
                        )
                }.asSingle,
            ) ?: return null

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

        return konvolutt
    }
}
