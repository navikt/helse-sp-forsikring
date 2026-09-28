package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.ForsikringsvurderingInput
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi.Id
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import org.intellij.lang.annotations.Language
import java.time.LocalDate

class ForsikringsvurderingRepository(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun lagre(
        forsikringsvurdering: Forsikringsvurdering,
        behovEllerRequestBody: String,
    ) {
        lagreForsikringsvurdering(forsikringsvurdering, behovEllerRequestBody)
        forsikringsvurdering.input.spesielleYrkesgrupper.forEach { spesiellYrkesgruppe ->
            lagreSpesiellYrkesgruppe(forsikringsvurdering.id, spesiellYrkesgruppe)
        }
        forsikringsvurdering.individuelleForsikringer.forEach { individuellForsikring ->
            lagreIndividuellForsikring(forsikringsvurdering.id, individuellForsikring)
        }
    }

    fun hent(id: Forsikringsvurdering.Id): Forsikringsvurdering? {
        val spesielleYrkesgrupper = hentSpesielleYrkesgrupper(id)
        val individuelleForsikringer = hentIndividuelleForsikringer(id)

        @Language("PostgreSQL")
        val statement = """
            SELECT råkopi_id,
                   identitetsnummer,
                   yrkesaktivitetstype,
                   skjæringstidspunkt,
                   kollektiv_forsikring,
                   vurdert_tidspunkt,
                   vedtaksperiode_id,
                   behandling_id,
                   forrige_forsikringsvurdering_id
            FROM forsikringsvurdering
            WHERE id = :id
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("id" to id.value))
                .map { row ->
                    Forsikringsvurdering.fraLagring(
                        id = id,
                        input =
                            ForsikringsvurderingInput(
                                identitetsnummer = Identitetsnummer.fraString(row.string("identitetsnummer")),
                                yrkesaktivitetstype = enumValueOf<Yrkesaktivitetstype>(row.string("yrkesaktivitetstype")),
                                spesielleYrkesgrupper = spesielleYrkesgrupper,
                                skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
                            ),
                        råkopiId = Id(row.uuid("råkopi_id")),
                        individuelleForsikringer = individuelleForsikringer,
                        kollektivForsikring =
                            row
                                .stringOrNull("kollektiv_forsikring")
                                ?.let<String, KollektivForsikring?> { enumValueOf<KollektivForsikring>(it) },
                        vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
                        vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                        behandlingId = row.uuid("behandling_id"),
                        forrigeForsikringsvurderingId =
                            row
                                .uuidOrNull("forrige_forsikringsvurdering_id")
                                ?.let { Forsikringsvurdering.Id(it) },
                    )
                }.asSingle,
        )
    }

    fun finn(
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
    ): Forsikringsvurdering? {
        @Language("PostgreSQL")
        val statement =
            """
            SELECT id,
                   råkopi_id,
                   identitetsnummer,
                   yrkesaktivitetstype,
                   skjæringstidspunkt,
                   kollektiv_forsikring,
                   vurdert_tidspunkt,
                   vedtaksperiode_id,
                   behandling_id,
                   forrige_forsikringsvurdering_id
            FROM forsikringsvurdering
            WHERE identitetsnummer = :identitetsnummer 
                AND skjæringstidspunkt = :skjaringstidspunkt
            ORDER BY vurdert_tidspunkt DESC, id DESC 
            LIMIT 1
            """.trimIndent()
        return spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "identitetsnummer" to identitetsnummer.value,
                    "skjaringstidspunkt" to skjæringstidspunkt,
                ),
            ).map { row ->
                val forsikringsvurderingId = Forsikringsvurdering.Id.fromString(row.string("id"))
                val spesielleYrkesgrupper = hentSpesielleYrkesgrupper(forsikringsvurderingId)
                val individuelleForsikringer = hentIndividuelleForsikringer(forsikringsvurderingId)
                Forsikringsvurdering.fraLagring(
                    id = forsikringsvurderingId,
                    input =
                        ForsikringsvurderingInput(
                            identitetsnummer = Identitetsnummer.fraString(row.string("identitetsnummer")),
                            yrkesaktivitetstype = enumValueOf<Yrkesaktivitetstype>(row.string("yrkesaktivitetstype")),
                            spesielleYrkesgrupper = spesielleYrkesgrupper,
                            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
                        ),
                    råkopiId = Id(row.uuid("råkopi_id")),
                    individuelleForsikringer = individuelleForsikringer,
                    kollektivForsikring =
                        row
                            .stringOrNull("kollektiv_forsikring")
                            ?.let<String, KollektivForsikring?> { enumValueOf<KollektivForsikring>(it) },
                    vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
                    vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                    behandlingId = row.uuid("behandling_id"),
                    forrigeForsikringsvurderingId =
                        row
                            .uuidOrNull("forrige_forsikringsvurdering_id")
                            ?.let { Forsikringsvurdering.Id(it) },
                )
            }.asSingle,
        )
    }

    private fun hentSpesielleYrkesgrupper(id: Forsikringsvurdering.Id): Set<SpesiellYrkesgruppe> {
        @Language("PostgreSQL")
        val statement = """
            SELECT spesiell_yrkesgruppe
            FROM forsikringsvurdering_spesiell_yrkesgruppe
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
        """
        return spForsikringTransactionalSession
            .run(
                queryOf(statement, mapOf("forsikringsvurdering_id" to id.value))
                    .map { row -> enumValueOf<SpesiellYrkesgruppe>(row.string("spesiell_yrkesgruppe")) }
                    .asList,
            ).toSet()
    }

    private fun hentIndividuelleForsikringer(id: Forsikringsvurdering.Id): List<VurdertIndividuellForsikring> {
        @Language("PostgreSQL")
        val statement = """
            SELECT råkopi_IF_VEDFRIVT_10_id,
                   type,
                   virkningsdato,
                   opphører,
                   opphørsdato,
                   premiegrunnlag,
                   er_betalt_noen_gang,
                   konklusjon
            FROM forsikringsvurdering_individuell_forsikring
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("forsikringsvurdering_id" to id.value))
                .map { row ->
                    VurdertIndividuellForsikring.fraLagring(
                        råkopiIfVedfrivt10Id = RåkopiIfVedfrivt10.Id(row.uuid("råkopi_IF_VEDFRIVT_10_id")),
                        type = enumValueOf(row.string("type")),
                        virkningsdato = row.localDate("virkningsdato"),
                        opphører = row.boolean("opphører"),
                        opphørsdato = row.localDateOrNull("opphørsdato"),
                        premiegrunnlag = row.int("premiegrunnlag"),
                        erBetaltNoenGang = row.boolean("er_betalt_noen_gang"),
                        konklusjon = enumValueOf(row.string("konklusjon")),
                    )
                }.asList,
        )
    }

    private fun lagreForsikringsvurdering(
        forsikringsvurdering: Forsikringsvurdering,
        behovEllerRequestBody: String,
    ) {
        val vedtaksperiodeId =
            requireNotNull(forsikringsvurdering.vedtaksperiodeId) {
                "Kan ikke lagre en forsikringsvurdering uten vedtaksperiodeId"
            }
        val behandlingId =
            requireNotNull(forsikringsvurdering.behandlingId) {
                "Kan ikke lagre en forsikringsvurdering uten behandlingId"
            }

        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering (id, råkopi_id, behov_eller_request_body, identitetsnummer,
                                              yrkesaktivitetstype,
                                              skjæringstidspunkt, kollektiv_forsikring, vurdert_tidspunkt,
                                              har_forsikring, dekning_i_ventetid, dekning_grad, opphørsdato,
                                              råkopi_IF_VEDFRIVT_10_id, forsikringskategori,
                                              vedtaksperiode_id, behandling_id, forrige_forsikringsvurdering_id)
            VALUES (:id, :rakopi_id, :behov_eller_request_body::jsonb, :identitetsnummer, :yrkesaktivitetstype,
                    :skjaeringstidspunkt, :kollektiv_forsikring, :vurdert_tidspunkt,
                    :har_forsikring, :dekning_i_ventetid, :dekning_grad, :opphorsdato,
                    :rakopi_IF_VEDFRIVT_10_id, :forsikringskategori,
                    :vedtaksperiode_id, :behandling_id, :forrige_forsikringsvurdering_id)
        """
        val dekning = forsikringsvurdering.dekning()
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to forsikringsvurdering.id.value,
                    "rakopi_id" to forsikringsvurdering.råkopiId.value,
                    "behov_eller_request_body" to behovEllerRequestBody,
                    "identitetsnummer" to forsikringsvurdering.input.identitetsnummer.value,
                    "yrkesaktivitetstype" to forsikringsvurdering.input.yrkesaktivitetstype.name,
                    "skjaeringstidspunkt" to forsikringsvurdering.input.skjæringstidspunkt,
                    "kollektiv_forsikring" to forsikringsvurdering.kollektivForsikring?.name,
                    "vurdert_tidspunkt" to forsikringsvurdering.vurdertTidspunkt,
                    "har_forsikring" to forsikringsvurdering.harForsikring(),
                    "dekning_i_ventetid" to dekning?.let { it.fraDag == 1 },
                    "dekning_grad" to dekning?.grad,
                    "opphorsdato" to forsikringsvurdering.opphørsdato(),
                    "rakopi_IF_VEDFRIVT_10_id" to
                        forsikringsvurdering
                            .gjeldendeIndividuellForsikring()
                            ?.råkopiIfVedfrivt10Id
                            ?.value,
                    "forsikringskategori" to
                        when {
                            forsikringsvurdering.harIndividuellForsikring() -> "INDIVIDUELL"
                            forsikringsvurdering.harKollektivForsikring() -> "KOLLEKTIV"
                            else -> null
                        },
                    "vedtaksperiode_id" to vedtaksperiodeId,
                    "behandling_id" to behandlingId,
                    "forrige_forsikringsvurdering_id" to forsikringsvurdering.forrigeForsikringsvurderingId?.value,
                ),
            ).asUpdate,
        )
    }

    private fun lagreSpesiellYrkesgruppe(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        spesiellYrkesgruppe: SpesiellYrkesgruppe,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_spesiell_yrkesgruppe (forsikringsvurdering_id, spesiell_yrkesgruppe)
            VALUES (:forsikringsvurdering_id, :spesiell_yrkesgruppe)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "spesiell_yrkesgruppe" to spesiellYrkesgruppe.name,
                ),
            ).asUpdate,
        )
    }

    private fun lagreIndividuellForsikring(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        individuellForsikring: VurdertIndividuellForsikring,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_individuell_forsikring
                (forsikringsvurdering_id, råkopi_IF_VEDFRIVT_10_id, type, virkningsdato, opphører,
                 opphørsdato, premiegrunnlag, er_betalt_noen_gang, konklusjon)
            VALUES
                (:forsikringsvurdering_id, :rakopi_IF_VEDFRIVT_10_id, :type, :virkningsdato, :opphorer,
                 :opphorsdato, :premiegrunnlag, :er_betalt_noen_gang, :konklusjon)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "rakopi_IF_VEDFRIVT_10_id" to individuellForsikring.råkopiIfVedfrivt10Id.value,
                    "type" to individuellForsikring.type.name,
                    "virkningsdato" to individuellForsikring.virkningsdato,
                    "opphorer" to individuellForsikring.opphører,
                    "opphorsdato" to individuellForsikring.opphørsdato,
                    "premiegrunnlag" to individuellForsikring.premiegrunnlag,
                    "er_betalt_noen_gang" to individuellForsikring.erBetaltNoenGang,
                    "konklusjon" to individuellForsikring.konklusjon.name,
                ),
            ).asUpdate,
        )
    }
}
