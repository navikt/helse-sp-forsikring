package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import org.intellij.lang.annotations.Language
import java.util.*

class UtbetalingPerForsikringstypeDao(
    private val spForsikringTransactionalSession: TransactionalSession,
) {
    fun lagre(
        id: UUID,
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
                    "id" to id,
                    "vedtak_fattet_melding_id" to vedtakFattetMeldingId,
                    "utbetalt_i_ventetid" to utbetaltIVentetid,
                    "utbetalt_utenom_ventetid" to utbetaltUtenomVentetid,
                    "kollektiv_forsikring_type" to (forsikringstype as? Forsikringstype.Kollektiv)?.type?.name,
                    "navkjopt_forsikring_type" to (forsikringstype as? Forsikringstype.NavKjøpt)?.type?.name,
                ),
            ).asUpdate,
        )
    }
}

sealed interface Forsikringstype {
    data class Kollektiv(
        val type: KollektivForsikring,
    ) : Forsikringstype

    data class NavKjøpt(
        val type: NavKjøptForsikringType,
    ) : Forsikringstype
}
