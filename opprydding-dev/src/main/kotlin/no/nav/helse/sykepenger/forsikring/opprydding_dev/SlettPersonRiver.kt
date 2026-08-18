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
import java.util.UUID
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
        // Konverter fra ddMMyy til yyMMdd-format før toLong()
        val fnrLong = (fødselsnummer.substring(4, 6) + fødselsnummer.substring(2, 4) + fødselsnummer.substring(0, 2) + fødselsnummer.substring(6)).toLong()

        // Samle råkopi-IDer for personen fra begge mulige veier (med og uten forsikringsdata)
        val råkopiIds: List<UUID> =
            tx.run(
                queryOf(
                    """
                SELECT DISTINCT råkopi_id FROM råkopi_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
                UNION
                SELECT råkopi_id FROM forsikringsvurdering WHERE identitetsnummer = :fnrStr
                """,
                    mapOf("fnr" to fnrLong, "fnrStr" to fødselsnummer),
                ).map { it.uuid("råkopi_id") }.asList,
            )

        if (råkopiIds.isEmpty()) return

        // Slett i riktig rekkefølge for å respektere FK-koblinger

        val placeholders = råkopiIds.indices.joinToString(",") { "?" }

        tx.run(
            queryOf(
                """
                DELETE FROM forsikringsvurdering_navkjøpt_forsikring
                WHERE forsikringsvurdering_id IN (
                    SELECT id FROM forsikringsvurdering WHERE råkopi_id IN ($placeholders)
                )
                """,
                *råkopiIds.toTypedArray(),
            ).asUpdate,
        )

        tx.run(
            queryOf(
                """
                DELETE FROM forsikringsvurdering_spesiell_yrkesgruppe
                WHERE forsikringsvurdering_id IN (
                    SELECT id FROM forsikringsvurdering WHERE råkopi_id IN ($placeholders)
                )
                """,
                *råkopiIds.toTypedArray(),
            ).asUpdate,
        )

        tx.run(
            queryOf(
                "DELETE FROM forsikringsvurdering WHERE råkopi_id IN ($placeholders)",
                *råkopiIds.toTypedArray(),
            ).asUpdate,
        )

        @Language("PostgreSQL")
        val slettFkonto12 = """
            DELETE FROM råkopi_IF_FKONTO_12
            WHERE råkopi_IF_VEDFRIVT_10_id IN (
                SELECT id FROM råkopi_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
            )
        """
        tx.run(queryOf(slettFkonto12, mapOf("fnr" to fnrLong)).asUpdate)

        @Language("PostgreSQL")
        val slettVedfrivt10 = "DELETE FROM råkopi_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr"
        tx.run(queryOf(slettVedfrivt10, mapOf("fnr" to fnrLong)).asUpdate)

        tx.run(
            queryOf(
                "DELETE FROM råkopi WHERE id IN ($placeholders)",
                *råkopiIds.toTypedArray(),
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
