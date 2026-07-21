package no.nav.helse.sykepenger.forsikring.telling.infrastruktur

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TellingDaoTest {
    private val tellingDao = TellingDao(TestcontainersSpForsikringDatabase.dataSource)

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `lagrer rad i tell_utbetaling`() {
        val expectedId = UUID.randomUUID()
        val expectedFødselsnummer = "26810697848"
        val expectedVedtaksperiodeId = UUID.randomUUID()
        val expectedVedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z")
        val expectedDekningsgrad = 80
        val expectedHarDekningIVentetid = true
        val expectedUtbetaltIVentetid = 100
        val expectedUtbetaltUtenomVentetid = 3272

        tellingDao.lagre(
            id = expectedId,
            fødselsnummer = expectedFødselsnummer,
            vedtaksperiodeId = expectedVedtaksperiodeId,
            vedtakFattetTidspunkt = expectedVedtakFattetTidspunkt,
            dekningsgrad = expectedDekningsgrad,
            harDekningIVentetid = expectedHarDekningIVentetid,
            utbetaltIVentetid = expectedUtbetaltIVentetid,
            utbetaltUtenomVentetid = expectedUtbetaltUtenomVentetid,
            json = """{"@event_name":"vedtak_fattet","utbetalingsdager":[]}""",
        )

        val rad = tellingDao.hent(expectedId)
        assertNotNull(rad)
        assertEquals(expectedId, rad.id)
        assertEquals(expectedFødselsnummer, rad.fødselsnummer)
        assertEquals(expectedVedtaksperiodeId, rad.vedtaksperiodeId)
        assertEquals(expectedVedtakFattetTidspunkt, rad.vedtakFattetTidspunkt)
        assertEquals(expectedDekningsgrad, rad.dekningsgrad)
        assertEquals(expectedHarDekningIVentetid, rad.harDekningIVentetid)
        assertEquals(expectedUtbetaltIVentetid, rad.utbetaltIVentetid)
        assertEquals(expectedUtbetaltUtenomVentetid, rad.utbetaltUtenomVentetid)
    }

    @Test
    fun `ignorerer duplikatinnsetting med samme id`() {
        val id = UUID.randomUUID()

        tellingDao.lagre(
            id = id,
            fødselsnummer = "26810697848",
            vedtaksperiodeId = UUID.randomUUID(),
            vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
            dekningsgrad = 80,
            harDekningIVentetid = true,
            utbetaltIVentetid = 100,
            utbetaltUtenomVentetid = 3272,
            json = """{"@event_name":"vedtak_fattet","utbetalingsdager":[]}""",
        )

        tellingDao.lagre(
            id = id,
            fødselsnummer = "26810697848",
            vedtaksperiodeId = UUID.randomUUID(),
            vedtakFattetTidspunkt = Instant.parse("2026-07-01T12:51:09.553707Z"),
            dekningsgrad = 80,
            harDekningIVentetid = true,
            utbetaltIVentetid = 100,
            utbetaltUtenomVentetid = 3272,
            json = """{"@event_name":"vedtak_fattet","utbetalingsdager":[]}""",
        )

        val antall =
            sessionOf(TestcontainersSpForsikringDatabase.dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT COUNT(*) FROM tell_utbetaling WHERE id = ?",
                        id,
                    ).map { it.int(1) }.asSingle,
                )!!
            }
        assertEquals(1, antall)
    }
}
