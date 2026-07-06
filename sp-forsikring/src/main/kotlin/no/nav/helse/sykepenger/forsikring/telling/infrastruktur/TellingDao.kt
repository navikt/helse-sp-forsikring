package no.nav.helse.sykepenger.forsikring.telling.infrastruktur

import java.time.Instant
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.shared.util.withSession
import org.intellij.lang.annotations.Language

class TellingDao(private val dataSource: DataSource) {
    fun lagre(
        id: UUID,
        fødselsnummer: String,
        vedtaksperiodeId: UUID,
        vedtakFattetTidspunkt: Instant,
        dekningsgrad: Int,
        harDekningIVentetid: Boolean,
        utbetaltIVentetid: Int,
        utbetaltUtenomVentetid: Int,
        json: String,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO tell_utbetaling (id, fødselsnummer, vedtaksperiodeId, vedtakFattetTidspunkt, dekningsgrad, harDekningIVentetid, utbetaltIVentetid, utbetaltUtenomVentetid, json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING;
        """.trimIndent()
        dataSource.withSession { session ->
            session.run(
                queryOf(
                    statement,
                    id,
                    fødselsnummer,
                    vedtaksperiodeId,
                    vedtakFattetTidspunkt,
                    dekningsgrad,
                    harDekningIVentetid,
                    utbetaltIVentetid,
                    utbetaltUtenomVentetid,
                    json
                ).asUpdate
            )
        }
    }

    fun hent(id: UUID): LagretTelling? {
        @Language("PostgreSQL")
        val statement = """
            SELECT
                id,
                fødselsnummer,
                vedtaksperiodeId,
                vedtakFattetTidspunkt,
                dekningsgrad,
                harDekningIVentetid,
                utbetaltIVentetid,
                utbetaltUtenomVentetid
            FROM tell_utbetaling
            WHERE id = ?
        """.trimIndent()

        return dataSource.withSession { session ->
            session.run(
                queryOf(statement, id)
                    .map { row ->
                        LagretTelling(
                            id = UUID.fromString(row.string("id")),
                            fødselsnummer = row.string("fødselsnummer"),
                            vedtaksperiodeId = UUID.fromString(row.string("vedtaksperiodeId")),
                            vedtakFattetTidspunkt = row.instant("vedtakFattetTidspunkt"),
                            dekningsgrad = row.int("dekningsgrad"),
                            harDekningIVentetid = row.boolean("harDekningIVentetid"),
                            utbetaltIVentetid = row.int("utbetaltIVentetid"),
                            utbetaltUtenomVentetid = row.int("utbetaltUtenomVentetid"),
                        )
                    }.asSingle
            )
        }
    }
}

data class LagretTelling(
    val id: UUID,
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val vedtakFattetTidspunkt: Instant,
    val dekningsgrad: Int,
    val harDekningIVentetid: Boolean,
    val utbetaltIVentetid: Int,
    val utbetaltUtenomVentetid: Int,
)
