package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

internal class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "slett_person") }
                validate {
                    it.requireKey("@id", "fødselsnummer")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()

        sessionOf(dataSource).use { session ->
            session.transaction { tx -> slettPerson(tx, fødselsnummer) }
        }
        context.publish(fødselsnummer, lagPersonSlettet(fødselsnummer))
    }

    private fun slettPerson(
        tx: TransactionalSession,
        fødselsnummer: String,
    ) {
        // Slett i riktig rekkefølge for å respektere FK-koblinger:
        // barn før forelder, og alt som peker på forsikringsvurdering før forsikringsvurderingene selv.
        slettUtbetalingsdata(tx, fødselsnummer)
        slettForsikringsvurderinger(tx, fødselsnummer)
        slettRåkopier(fødselsnummer, tx)
    }

    private fun slettRåkopier(
        fødselsnummer: String,
        tx: TransactionalSession,
    ) {
        // Kildesystemet lagrer fødselsnummeret som et tall på formatet yyMMdd + personnummer
        val fnrLong =
            (
                fødselsnummer.substring(4, 6) +
                    fødselsnummer.substring(2, 4) +
                    fødselsnummer.substring(0, 2) +
                    fødselsnummer.substring(6)
            ).toLong()

        @Language("PostgreSQL")
        val query = """
            SELECT DISTINCT råkopi_id FROM råkopi_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
        """
        val råkopiIds =
            tx.run(
                queryOf(query, mapOf("fnr" to fnrLong, "fnrStr" to fødselsnummer))
                    .map { it.uuid("råkopi_id") }
                    .asList,
            )

        if (råkopiIds.isNotEmpty()) {
            @Language("PostgreSQL")
            val slettFkonto12 = """
            DELETE FROM råkopi_IF_FKONTO_12
            WHERE råkopi_IF_VEDFRIVT_10_id IN (
                SELECT id FROM råkopi_IF_VEDFRIVT_10 WHERE råkopi_id IN (${råkopiIds.joinToString(",") { "?" }})
            )
        """
            tx.run(queryOf(slettFkonto12, *råkopiIds.toTypedArray<UUID>()).asUpdate)
            @Language("PostgreSQL")
            val slettVedfrivt10 =
                "DELETE FROM råkopi_IF_VEDFRIVT_10 WHERE råkopi_id IN (${råkopiIds.joinToString(",") { "?" }})"
            tx.run(queryOf(slettVedfrivt10, *råkopiIds.toTypedArray<UUID>()).asUpdate)
            tx.run(
                queryOf(
                    "DELETE FROM råkopi WHERE id IN (${råkopiIds.joinToString(",") { "?" }})",
                    *råkopiIds.toTypedArray<UUID>(),
                ).asUpdate,
            )
        }
    }

    private fun slettUtbetalingsdata(
        tx: TransactionalSession,
        fødselsnummer: String,
    ) {
        tx.run(
            queryOf(
                // language=PostgreSQL
                """
                DELETE FROM utbetaling_per_forsikringstype
                WHERE vedtak_fattet_melding_id IN (
                    SELECT id FROM vedtak_fattet_melding WHERE 
            identitetsnummer = :identitetsnummer
                )
                """,
                mapOf("identitetsnummer" to fødselsnummer),
            ).asUpdate,
        )

        tx.run(
            queryOf(
                // language=PostgreSQL
                """
                DELETE FROM vedtak_fattet_melding WHERE 
            identitetsnummer = :identitetsnummer
                """,
                mapOf("identitetsnummer" to fødselsnummer),
            ).asUpdate,
        )
    }

    private fun slettForsikringsvurderinger(
        tx: TransactionalSession,
        identitetsnummer: String,
    ) {
        listOf(
            "forsikringsvurdering_individuell_forsikring",
            "forsikringsvurdering_spesiell_yrkesgruppe",
        ).forEach { tabell ->
            tx.run(
                queryOf(
                    "DELETE FROM $tabell WHERE forsikringsvurdering_id IN (SELECT id FROM forsikringsvurdering WHERE identitetsnummer = :identitetsnummer)",
                    mapOf("identitetsnummer" to identitetsnummer),
                ).asUpdate,
            )
        }

        tx.run(
            queryOf(
                "DELETE FROM forsikringsvurdering WHERE identitetsnummer = :identitetsnummer",
                mapOf("identitetsnummer" to identitetsnummer),
            ).asUpdate,
        )
    }

    @Language("JSON")
    private fun lagPersonSlettet(fødselsnummer: String): String =
        """
        {
            "@event_name": "person_slettet",
            "fødselsnummer": "$fødselsnummer"
        }
        """.trimIndent()
}
