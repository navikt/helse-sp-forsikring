package no.nav.helse.sykepenger.forsikring.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

internal class SlettPersonRiverTest {
    private val rapid =
        TestRapid().apply {
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
        val fnrLong = (fødselsnummer.substring(4, 6) + fødselsnummer.substring(2, 4) + fødselsnummer.substring(0, 2) + fødselsnummer.substring(6)).toLong()

        val råkopiId = UUID.randomUUID()
        val vedfrivt10Id = UUID.randomUUID()
        val fkonto12Id = UUID.randomUUID()
        val forsikringsvurderingId = UUID.randomUUID()

        insertRåkopi(råkopiId)
        insertVedfrivt10(vedfrivt10Id, råkopiId, fnrLong)
        insertFkonto12(fkonto12Id, vedfrivt10Id)
        insertForsikringsvurdering(forsikringsvurderingId, råkopiId, fødselsnummer)
        insertIndividuellForsikring(forsikringsvurderingId, vedfrivt10Id)
        insertSpesiellYrkesgruppe(forsikringsvurderingId)
        val vedtakFattetMeldingId = UUID.randomUUID()
        insertVedtakFattetMelding(vedtakFattetMeldingId, forsikringsvurderingId, fødselsnummer)
        insertUtbetalingPerForsikringstype(UUID.randomUUID(), vedtakFattetMeldingId)

        assertEquals(1, Database.countRåkopi())
        assertEquals(1, Database.countForsikringsvurdering())
        assertEquals(1, Database.countRåkopiIfVedfrivt10())
        assertEquals(1, Database.countRåkopiIfFkonto12())
        assertEquals(1, Database.countIndividuelleForsikringer())
        assertEquals(1, Database.countSpesielleYrkesgrupper())
        assertEquals(1, Database.countVedtakFattetMeldinger())
        assertEquals(1, Database.countUtbetalingPerForsikringstype())

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(0, Database.countRåkopi())
        assertEquals(0, Database.countForsikringsvurdering())
        assertEquals(0, Database.countRåkopiIfVedfrivt10())
        assertEquals(0, Database.countRåkopiIfFkonto12())
        assertEquals(0, Database.countIndividuelleForsikringer())
        assertEquals(0, Database.countSpesielleYrkesgrupper())
        assertEquals(0, Database.countVedtakFattetMeldinger())
        assertEquals(0, Database.countUtbetalingPerForsikringstype())
    }

    @Test
    fun `sletter vedtaksdata for person uten råkopi`() {
        val fødselsnummer = "01020312345"

        val vedtakFattetMeldingId = UUID.randomUUID()
        insertVedtakFattetMelding(vedtakFattetMeldingId, null, fødselsnummer)
        insertUtbetalingPerForsikringstype(UUID.randomUUID(), vedtakFattetMeldingId)

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(0, Database.countVedtakFattetMeldinger())
        assertEquals(0, Database.countUtbetalingPerForsikringstype())
    }

    @Test
    fun `publiserer person_slettet etter sletting`() {
        val fødselsnummer = "01020312345"
        val fnrLong = (fødselsnummer.substring(4, 6) + fødselsnummer.substring(2, 4) + fødselsnummer.substring(0, 2) + fødselsnummer.substring(6)).toLong()

        val råkopiId = UUID.randomUUID()
        val vedfrivt10Id = UUID.randomUUID()
        insertRåkopi(råkopiId)
        insertVedfrivt10(vedfrivt10Id, råkopiId, fnrLong)
        insertForsikringsvurdering(UUID.randomUUID(), råkopiId, fødselsnummer)

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `gjør ingenting for person uten data`() {
        rapid.sendTestMessage(slettPersonMelding("99999999999"))

        assertEquals(0, Database.countRåkopi())
    }

    @Test
    fun `sletter ikke data for annen person`() {
        val fødselsnummer1 = "01020312345"
        val fødselsnummer2 = "02020312345"
        val fnrLong1 = (fødselsnummer1.substring(4, 6) + fødselsnummer1.substring(2, 4) + fødselsnummer1.substring(0, 2) + fødselsnummer1.substring(6)).toLong()
        val fnrLong2 = (fødselsnummer2.substring(4, 6) + fødselsnummer2.substring(2, 4) + fødselsnummer2.substring(0, 2) + fødselsnummer2.substring(6)).toLong()

        val råkopiId1 = UUID.randomUUID()
        val vedfrivt10Id1 = UUID.randomUUID()
        insertRåkopi(råkopiId1)
        insertVedfrivt10(vedfrivt10Id1, råkopiId1, fnrLong1)
        insertForsikringsvurdering(UUID.randomUUID(), råkopiId1, fødselsnummer1)

        val råkopiId2 = UUID.randomUUID()
        val vedfrivt10Id2 = UUID.randomUUID()
        val forsikringsvurderingId2 = UUID.randomUUID()
        insertRåkopi(råkopiId2)
        insertVedfrivt10(vedfrivt10Id2, råkopiId2, fnrLong2)
        insertForsikringsvurdering(forsikringsvurderingId2, råkopiId2, fødselsnummer2)
        insertVedtakFattetMelding(UUID.randomUUID(), forsikringsvurderingId2, fødselsnummer2)

        assertEquals(2, Database.countRåkopi())

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer1))

        assertEquals(1, Database.countRåkopi())
        assertEquals(1, Database.countForsikringsvurdering())
        assertEquals(1, Database.countRåkopiIfVedfrivt10())
        assertEquals(1, Database.countVedtakFattetMeldinger())
    }

    private fun insertSpesiellYrkesgruppe(forsikringsvurderingId: UUID) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO forsikringsvurdering_spesiell_yrkesgruppe (forsikringsvurdering_id, spesiell_yrkesgruppe)
                VALUES (?, 'FISKER')
                """,
                ).use { stmt ->
                    stmt.setObject(1, forsikringsvurderingId)
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertVedtakFattetMelding(
        id: UUID,
        forsikringsvurderingId: UUID?,
        fødselsnummer: String,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO vedtak_fattet_melding
                    (id, forsikringsvurdering_id, identitetsnummer, behandling_id, vedtak_fattet_tidspunkt, json)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, forsikringsvurderingId)
                    stmt.setString(3, fødselsnummer)
                    stmt.setObject(4, UUID.randomUUID())
                    stmt.setTimestamp(5, Timestamp.from(Instant.now()))
                    stmt.setString(6, """{"@event_name": "vedtak_fattet"}""")
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertUtbetalingPerForsikringstype(
        id: UUID,
        vedtakFattetMeldingId: UUID,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO utbetaling_per_forsikringstype
                    (id, utbetalt_i_ventetid, utbetalt_utenom_ventetid, vedtak_fattet_melding_id, individuell_forsikring_type)
                VALUES (?, 0, 0, ?, 'SELVSTENDIG_80_PROSENT_FRA_DAG_1')
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, vedtakFattetMeldingId)
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertRåkopi(id: UUID) {
        Database.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO råkopi (id, lest_tidspunkt) VALUES (?, ?)").use { stmt ->
                stmt.setObject(1, id)
                stmt.setTimestamp(2, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
        }
    }

    private fun insertVedfrivt10(
        id: UUID,
        råkopiId: UUID,
        fnr: Long,
    ) {
        val now = Timestamp.from(Instant.now())
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO råkopi_IF_VEDFRIVT_10 (
                    id, råkopi_id,
                    IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ,
                    IF10_GODKJ, IF10_FORSFOM, IF10_VIRKDATO, IF10_TYPE, IF10_SELVFOM,
                    IF10_KOMBI, IF10_PREMGRL, IF10_FOM, IF10_PREMIE,
                    IF10_GML_PREMGRL, IF10_GML_FOM, IF10_GML_PREMIE,
                    IF10_FRIFOM, IF10_FORSTOM, IF10_OPPHGR, IF10_VARSEL,
                    IF10_TERM_KV, IF10_TERM_AAR, IF10_VARSEL_BELOEP, IF10_BETALT_BELOEP,
                    IF10_PURR, IF10_TKNR_BOST, IF10_TKNR_BEH,
                    OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_VED
                ) VALUES (?, ?, '1', ?, 0, 'J', 0, 20260101, '1', ' ', ' ', 0, 0, 0, 0, 0, 0, 0, 0, ' ', 0, ' ', ' ', 0, 0, 0, 0, 0, ?, ?, ' ', 0)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, råkopiId)
                    stmt.setLong(3, fnr)
                    stmt.setTimestamp(4, now)
                    stmt.setTimestamp(5, now)
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertFkonto12(
        id: UUID,
        vedfrivt10Id: UUID,
    ) {
        val now = Timestamp.from(Instant.now())
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO råkopi_IF_FKONTO_12 (id, råkopi_IF_VEDFRIVT_10_id, OPPRETTET, ENDRET_I_KILDE, KILDE_IF, ID_KONT)
                VALUES (?, ?, ?, ?, ' ', 0)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, vedfrivt10Id)
                    stmt.setTimestamp(3, now)
                    stmt.setTimestamp(4, now)
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertForsikringsvurdering(
        id: UUID,
        råkopiId: UUID,
        fødselsnummer: String,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO forsikringsvurdering (id, råkopi_id, behov, identitetsnummer, yrkesaktivitetstype,
                                                  skjæringstidspunkt, vurdert_tidspunkt, har_forsikring)
                VALUES (?, ?, ?::jsonb, ?, 'SELVSTENDIG', DATE '2026-01-01', ?, ?)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, råkopiId)
                    stmt.setString(3, """{"fødselsnummer": "$fødselsnummer", "@behov": ["Forsikringsvurdering"]}""")
                    stmt.setString(4, fødselsnummer)
                    stmt.setTimestamp(5, Timestamp.from(Instant.now()))
                    stmt.setBoolean(6, false)
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertIndividuellForsikring(
        forsikringsvurderingId: UUID,
        vedfrivt10Id: UUID,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO forsikringsvurdering_individuell_forsikring
                    (forsikringsvurdering_id, råkopi_IF_VEDFRIVT_10_id, type, virkningsdato, opphører,
                     premiegrunnlag, er_betalt_noen_gang, konklusjon)
                VALUES (?, ?, 'SELVSTENDIG_80_PROSENT_FRA_DAG_1', DATE '2026-01-01', false, 0, false, 'ALDRI_BETALT')
                """,
                ).use { stmt ->
                    stmt.setObject(1, forsikringsvurderingId)
                    stmt.setObject(2, vedfrivt10Id)
                    stmt.executeUpdate()
                }
        }
    }

    private fun slettPersonMelding(fødselsnummer: String) =
        """
        {
            "@event_name": "slett_person",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$fødselsnummer"
        }
        """.trimIndent()
}
