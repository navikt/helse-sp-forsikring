package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import javax.sql.DataSource
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagId
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagIfVedrift10Id
import no.nav.helse.sykepenger.forsikring.withSession
import org.intellij.lang.annotations.Language

class ForsikringsvurderingRepository(private val dataSource: DataSource) : IForsikringsvurderingRepository {

    override fun lagre(forsikringsvurdering: Forsikringsvurdering, session: TransactionalSession) {
        lagreForsikringsvurdering(forsikringsvurdering, session)
        forsikringsvurdering.ekskluderinger.forEach { ekskludering ->
            lagreEkskludering(forsikringsvurdering.id, ekskludering, session)
        }
    }

    override fun hent(id: ForsikringsvurderingId): Forsikringsvurdering? {
        return dataSource.withSession { session ->
            @Language("PostgreSQL")
            val statement = "SELECT * FROM forsikringsvurdering WHERE id = :id"
            session.run(
                queryOf(statement, mapOf("id" to id.value))
                    .map { row ->
                        Forsikringsvurdering.fraLagring(
                            id = id,
                            oppslagId = OppslagId(row.uuid("oppslag_id")),
                            behovJson = row.string("behov"),
                            ekskluderinger = hentEkskluderinger(id, session),
                            harForsikring = row.boolean("har_forsikring"),
                            dekning = row.intOrNull("dekning_grad")?.let { grad ->
                                Forsikringsvurdering.Dekning(
                                    iVentetid = row.anyOrNull("dekning_i_ventetid") as Boolean,
                                    grad = grad,
                                )
                            },
                            opphørsdato = row.localDateOrNull("opphørsdato"),
                        )
                    }
                    .asSingle
            )
        }
    }

    private fun hentEkskluderinger(
        forsikringsvurderingId: ForsikringsvurderingId,
        session: kotliquery.Session,
    ): List<Forsikringsvurdering.EkskluderingNavKjøptForsikring> {
        @Language("PostgreSQL")
        val statement = """
            SELECT *
            FROM forsikringsvurdering_ekskludering_navkjopt_forsikring
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
        """
        return session.run(
            queryOf(statement, mapOf("forsikringsvurdering_id" to forsikringsvurderingId.value))
                .map { row ->
                    Forsikringsvurdering.EkskluderingNavKjøptForsikring(
                        oppslagIfVedfrivt10Id = OppslagIfVedrift10Id(row.uuid("oppslag_IF_VEDFRIVT_10_id")),
                        ekskluderingsårsak = enumValueOf(row.string("ekskluderingsaarsak")),
                    )
                }
                .asList
        )
    }

    private fun lagreForsikringsvurdering(
        forsikringsvurdering: Forsikringsvurdering,
        session: TransactionalSession,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering (id, oppslag_id, behov, har_forsikring, dekning_i_ventetid, dekning_grad, opphørsdato)
            VALUES (:id, :oppslag_id, :behov::jsonb, :har_forsikring, :dekning_i_ventetid, :dekning_grad, :opphorsdato)
        """
        session.run(
            queryOf(
                statement,
                mapOf(
                    "id" to forsikringsvurdering.id.value,
                    "oppslag_id" to forsikringsvurdering.oppslagId.value,
                    "behov" to forsikringsvurdering.behovJson,
                    "har_forsikring" to forsikringsvurdering.harForsikring,
                    "dekning_i_ventetid" to forsikringsvurdering.dekning?.iVentetid,
                    "dekning_grad" to forsikringsvurdering.dekning?.grad,
                    "opphorsdato" to forsikringsvurdering.opphørsdato
                )
            ).asUpdate
        )
    }

    private fun lagreEkskludering(
        forsikringsvurderingId: ForsikringsvurderingId,
        ekskludering: Forsikringsvurdering.EkskluderingNavKjøptForsikring,
        session: TransactionalSession,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_ekskludering_navkjopt_forsikring
                (forsikringsvurdering_id, oppslag_IF_VEDFRIVT_10_id, ekskluderingsaarsak)
            VALUES
                (:forsikringsvurdering_id, :oppslag_IF_VEDFRIVT_10_id, :ekskluderingsaarsak)
        """
        session.run(
            queryOf(
                statement,
                mapOf(
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "oppslag_IF_VEDFRIVT_10_id" to ekskludering.oppslagIfVedfrivt10Id.value,
                    "ekskluderingsaarsak" to ekskludering.ekskluderingsårsak.name,
                )
            ).asUpdate
        )
    }
}
