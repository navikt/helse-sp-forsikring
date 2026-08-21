package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringstype
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private val OSLO = ZoneId.of("Europe/Oslo")

class UtbetalingPerForsikringstypeDao(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    /**
     * Summerer utbetalinger per forsikringstype for vedtak fattet i perioden [fom]–[tom], begge dager inklusive.
     *
     * Perioden tolkes i norsk tid, mens `vedtak_fattet_tidspunkt` er lagret som et tidspunkt i UTC. Derfor
     * konverteres døgngrensene til [java.time.Instant] i Kotlin i stedet for å sammenlikne på dato i SQL – da
     * slipper vi at databasens tidssone påvirker hvilke vedtak som havner innenfor perioden.
     */
    fun summerPerForsikringstype(
        fom: LocalDate,
        tom: LocalDate,
    ): List<SumPerForsikringstype> {
        require(!fom.isAfter(tom)) { "fom ($fom) kan ikke være etter tom ($tom)" }

        @Language("PostgreSQL")
        val statement = """
            SELECT upf.kollektiv_forsikring_type,
                   upf.navkjøpt_forsikring_type,
                   SUM(upf.utbetalt_i_ventetid)      AS utbetalt_i_ventetid,
                   SUM(upf.utbetalt_utenom_ventetid) AS utbetalt_utenom_ventetid
            FROM utbetaling_per_forsikringstype upf
                     INNER JOIN vedtak_fattet_melding vfm ON vfm.id = upf.vedtak_fattet_melding_id
            WHERE vfm.vedtak_fattet_tidspunkt >= :fra
              AND vfm.vedtak_fattet_tidspunkt < :til
            GROUP BY upf.kollektiv_forsikring_type, upf.navkjøpt_forsikring_type
        """
        return spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "fra" to fom.atStartOfDay(OSLO).toInstant(),
                    "til" to tom.plusDays(1).atStartOfDay(OSLO).toInstant(),
                ),
            ).map { row ->
                val kollektiv = row.stringOrNull("kollektiv_forsikring_type")
                val navKjøpt = row.stringOrNull("navkjøpt_forsikring_type")
                SumPerForsikringstype(
                    forsikringstype =
                        when {
                            kollektiv != null -> KollektivForsikring.valueOf(kollektiv)
                            navKjøpt != null -> NavKjøptForsikringType.valueOf(navKjøpt)
                            else -> error("Rad i utbetaling_per_forsikringstype mangler forsikringstype")
                        },
                    utbetaltIVentetid = row.long("utbetalt_i_ventetid"),
                    utbetaltUtenomVentetid = row.long("utbetalt_utenom_ventetid"),
                )
            }.asList,
        )
    }

    fun insert(
        vedtakFattetMeldingId: UUID,
        forsikringstype: Forsikringstype,
        utbetaltIVentetid: Int,
        utbetaltUtenomVentetid: Int,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO utbetaling_per_forsikringstype (id, vedtak_fattet_melding_id, utbetalt_i_ventetid,
                                                        utbetalt_utenom_ventetid, kollektiv_forsikring_type,
                                                        navkjøpt_forsikring_type)
            VALUES (:id, :vedtak_fattet_melding_id, :utbetalt_i_ventetid,
                    :utbetalt_utenom_ventetid, :kollektiv_forsikring_type,
                    :navkjopt_forsikring_type)
        """
        spForsikringTransactionalSession.run(
            queryOf(
                statement,
                mapOf(
                    "id" to UUID.randomUUID(),
                    "vedtak_fattet_melding_id" to vedtakFattetMeldingId,
                    "utbetalt_i_ventetid" to utbetaltIVentetid,
                    "utbetalt_utenom_ventetid" to utbetaltUtenomVentetid,
                    "kollektiv_forsikring_type" to
                        when (forsikringstype) {
                            is KollektivForsikring -> forsikringstype.name
                            is NavKjøptForsikringType -> null
                        },
                    "navkjopt_forsikring_type" to
                        when (forsikringstype) {
                            is KollektivForsikring -> null
                            is NavKjøptForsikringType -> forsikringstype.name
                        },
                ),
            ).asUpdate,
        )
    }
}

data class SumPerForsikringstype(
    val forsikringstype: Forsikringstype,
    val utbetaltIVentetid: Long,
    val utbetaltUtenomVentetid: Long,
) {
    val totalt: Long = utbetaltIVentetid + utbetaltUtenomVentetid
}
