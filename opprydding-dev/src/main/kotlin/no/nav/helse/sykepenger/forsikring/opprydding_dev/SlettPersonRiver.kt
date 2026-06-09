package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID
import javax.sql.DataSource
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

internal class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "slett_person") }
            validate {
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        sessionOf(dataSource).use { session ->
            session.transaction { tx -> slettPerson(tx, fødselsnummer) }
        }
        context.publish(fødselsnummer, lagPersonSlettet(fødselsnummer))
    }

    private fun slettPerson(tx: TransactionalSession, fødselsnummer: String) {
        val fnrLong = fødselsnummer.toLong()

        // Samle oppslag-IDer for personen fra begge mulige veier (med og uten forsikringsdata)
        val oppslagIds: List<UUID> = tx.run(
            queryOf(
                """
                SELECT DISTINCT oppslag_id FROM oppslag_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
                UNION
                SELECT oppslag_id FROM forsikringsvurdering WHERE behov->>'fødselsnummer' = :fnrStr
                """,
                mapOf("fnr" to fnrLong, "fnrStr" to fødselsnummer)
            ).map { it.uuid("oppslag_id") }.asList
        )

        if (oppslagIds.isEmpty()) return

        // Slett i riktig rekkefølge for å respektere FK-koblinger

        @Language("PostgreSQL")
        val slettEkskluderinger = """
            DELETE FROM forsikringsvurdering_ekskludering_navkjopt_forsikring
            WHERE oppslag_IF_VEDFRIVT_10_id IN (
                SELECT id FROM oppslag_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
            )
        """
        tx.run(queryOf(slettEkskluderinger, mapOf("fnr" to fnrLong)).asUpdate)

        if (oppslagIds.isNotEmpty()) {
            val placeholders = oppslagIds.indices.joinToString(",") { "?" }
            tx.run(
                queryOf(
                    "DELETE FROM forsikringsvurdering WHERE oppslag_id IN ($placeholders)",
                    *oppslagIds.toTypedArray()
                ).asUpdate
            )
        }

        @Language("PostgreSQL")
        val slettFkonto12 = """
            DELETE FROM oppslag_IF_FKONTO_12
            WHERE oppslag_IF_VEDFRIVT_10_id IN (
                SELECT id FROM oppslag_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr
            )
        """
        tx.run(queryOf(slettFkonto12, mapOf("fnr" to fnrLong)).asUpdate)

        @Language("PostgreSQL")
        val slettVedfrivt10 = "DELETE FROM oppslag_IF_VEDFRIVT_10 WHERE IF01_AGNR_FNR = :fnr"
        tx.run(queryOf(slettVedfrivt10, mapOf("fnr" to fnrLong)).asUpdate)

        if (oppslagIds.isNotEmpty()) {
            val placeholders = oppslagIds.indices.joinToString(",") { "?" }
            tx.run(
                queryOf(
                    "DELETE FROM oppslag WHERE id IN ($placeholders)",
                    *oppslagIds.toTypedArray()
                ).asUpdate
            )
        }
    }

    @Language("JSON")
    private fun lagPersonSlettet(fødselsnummer: String): String {
        return """
            {
                "@event_name": "person_slettet",
                "fødselsnummer": "$fødselsnummer"
            }
        """.trimIndent()
    }
}
