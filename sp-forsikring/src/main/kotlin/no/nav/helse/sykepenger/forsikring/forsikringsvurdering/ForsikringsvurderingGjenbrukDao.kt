package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.util.*

class ForsikringsvurderingGjenbrukDao(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun insert(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        behov: String,
        opprettet: Instant,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_gjenbruk (id, forsikringsvurdering_id, behov, opprettet)
            VALUES (:id, :forsikringsvurdering_id, :behov::jsonb, :opprettet)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to UUID.randomUUID(),
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "behov" to behov,
                    "opprettet" to opprettet,
                ),
            ).asUpdate,
        )
    }
}
