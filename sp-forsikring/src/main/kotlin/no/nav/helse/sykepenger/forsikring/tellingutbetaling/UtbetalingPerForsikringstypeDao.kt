package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringstype
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private val OSLO = ZoneId.of("Europe/Oslo")

/** Beløp lagres med to desimaler, altså med presisjon ned til øret. */
const val BELØPSSKALA = 2

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
                    utbetaltIVentetid = row.bigDecimal("utbetalt_i_ventetid").setScale(BELØPSSKALA),
                    utbetaltUtenomVentetid = row.bigDecimal("utbetalt_utenom_ventetid").setScale(BELØPSSKALA),
                )
            }.asList,
        )
    }

    fun insert(
        vedtakFattetMeldingId: UUID,
        forsikringstype: Forsikringstype,
        utbetaltIVentetid: BigDecimal,
        utbetaltUtenomVentetid: BigDecimal,
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
                    "utbetalt_i_ventetid" to utbetaltIVentetid.setScale(BELØPSSKALA, RoundingMode.HALF_UP),
                    "utbetalt_utenom_ventetid" to utbetaltUtenomVentetid.setScale(BELØPSSKALA, RoundingMode.HALF_UP),
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
    val utbetaltIVentetid: BigDecimal,
    val utbetaltUtenomVentetid: BigDecimal,
) {
    val totalt: BigDecimal = (utbetaltIVentetid + utbetaltUtenomVentetid).setScale(BELØPSSKALA)
}
