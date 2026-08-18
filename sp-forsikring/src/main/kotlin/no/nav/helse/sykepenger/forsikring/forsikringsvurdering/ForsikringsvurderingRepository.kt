package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.*
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi.Id
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import org.intellij.lang.annotations.Language

class ForsikringsvurderingRepository(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun lagre(
        forsikringsvurdering: Forsikringsvurdering,
        behovJson: String,
    ) {
        lagreForsikringsvurdering(forsikringsvurdering, behovJson)
        forsikringsvurdering.spesielleYrkesgrupper.forEach { spesiellYrkesgruppe ->
            lagreSpesiellYrkesgruppe(forsikringsvurdering.id, spesiellYrkesgruppe)
        }
        forsikringsvurdering.navKjøpteForsikringer.forEach { navKjøptForsikring ->
            lagreNavKjøptForsikring(forsikringsvurdering.id, navKjøptForsikring)
        }
    }

    fun hent(id: Forsikringsvurdering.Id): Forsikringsvurdering? {
        val spesielleYrkesgrupper = hentSpesielleYrkesgrupper(id)
        val navKjøpteForsikringer = hentNavKjøpteForsikringer(id)

        @Language("PostgreSQL")
        val statement = """
            SELECT råkopi_id,
                   identitetsnummer,
                   yrkesaktivitetstype,
                   skjæringstidspunkt,
                   kollektiv_forsikring,
                   vurdert_tidspunkt
            FROM forsikringsvurdering
            WHERE id = :id
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("id" to id.value))
                .map { row ->
                    Forsikringsvurdering.fraLagring(
                        id = id,
                        identitetsnummer = Identitetsnummer.fraString(row.string("identitetsnummer")),
                        yrkesaktivitetstype = enumValueOf<Yrkesaktivitetstype>(row.string("yrkesaktivitetstype")),
                        spesielleYrkesgrupper = spesielleYrkesgrupper,
                        skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
                        råkopiId = Id(row.uuid("råkopi_id")),
                        navKjøpteForsikringer = navKjøpteForsikringer,
                        kollektivForsikring =
                            row
                                .stringOrNull("kollektiv_forsikring")
                                ?.let<String, KollektivForsikring?> { enumValueOf<KollektivForsikring>(it) },
                        vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
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

    private fun hentNavKjøpteForsikringer(id: Forsikringsvurdering.Id): List<VurdertNavKjøptForsikring> {
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
            FROM forsikringsvurdering_navkjøpt_forsikring
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("forsikringsvurdering_id" to id.value))
                .map { row ->
                    VurdertNavKjøptForsikring.fraLagring(
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
        behovJson: String,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering (id, råkopi_id, behov, identitetsnummer, yrkesaktivitetstype,
                                              skjæringstidspunkt, kollektiv_forsikring, vurdert_tidspunkt,
                                              har_forsikring, dekning_i_ventetid, dekning_grad, opphørsdato,
                                              råkopi_IF_VEDFRIVT_10_id, forsikringskategori)
            VALUES (:id, :rakopi_id, :behov::jsonb, :identitetsnummer, :yrkesaktivitetstype,
                    :skjaeringstidspunkt, :kollektiv_forsikring, :vurdert_tidspunkt,
                    :har_forsikring, :dekning_i_ventetid, :dekning_grad, :opphorsdato,
                    :rakopi_IF_VEDFRIVT_10_id, :forsikringskategori)
        """
        val dekning = forsikringsvurdering.dekning()
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to forsikringsvurdering.id.value,
                    "rakopi_id" to forsikringsvurdering.råkopiId.value,
                    "behov" to behovJson,
                    "identitetsnummer" to forsikringsvurdering.identitetsnummer.value,
                    "yrkesaktivitetstype" to forsikringsvurdering.yrkesaktivitetstype.name,
                    "skjaeringstidspunkt" to forsikringsvurdering.skjæringstidspunkt,
                    "kollektiv_forsikring" to forsikringsvurdering.kollektivForsikring?.name,
                    "vurdert_tidspunkt" to forsikringsvurdering.vurdertTidspunkt,
                    "har_forsikring" to forsikringsvurdering.harForsikring(),
                    "dekning_i_ventetid" to dekning?.let { it.fraDag == 1 },
                    "dekning_grad" to dekning?.grad,
                    "opphorsdato" to forsikringsvurdering.opphørsdato(),
                    "rakopi_IF_VEDFRIVT_10_id" to
                        forsikringsvurdering
                            .gjeldendeNavKjøptForsikring()
                            ?.råkopiIfVedfrivt10Id
                            ?.value,
                    "forsikringskategori" to
                        when {
                            forsikringsvurdering.harNavKjøptForsikring() -> "NAVKJØPT"
                            forsikringsvurdering.harKollektivForsikring() -> "KOLLEKTIV"
                            else -> null
                        },
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

    private fun lagreNavKjøptForsikring(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        navKjøptForsikring: VurdertNavKjøptForsikring,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_navkjøpt_forsikring
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
                    "rakopi_IF_VEDFRIVT_10_id" to navKjøptForsikring.råkopiIfVedfrivt10Id.value,
                    "type" to navKjøptForsikring.type.name,
                    "virkningsdato" to navKjøptForsikring.virkningsdato,
                    "opphorer" to navKjøptForsikring.opphører,
                    "opphorsdato" to navKjøptForsikring.opphørsdato,
                    "premiegrunnlag" to navKjøptForsikring.premiegrunnlag,
                    "er_betalt_noen_gang" to navKjøptForsikring.erBetaltNoenGang,
                    "konklusjon" to navKjøptForsikring.konklusjon.name,
                ),
            ).asUpdate,
        )
    }
}
