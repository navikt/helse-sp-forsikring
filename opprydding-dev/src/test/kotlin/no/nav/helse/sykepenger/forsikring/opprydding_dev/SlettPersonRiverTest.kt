package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SlettPersonRiverTest {

    private val rapid = TestRapid().apply {
        SlettPersonRiver(this, Database.dataSource)
    }

    @BeforeEach
    fun beforeEach() {
        Database.reset()
        rapid.reset()
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun shutdown() {
            Database.shutdown()
        }
    }

    @Test
    fun `sletter all data for en person`() {
        val fødselsnummer = "01020312345"
        val fnrLong = fødselsnummer.toLong()

        val oppslagId = UUID.randomUUID()
        val vedfrivt10Id = UUID.randomUUID()
        val fkonto12Id = UUID.randomUUID()
        val forsikringsvurderingId = UUID.randomUUID()

        insertOppslag(oppslagId)
        insertVedfrivt10(vedfrivt10Id, oppslagId, fnrLong)
        insertFkonto12(fkonto12Id, vedfrivt10Id)
        insertForsikringsvurdering(forsikringsvurderingId, oppslagId, fødselsnummer)
        insertEkskludering(forsikringsvurderingId, vedfrivt10Id)

        assertEquals(1, Database.countOppslag())
        assertEquals(1, Database.countForsikringsvurdering())
        assertEquals(1, Database.countOppslagIfVedfrivt10())
        assertEquals(1, Database.countOppslagIfFkonto12())
        assertEquals(1, Database.countEkskluderinger())

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(0, Database.countOppslag())
        assertEquals(0, Database.countForsikringsvurdering())
        assertEquals(0, Database.countOppslagIfVedfrivt10())
        assertEquals(0, Database.countOppslagIfFkonto12())
        assertEquals(0, Database.countEkskluderinger())
    }

    @Test
    fun `publiserer person_slettet etter sletting`() {
        val fødselsnummer = "01020312345"
        val fnrLong = fødselsnummer.toLong()

        val oppslagId = UUID.randomUUID()
        val vedfrivt10Id = UUID.randomUUID()
        insertOppslag(oppslagId)
        insertVedfrivt10(vedfrivt10Id, oppslagId, fnrLong)
        insertForsikringsvurdering(UUID.randomUUID(), oppslagId, fødselsnummer)

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `gjør ingenting for person uten data`() {
        rapid.sendTestMessage(slettPersonMelding("99999999999"))

        assertEquals(0, Database.countOppslag())
    }

    @Test
    fun `sletter ikke data for annen person`() {
        val fødselsnummer1 = "01020312345"
        val fødselsnummer2 = "02020312345"

        val oppslagId1 = UUID.randomUUID()
        val vedfrivt10Id1 = UUID.randomUUID()
        insertOppslag(oppslagId1)
        insertVedfrivt10(vedfrivt10Id1, oppslagId1, fødselsnummer1.toLong())
        insertForsikringsvurdering(UUID.randomUUID(), oppslagId1, fødselsnummer1)

        val oppslagId2 = UUID.randomUUID()
        val vedfrivt10Id2 = UUID.randomUUID()
        insertOppslag(oppslagId2)
        insertVedfrivt10(vedfrivt10Id2, oppslagId2, fødselsnummer2.toLong())
        insertForsikringsvurdering(UUID.randomUUID(), oppslagId2, fødselsnummer2)

        assertEquals(2, Database.countOppslag())

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer1))

        assertEquals(1, Database.countOppslag())
        assertEquals(1, Database.countForsikringsvurdering())
        assertEquals(1, Database.countOppslagIfVedfrivt10())
    }

    private fun insertOppslag(id: UUID) {
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO oppslag (id, oppslag_tidspunkt) VALUES (?, ?)").use { stmt ->
                stmt.setObject(1, id)
                stmt.setTimestamp(2, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
        }
    }

    private fun insertVedfrivt10(id: UUID, oppslagId: UUID, fnr: Long) {
        val now = Timestamp.from(Instant.now())
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO oppslag_IF_VEDFRIVT_10 (
                    id, oppslag_id,
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                    IF10_GODKJ, IF10_FORSFOM, IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM,
                    IF10_KOMBI, IF10_PREMGRL, IF10_FOM, IF10_PREMIE,
                    IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                    IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL,
                    IF10_TERM_KV, IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP,
                    IF10_PURR, IF10_TKNR_BOST, IF10_TKNR_BEH,
                    OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_VED
                ) VALUES (?, ?, '1', ?, 0, 'J', 0, 20260101, '1', ' ', ' ', 0, 0, 0, 0, 0, 0, 0, 0, ' ', 0, ' ', ' ', 0, 0, 0, 0, 0, ?, ?, ' ', 0)
                """
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, oppslagId)
                stmt.setLong(3, fnr)
                stmt.setTimestamp(4, now)
                stmt.setTimestamp(5, now)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertFkonto12(id: UUID, vedfrivt10Id: UUID) {
        val now = Timestamp.from(Instant.now())
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO oppslag_IF_FKONTO_12 (id, oppslag_IF_VEDFRIVT_10_id, OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT)
                VALUES (?, ?, ?, ?, ' ', 0)
                """
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, vedfrivt10Id)
                stmt.setTimestamp(3, now)
                stmt.setTimestamp(4, now)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertForsikringsvurdering(id: UUID, oppslagId: UUID, fødselsnummer: String) {
        val behovJson = """{"fødselsnummer": "$fødselsnummer", "@behov": ["Forsikringsvurdering"]}"""
        val løsningJson = """{"harForsikring": false}"""
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO forsikringsvurdering (id, oppslag_id, behov, løsning) VALUES (?, ?, ?::jsonb, ?::jsonb)"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, oppslagId)
                stmt.setString(3, behovJson)
                stmt.setString(4, løsningJson)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertEkskludering(forsikringsvurderingId: UUID, vedfrivt10Id: UUID) {
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO forsikringsvurdering_ekskludering_navkjopt_forsikring
                    (forsikringsvurdering_id, oppslag_IF_VEDFRIVT_10_id, ekskluderingsaarsak)
                VALUES (?, ?, 'ALDRI_BETALT')
                """
            ).use { stmt ->
                stmt.setObject(1, forsikringsvurderingId)
                stmt.setObject(2, vedfrivt10Id)
                stmt.executeUpdate()
            }
        }
    }

    private fun slettPersonMelding(fødselsnummer: String) = """
        {
            "@event_name": "slett_person",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$fødselsnummer"
        }
    """.trimIndent()
}
