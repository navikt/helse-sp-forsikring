package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtbetalingPerForsikringstypeDaoTest {
    private val dataSource = TestcontainersSpForsikringDatabase.dataSource

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `lagrer utbetaling for navkjøpt forsikring`() {
        val meldingId = UUID.randomUUID()
        val forventetId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).lagre(
                id = forventetId,
                vedtakFattetMeldingId = meldingId,
                forsikringstype = Forsikringstype.NavKjøpt(NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1),
                utbetaltIVentetid = 100,
                utbetaltUtenomVentetid = 3272,
            )
        }

        val rad = hentUtbetaling(forventetId)
        assertNotNull(rad)
        assertEquals(meldingId, rad.vedtakFattetMeldingId)
        assertEquals(100, rad.utbetaltIVentetid)
        assertEquals(3272, rad.utbetaltUtenomVentetid)
        assertEquals(NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.name, rad.navkjøptForsikringType)
        assertNull(rad.kollektivForsikringType)
    }

    @Test
    fun `lagrer utbetaling for kollektiv forsikring`() {
        val meldingId = UUID.randomUUID()
        val forventetId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).lagre(
                id = forventetId,
                vedtakFattetMeldingId = meldingId,
                forsikringstype = Forsikringstype.Kollektiv(KollektivForsikring.JORDBRUKER),
                utbetaltIVentetid = 0,
                utbetaltUtenomVentetid = 500,
            )
        }

        val rad = hentUtbetaling(forventetId)
        assertNotNull(rad)
        assertEquals(KollektivForsikring.JORDBRUKER.name, rad.kollektivForsikringType)
        assertNull(rad.navkjøptForsikringType)
        assertEquals(0, rad.utbetaltIVentetid)
        assertEquals(500, rad.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer flere forsikringstyper på samme melding`() {
        val meldingId = UUID.randomUUID()
        val navkjøptId = UUID.randomUUID()
        val kollektivId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            val dao = UtbetalingPerForsikringstypeDao(transaction)
            dao.lagre(
                id = navkjøptId,
                vedtakFattetMeldingId = meldingId,
                forsikringstype =
                    Forsikringstype.NavKjøpt(NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1),
                utbetaltIVentetid = 100,
                utbetaltUtenomVentetid = 0,
            )
            dao.lagre(
                id = kollektivId,
                vedtakFattetMeldingId = meldingId,
                forsikringstype = Forsikringstype.Kollektiv(KollektivForsikring.JORDBRUKER),
                utbetaltIVentetid = 0,
                utbetaltUtenomVentetid = 3272,
            )
        }

        assertEquals(2, antallUtbetalingerFor(meldingId))
        assertEquals(
            NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1.name,
            hentUtbetaling(navkjøptId)?.navkjøptForsikringType,
        )
        assertEquals(KollektivForsikring.JORDBRUKER.name, hentUtbetaling(kollektivId)?.kollektivForsikringType)
    }

    @Test
    fun `ruller tilbake hele transaksjonen når lagring feiler`() {
        val meldingId = UUID.randomUUID()
        val id = UUID.randomUUID()

        runCatching {
            dataSource.inTransaction { transaction ->
                lagreMelding(transaction, meldingId)
                val dao = UtbetalingPerForsikringstypeDao(transaction)
                dao.lagre(
                    id = id,
                    vedtakFattetMeldingId = meldingId,
                    forsikringstype = Forsikringstype.Kollektiv(KollektivForsikring.FISKER_BLAD_B),
                    utbetaltIVentetid = 100,
                    utbetaltUtenomVentetid = 3272,
                )
                dao.lagre(
                    id = UUID.randomUUID(),
                    vedtakFattetMeldingId = UUID.randomUUID(),
                    forsikringstype = Forsikringstype.Kollektiv(KollektivForsikring.FISKER_BLAD_B),
                    utbetaltIVentetid = 100,
                    utbetaltUtenomVentetid = 3272,
                )
            }
        }

        assertNull(hentUtbetaling(id))
        assertEquals(0, antallUtbetalingerFor(meldingId))
    }

    @Test
    fun `tillater ikke to rader med samme forsikringstype på samme melding`() {
        val meldingId = UUID.randomUUID()

        val resultat =
            runCatching {
                dataSource.inTransaction { transaction ->
                    lagreMelding(transaction, meldingId)
                    val dao = UtbetalingPerForsikringstypeDao(transaction)
                    repeat(2) {
                        dao.lagre(
                            id = UUID.randomUUID(),
                            vedtakFattetMeldingId = meldingId,
                            forsikringstype = Forsikringstype.Kollektiv(KollektivForsikring.FISKER_BLAD_B),
                            utbetaltIVentetid = 100,
                            utbetaltUtenomVentetid = 3272,
                        )
                    }
                }
            }

        val feil = assertNotNull(resultat.exceptionOrNull())
        assertTrue(
            feil.stackTraceToString().contains("utbetaling_per_forsikringstype_unik_type_per_melding"),
            "forventet brudd på unik-constrainten, men fikk: $feil",
        )
        assertEquals(0, antallUtbetalingerFor(meldingId))
    }

    private data class UtbetalingPerForsikringstypeRad(
        val id: UUID,
        val vedtakFattetMeldingId: UUID,
        val utbetaltIVentetid: Int,
        val utbetaltUtenomVentetid: Int,
        val kollektivForsikringType: String?,
        val navkjøptForsikringType: String?,
    )

    private fun hentUtbetaling(id: UUID): UtbetalingPerForsikringstypeRad? =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT id, vedtak_fattet_melding_id, utbetalt_i_ventetid, utbetalt_utenom_ventetid,
                           kollektiv_forsikring_type, navkjøpt_forsikring_type
                    FROM utbetaling_per_forsikringstype
                    WHERE id = ?
                    """.trimIndent(),
                    id,
                ).map { row ->
                    UtbetalingPerForsikringstypeRad(
                        id = row.uuid("id"),
                        vedtakFattetMeldingId = row.uuid("vedtak_fattet_melding_id"),
                        utbetaltIVentetid = row.int("utbetalt_i_ventetid"),
                        utbetaltUtenomVentetid = row.int("utbetalt_utenom_ventetid"),
                        kollektivForsikringType = row.stringOrNull("kollektiv_forsikring_type"),
                        navkjøptForsikringType = row.stringOrNull("navkjøpt_forsikring_type"),
                    )
                }.asSingle,
            )
        }

    private fun antallUtbetalingerFor(vedtakFattetMeldingId: UUID): Int =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT COUNT(*) FROM utbetaling_per_forsikringstype WHERE vedtak_fattet_melding_id = ?",
                    vedtakFattetMeldingId,
                ).map { it.int(1) }.asSingle,
            )!!
        }

    private fun lagreMelding(
        transaction: TransactionalSession,
        meldingId: UUID,
    ) {
        VedtakFattetMeldingDao(transaction).lagre(
            id = meldingId,
            forsikringsvurderingId = null,
            identitetsnummer = Identitetsnummer.fraString(TESTFØDSELSNUMMER),
            behandlingId = UUID.randomUUID(),
            vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
            json = """{"@event_name":"vedtak_fattet"}""",
        )
    }
}
