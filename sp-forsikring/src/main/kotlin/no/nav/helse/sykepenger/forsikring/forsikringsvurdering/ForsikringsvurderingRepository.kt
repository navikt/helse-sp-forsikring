package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.*
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import org.intellij.lang.annotations.Language
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Leser og skriver forsikringsvurderinger mot et databaseskjema som er eldre enn domenemodellen.
 *
 * Skjemaet lagrer bare konklusjonen av en vurdering, ikke hele den vurderte tilstanden. Ved lesing
 * rekonstrueres derfor domeneobjektet slik:
 *
 *  - identitetsnummer, yrkesaktivitetstype, spesielle yrkesgrupper og skjæringstidspunkt hentes fra
 *    behov-JSON-en som ble lagret sammen med vurderingen
 *  - de nav-kjøpte forsikringene tolkes fra råkopiradene (oppslag_IF_VEDFRIVT_10 / oppslag_IF_FKONTO_12)
 *  - konklusjonen per nav-kjøpt forsikring hentes fra ekskluderingstabellen, slik at den historiske
 *    vurderingen bevares i stedet for å vurderes på nytt
 *  - vurdertTidspunkt settes til oppslagstidspunktet for råkopien
 */
class ForsikringsvurderingRepository(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    private val navKjøptForsikringService = NavKjøptForsikringService()
    private val kollektivForsikringService = KollektivForsikringService()

    fun lagre(
        forsikringsvurdering: Forsikringsvurdering,
        behovJson: String,
    ) {
        lagreForsikringsvurdering(forsikringsvurdering, behovJson)
        forsikringsvurdering.navKjøpteForsikringer
            .filterNot { it.erGyldig() }
            .forEach { ekskludertForsikring ->
                lagreEkskludering(forsikringsvurdering.id, ekskludertForsikring)
            }
    }

    fun hent(id: Forsikringsvurdering.Id): Forsikringsvurdering? {
        val rad = hentRad(id) ?: return null

        val konklusjoner = hentKonklusjoner(id)

        val navKjøpteForsikringer =
            hentNavKjøpteForsikringer(rad.råkopiId).map { navKjøptForsikring ->
                VurdertNavKjøptForsikring.fraNavKjøptForsikringMedKonklusjon(
                    navKjøptForsikring = navKjøptForsikring,
                    yrkesaktivitetstype = rad.yrkesaktivitetstype,
                    spesielleYrkesgrupper = rad.spesielleYrkesgrupper,
                    konklusjon =
                        konklusjoner[navKjøptForsikring.råkopiIfVedfrivt10Id.value]
                            ?: VurdertNavKjøptForsikring.Konklusjon.GYLDIG,
                )
            }

        return Forsikringsvurdering.fraLagring(
            id = id,
            identitetsnummer = rad.identitetsnummer,
            yrkesaktivitetstype = rad.yrkesaktivitetstype,
            spesielleYrkesgrupper = rad.spesielleYrkesgrupper,
            skjæringstidspunkt = rad.skjæringstidspunkt,
            råkopiId = rad.råkopiId,
            navKjøpteForsikringer = navKjøpteForsikringer,
            kollektivForsikring = utledKollektivForsikring(rad.spesielleYrkesgrupper),
            vurdertTidspunkt = rad.vurdertTidspunkt,
        )
    }

    private class Rad(
        val råkopiId: Råkopi.Id,
        val identitetsnummer: Identitetsnummer,
        val yrkesaktivitetstype: Yrkesaktivitetstype,
        val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        val skjæringstidspunkt: LocalDate,
        val vurdertTidspunkt: Instant,
    )

    private fun hentRad(id: Forsikringsvurdering.Id): Rad? {
        // jsonb_array_elements_text er en set-returning function, så den pakkes i en skalar subquery
        // for at raden ikke skal multipliseres opp til én rad per spesiell yrkesgruppe.
        @Language("PostgreSQL")
        val statement = """
            SELECT f.oppslag_id,
                   f.behov ->> 'fødselsnummer'                                 AS fodselsnummer,
                   f.behov ->> 'yrkesaktivitetstype'                           AS yrkesaktivitetstype,
                   f.behov -> 'Forsikringsvurdering' ->> 'skjæringstidspunkt'  AS skjaeringstidspunkt,
                   ARRAY(
                       SELECT jsonb_array_elements_text(f.behov -> 'Forsikringsvurdering' -> 'spesielleYrkesgrupper')
                   )                                                          AS spesielle_yrkesgrupper,
                   o.oppslag_tidspunkt
            FROM forsikringsvurdering f
                     JOIN oppslag o ON o.id = f.oppslag_id
            WHERE f.id = :id
        """
        return spForsikringTransactionalSession.run(
            queryOf(statement, mapOf("id" to id.value))
                .map { row ->
                    Rad(
                        råkopiId = Råkopi.Id(row.uuid("oppslag_id")),
                        identitetsnummer = Identitetsnummer.fraString(row.string("fodselsnummer")),
                        yrkesaktivitetstype = enumValueOf(row.string("yrkesaktivitetstype")),
                        spesielleYrkesgrupper =
                            row
                                .array<String>("spesielle_yrkesgrupper")
                                .map { tilSpesiellYrkesgruppe(it) }
                                .toSet(),
                        skjæringstidspunkt = LocalDate.parse(row.string("skjaeringstidspunkt")),
                        vurdertTidspunkt = row.instant("oppslag_tidspunkt"),
                    )
                }.asSingle,
        )
    }

    /**
     * Duplisert fra ForsikringsvurderingBehovRiver med vilje: begge steder mapper verdier fra
     * behov-meldinga, som er et grensesnitt utenfor domenet.
     */
    private fun tilSpesiellYrkesgruppe(verdi: String): SpesiellYrkesgruppe =
        when (verdi) {
            "FISKER_BLAD_B" -> SpesiellYrkesgruppe.FISKER_BLAD_B
            "JORDBRUKER" -> SpesiellYrkesgruppe.JORDBRUKER
            "REINDRIFTER" -> SpesiellYrkesgruppe.REINDRIFTER
            else -> error("Ukjent spesiell yrkesgruppe: $verdi")
        }

    private fun hentKonklusjoner(id: Forsikringsvurdering.Id): Map<UUID, VurdertNavKjøptForsikring.Konklusjon> {
        @Language("PostgreSQL")
        val statement = """
            SELECT oppslag_IF_VEDFRIVT_10_id, ekskluderingsaarsak
            FROM forsikringsvurdering_ekskludering_navkjopt_forsikring
            WHERE forsikringsvurdering_id = :forsikringsvurdering_id
        """
        return spForsikringTransactionalSession
            .run(
                queryOf(statement, mapOf("forsikringsvurdering_id" to id.value))
                    .map { row ->
                        row.uuid("oppslag_IF_VEDFRIVT_10_id") to
                            enumValueOf<VurdertNavKjøptForsikring.Konklusjon>(row.string("ekskluderingsaarsak"))
                    }.asList,
            ).toMap()
    }

    private fun hentNavKjøpteForsikringer(råkopiId: Råkopi.Id) =
        RåkopiRepository(spForsikringTransactionalSession)
            .hent(råkopiId)
            ?.let { navKjøptForsikringService.tolkTilNavKjøpteForsikringer(it) }
            ?: error("Fant ikke råkopi med id $råkopiId")

    private fun utledKollektivForsikring(spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>): KollektivForsikring? =
        kollektivForsikringService
            .utledKollektiveForsikringer(spesielleYrkesgrupper)
            .also { kollektiveForsikringer ->
                if (kollektiveForsikringer.size > 1) {
                    error(
                        "Utledet mer enn én gjeldende kollektiv forsikring for bruker." +
                            " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                            " til økt utbetaling (med tanke på senere justering av premiesats)",
                    )
                }
            }.firstOrNull()

    private fun lagreForsikringsvurdering(
        forsikringsvurdering: Forsikringsvurdering,
        behovJson: String,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering (id, oppslag_id, behov, har_forsikring, dekning_i_ventetid, dekning_grad, opphørsdato, oppslag_IF_VEDFRIVT_10_id, forsikringskategori)
            VALUES (:id, :oppslag_id, :behov::jsonb, :har_forsikring, :dekning_i_ventetid, :dekning_grad, :opphorsdato, :oppslag_IF_VEDFRIVT_10_id, :forsikringskategori)
        """
        val dekning = forsikringsvurdering.dekning()
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to forsikringsvurdering.id.value,
                    "oppslag_id" to forsikringsvurdering.råkopiId.value,
                    "behov" to behovJson,
                    "har_forsikring" to forsikringsvurdering.harForsikring(),
                    "dekning_i_ventetid" to dekning?.let { it.fraDag == 1 },
                    "dekning_grad" to dekning?.grad,
                    "opphorsdato" to forsikringsvurdering.opphørsdato(),
                    "oppslag_IF_VEDFRIVT_10_id" to
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

    private fun lagreEkskludering(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        ekskludertForsikring: VurdertNavKjøptForsikring,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_ekskludering_navkjopt_forsikring
                (forsikringsvurdering_id, oppslag_IF_VEDFRIVT_10_id, ekskluderingsaarsak)
            VALUES
                (:forsikringsvurdering_id, :oppslag_IF_VEDFRIVT_10_id, :ekskluderingsaarsak)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "oppslag_IF_VEDFRIVT_10_id" to ekskludertForsikring.råkopiIfVedfrivt10Id.value,
                    "ekskluderingsaarsak" to ekskludertForsikring.konklusjon.name,
                ),
            ).asUpdate,
        )
    }
}
