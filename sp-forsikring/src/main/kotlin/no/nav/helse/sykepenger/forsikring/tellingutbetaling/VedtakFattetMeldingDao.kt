package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.util.*

class VedtakFattetMeldingDao(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun eksisterer(id: UUID): Boolean {
        @Language("PostgreSQL")
        val statement = """
            SELECT EXISTS(SELECT 1 FROM vedtak_fattet_melding WHERE id = :id)
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("id" to id))
                .map { row -> row.boolean(1) }
                .asSingle,
        ) == true
    }

    fun insert(
        id: UUID,
        forsikringsvurderingId: Forsikringsvurdering.Id?,
        identitetsnummer: Identitetsnummer,
        behandlingId: UUID,
        vedtakFattetTidspunkt: Instant,
        json: String,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO vedtak_fattet_melding (id, forsikringsvurdering_id, identitetsnummer, behandling_id,
                                               vedtak_fattet_tidspunkt, json)
            VALUES (:id, :forsikringsvurdering_id, :identitetsnummer, :behandling_id,
                    :vedtak_fattet_tidspunkt, :json::jsonb)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to id,
                    "forsikringsvurdering_id" to forsikringsvurderingId?.value,
                    "identitetsnummer" to identitetsnummer.value,
                    "behandling_id" to behandlingId,
                    "vedtak_fattet_tidspunkt" to vedtakFattetTidspunkt,
                    "json" to json,
                ),
            ).asUpdate,
        )
    }
}
