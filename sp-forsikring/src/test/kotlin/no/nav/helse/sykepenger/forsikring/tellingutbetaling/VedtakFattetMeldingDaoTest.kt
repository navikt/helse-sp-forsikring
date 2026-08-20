package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VedtakFattetMeldingDaoTest {
    private val dataSource = TestcontainersSpForsikringDatabase.dataSource

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `lagrer rad i vedtak_fattet_melding`() {
        val forventetForsikringsvurderingId = lagreForsikringsvurdering()
        val forventetId = UUID.randomUUID()
        val forventetBehandlingId = UUID.randomUUID()
        val forventetVedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z")
        val forventetJson = """{"@event_name":"vedtak_fattet","utbetalingsdager":[]}"""

        dataSource.inTransaction { transaction ->
            VedtakFattetMeldingDao(transaction).insert(
                id = forventetId,
                forsikringsvurderingId = forventetForsikringsvurderingId,
                identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                behandlingId = forventetBehandlingId,
                vedtakFattetTidspunkt = forventetVedtakFattetTidspunkt,
                json = forventetJson,
            )
        }

        val rad = hentVedtakFattetMelding(forventetId)
        assertNotNull(rad)
        assertEquals(forventetForsikringsvurderingId.value, rad.forsikringsvurderingId)
        assertEquals(TESTFØDSELSNUMMER, rad.identitetsnummer)
        assertEquals(forventetBehandlingId, rad.behandlingId)
        assertEquals(forventetVedtakFattetTidspunkt, rad.vedtakFattetTidspunkt)
        assertTrue(harLagretJson(forventetId, forventetJson), "json-kolonnen skal inneholde meldingen som ble lagret")
    }

    @Test
    fun `lagrer rad uten forsikringsvurdering`() {
        val forventetId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            VedtakFattetMeldingDao(transaction).insert(
                id = forventetId,
                forsikringsvurderingId = null,
                identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                behandlingId = UUID.randomUUID(),
                vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
                json = """{"@event_name":"vedtak_fattet"}""",
            )
        }

        val rad = hentVedtakFattetMelding(forventetId)
        assertNotNull(rad)
        assertNull(rad.forsikringsvurderingId)
    }

    @Test
    fun `ruller tilbake hele transaksjonen når lagring feiler`() {
        val id = UUID.randomUUID()
        val ukjentForsikringsvurderingId = Forsikringsvurdering.Id(UUID.randomUUID())

        runCatching {
            dataSource.inTransaction { transaction ->
                val dao = VedtakFattetMeldingDao(transaction)
                dao.insert(
                    id = id,
                    forsikringsvurderingId = null,
                    identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                    behandlingId = UUID.randomUUID(),
                    vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
                    json = """{"@event_name":"vedtak_fattet"}""",
                )
                dao.insert(
                    id = UUID.randomUUID(),
                    forsikringsvurderingId = ukjentForsikringsvurderingId,
                    identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                    behandlingId = UUID.randomUUID(),
                    vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
                    json = """{"@event_name":"vedtak_fattet"}""",
                )
            }
        }

        assertNull(hentVedtakFattetMelding(id))
        assertEquals(0, antallRader("vedtak_fattet_melding"))
    }

    @Test
    fun `eksisterer er true for lagret melding og false for ukjent id`() {
        val lagretId = UUID.randomUUID()
        val ukjentId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            VedtakFattetMeldingDao(transaction).insert(
                id = lagretId,
                forsikringsvurderingId = null,
                identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                behandlingId = UUID.randomUUID(),
                vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
                json = """{"@event_name":"vedtak_fattet"}""",
            )
        }

        dataSource.inTransaction { transaction ->
            val dao = VedtakFattetMeldingDao(transaction)
            assertTrue(dao.eksisterer(lagretId))
            assertFalse(dao.eksisterer(ukjentId))
        }
    }

    @Test
    fun `eksisterer ser rader som er lagret i samme transaksjon`() {
        val id = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            val dao = VedtakFattetMeldingDao(transaction)
            assertFalse(dao.eksisterer(id))
            dao.insert(
                id = id,
                forsikringsvurderingId = null,
                identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
                behandlingId = UUID.randomUUID(),
                vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
                json = """{"@event_name":"vedtak_fattet"}""",
            )
            assertTrue(dao.eksisterer(id))
        }
    }

    private data class VedtakFattetMeldingRad(
        val id: UUID,
        val forsikringsvurderingId: UUID?,
        val identitetsnummer: String,
        val behandlingId: UUID,
        val vedtakFattetTidspunkt: Instant,
    )

    private fun hentVedtakFattetMelding(id: UUID): VedtakFattetMeldingRad? =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT id, forsikringsvurdering_id, identitetsnummer, behandling_id, vedtak_fattet_tidspunkt
                    FROM vedtak_fattet_melding
                    WHERE id = ?
                    """.trimIndent(),
                    id,
                ).map { row ->
                    VedtakFattetMeldingRad(
                        id = row.uuid("id"),
                        forsikringsvurderingId = row.uuidOrNull("forsikringsvurdering_id"),
                        identitetsnummer = row.string("identitetsnummer"),
                        behandlingId = row.uuid("behandling_id"),
                        vedtakFattetTidspunkt = row.instant("vedtak_fattet_tidspunkt"),
                    )
                }.asSingle,
            )
        }

    private fun harLagretJson(
        id: UUID,
        forventetJson: String,
    ): Boolean =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT json = ?::jsonb FROM vedtak_fattet_melding WHERE id = ?",
                    forventetJson,
                    id,
                ).map { it.boolean(1) }.asSingle,
            )!!
        }

    private fun antallRader(tabell: String): Int =
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SELECT COUNT(*) FROM $tabell").map { it.int(1) }.asSingle)!!
        }
}
