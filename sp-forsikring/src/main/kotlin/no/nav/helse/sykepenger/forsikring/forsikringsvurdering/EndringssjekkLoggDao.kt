package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.util.*

class EndringssjekkLoggDao(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun insert(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        utførtAvSaksbehandlerIdent: String,
        tidspunkt: Instant,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO endringssjekk_logg (id, forsikringsvurdering_id, utfort_av_saksbehandler_ident, tidspunkt)
            VALUES (:id, :forsikringsvurdering_id, :utfort_av_saksbehandler_ident, :tidspunkt)
             """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to UUID.randomUUID(),
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "utfort_av_saksbehandler_ident" to utførtAvSaksbehandlerIdent,
                    "tidspunkt" to tidspunkt,
                ),
            ).asUpdate,
        )
    }

    fun hentSistHentet(forsikringsvurderingId: Forsikringsvurdering.Id): EndringssjekkLogg? {
        @Language("PostgreSQL")
        val statement = """
            SELECT id, tidspunkt, utfort_av_saksbehandler_ident 
            FROM endringssjekk_logg
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
            ORDER BY tidspunkt DESC 
            LIMIT 1
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("forsikringsvurdering_id" to forsikringsvurderingId.value))
                .map { row ->
                    EndringssjekkLogg(
                        row.uuid("id"),
                        forsikringsvurderingId,
                        row.string("utfort_av_saksbehandler_ident"),
                        row.instant("tidspunkt"),
                    )
                }.asSingle,
        )
    }
}
