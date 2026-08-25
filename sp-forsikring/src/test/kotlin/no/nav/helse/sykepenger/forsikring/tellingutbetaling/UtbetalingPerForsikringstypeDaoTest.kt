package no.nav.helse.sykepenger.forsikring.tellingutbetaling

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.BeforeEach
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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
    fun `lagrer utbetaling for individuell forsikring`() {
        val meldingId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                utbetaltIVentetid = kr("100"),
                utbetaltUtenomVentetid = kr("3272"),
            )
        }

        val rad = assertNotNull(hentUtbetalingerFor(meldingId).singleOrNull())
        assertEquals(meldingId, rad.vedtakFattetMeldingId)
        assertBeløp("100", rad.utbetaltIVentetid)
        assertBeløp("3272", rad.utbetaltUtenomVentetid)
        assertEquals(IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1.name, rad.individuellForsikringType)
        assertNull(rad.kollektivForsikringType)
    }

    @Test
    fun `lagrer utbetaling for kollektiv forsikring`() {
        val meldingId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = KollektivForsikring.JORDBRUKER,
                utbetaltIVentetid = kr("0"),
                utbetaltUtenomVentetid = kr("500"),
            )
        }

        val rad = assertNotNull(hentUtbetalingerFor(meldingId).singleOrNull())
        assertEquals(KollektivForsikring.JORDBRUKER.name, rad.kollektivForsikringType)
        assertNull(rad.individuellForsikringType)
        assertBeløp("0", rad.utbetaltIVentetid)
        assertBeløp("500", rad.utbetaltUtenomVentetid)
    }

    @Test
    fun `lagrer flere forsikringstyper på samme melding`() {
        val meldingId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            val dao = UtbetalingPerForsikringstypeDao(transaction)
            dao.insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                utbetaltIVentetid = kr("100"),
                utbetaltUtenomVentetid = kr("0"),
            )
            dao.insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = KollektivForsikring.JORDBRUKER,
                utbetaltIVentetid = kr("0"),
                utbetaltUtenomVentetid = kr("3272"),
            )
        }

        assertEquals(2, antallUtbetalingerFor(meldingId))
        val rader = hentUtbetalingerFor(meldingId)
        assertEquals(
            IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1.name,
            rader.single { it.individuellForsikringType != null }.individuellForsikringType,
        )
        assertEquals(
            KollektivForsikring.JORDBRUKER.name,
            rader.single { it.kollektivForsikringType != null }.kollektivForsikringType,
        )
    }

    @Test
    fun `ruller tilbake hele transaksjonen når lagring feiler`() {
        val meldingId = UUID.randomUUID()

        runCatching {
            dataSource.inTransaction { transaction ->
                lagreMelding(transaction, meldingId)
                val dao = UtbetalingPerForsikringstypeDao(transaction)
                dao.insert(
                    vedtakFattetMeldingId = meldingId,
                    forsikringstype = KollektivForsikring.FISKER_BLAD_B,
                    utbetaltIVentetid = kr("100"),
                    utbetaltUtenomVentetid = kr("3272"),
                )
                dao.insert(
                    vedtakFattetMeldingId = UUID.randomUUID(),
                    forsikringstype = KollektivForsikring.FISKER_BLAD_B,
                    utbetaltIVentetid = kr("100"),
                    utbetaltUtenomVentetid = kr("3272"),
                )
            }
        }

        assertNull(hentUtbetalingerFor(meldingId).singleOrNull())
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
                        dao.insert(
                            vedtakFattetMeldingId = meldingId,
                            forsikringstype = KollektivForsikring.FISKER_BLAD_B,
                            utbetaltIVentetid = kr("100"),
                            utbetaltUtenomVentetid = kr("3272"),
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

    @Test
    fun `summerer per forsikringstype innenfor perioden`() {
        val iPerioden = UUID.randomUUID()
        val ogsåIPerioden = UUID.randomUUID()
        val utenforPerioden = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, iPerioden, Instant.parse("2026-07-01T22:00:00Z"))
            lagreMelding(transaction, ogsåIPerioden, Instant.parse("2026-07-03T09:00:00Z"))
            lagreMelding(transaction, utenforPerioden, Instant.parse("2026-07-04T09:00:00Z"))

            val dao = UtbetalingPerForsikringstypeDao(transaction)
            dao.insert(iPerioden, KollektivForsikring.JORDBRUKER, utbetaltIVentetid = kr("100"), utbetaltUtenomVentetid = kr("200"))
            dao.insert(
                iPerioden,
                IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                utbetaltIVentetid = kr("10"),
                utbetaltUtenomVentetid = kr("20"),
            )
            dao.insert(ogsåIPerioden, KollektivForsikring.JORDBRUKER, utbetaltIVentetid = kr("1"), utbetaltUtenomVentetid = kr("2"))
            dao.insert(utenforPerioden, KollektivForsikring.JORDBRUKER, utbetaltIVentetid = kr("9999"), utbetaltUtenomVentetid = kr("9999"))
        }

        val summer = summerPerForsikringstype(fom = LocalDate.of(2026, 7, 2), tom = LocalDate.of(2026, 7, 3))

        assertEquals(2, summer.size)
        val kollektiv = assertNotNull(summer.singleOrNull { it.forsikringstype == KollektivForsikring.JORDBRUKER })
        assertBeløp("101", kollektiv.utbetaltIVentetid)
        assertBeløp("202", kollektiv.utbetaltUtenomVentetid)
        assertBeløp("303", kollektiv.totalt)

        val individuell =
            assertNotNull(summer.singleOrNull { it.forsikringstype == IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1 })
        assertBeløp("10", individuell.utbetaltIVentetid)
        assertBeløp("20", individuell.utbetaltUtenomVentetid)
        assertBeløp("30", individuell.totalt)
    }

    @Test
    fun `tar med vedtak fattet på både fom- og tom-dagen i norsk tid`() {
        val førsteMinuttAvFom = UUID.randomUUID()
        val sisteMinuttAvTom = UUID.randomUUID()
        val likeFørFom = UUID.randomUUID()
        val likeEtterTom = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            // 2026-07-02T00:00 norsk tid = 2026-07-01T22:00Z (sommertid)
            lagreMelding(transaction, likeFørFom, Instant.parse("2026-07-01T21:59:59Z"))
            lagreMelding(transaction, førsteMinuttAvFom, Instant.parse("2026-07-01T22:00:00Z"))
            lagreMelding(transaction, sisteMinuttAvTom, Instant.parse("2026-07-03T21:59:59Z"))
            lagreMelding(transaction, likeEtterTom, Instant.parse("2026-07-03T22:00:00Z"))

            val dao = UtbetalingPerForsikringstypeDao(transaction)
            listOf(likeFørFom, førsteMinuttAvFom, sisteMinuttAvTom, likeEtterTom).forEach {
                dao.insert(it, KollektivForsikring.JORDBRUKER, utbetaltIVentetid = kr("0"), utbetaltUtenomVentetid = kr("1"))
            }
        }

        val summer = summerPerForsikringstype(fom = LocalDate.of(2026, 7, 2), tom = LocalDate.of(2026, 7, 3))

        assertBeløp("2", assertNotNull(summer.singleOrNull()).totalt)
    }

    @Test
    fun `returnerer tom liste når ingen vedtak er fattet i perioden`() {
        val summer = summerPerForsikringstype(fom = LocalDate.of(2026, 7, 2), tom = LocalDate.of(2026, 7, 3))

        assertTrue(summer.isEmpty())
    }

    @Test
    fun `avviser periode der fom er etter tom`() {
        val resultat =
            runCatching { summerPerForsikringstype(fom = LocalDate.of(2026, 7, 3), tom = LocalDate.of(2026, 7, 2)) }

        assertTrue(resultat.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `lagrer beløp med to desimaler`() {
        val meldingId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = KollektivForsikring.JORDBRUKER,
                utbetaltIVentetid = kr("1234.56"),
                utbetaltUtenomVentetid = kr("0.05"),
            )
        }

        val rad = assertNotNull(hentUtbetalingerFor(meldingId).singleOrNull())
        assertBeløp("1234.56", rad.utbetaltIVentetid)
        assertBeløp("0.05", rad.utbetaltUtenomVentetid)
    }

    @Test
    fun `runder av til to desimaler når beløpet har flere desimaler`() {
        val meldingId = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, meldingId)
            UtbetalingPerForsikringstypeDao(transaction).insert(
                vedtakFattetMeldingId = meldingId,
                forsikringstype = KollektivForsikring.JORDBRUKER,
                utbetaltIVentetid = kr("100.005"),
                utbetaltUtenomVentetid = kr("99.994"),
            )
        }

        val rad = assertNotNull(hentUtbetalingerFor(meldingId).singleOrNull())
        assertBeløp("100.01", rad.utbetaltIVentetid)
        assertBeløp("99.99", rad.utbetaltUtenomVentetid)
    }

    @Test
    fun `summerer desimalbeløp uten å miste presisjon`() {
        val førsteMelding = UUID.randomUUID()
        val andreMelding = UUID.randomUUID()

        dataSource.inTransaction { transaction ->
            lagreMelding(transaction, førsteMelding, Instant.parse("2026-07-02T09:00:00Z"))
            lagreMelding(transaction, andreMelding, Instant.parse("2026-07-03T09:00:00Z"))

            val dao = UtbetalingPerForsikringstypeDao(transaction)
            dao.insert(førsteMelding, KollektivForsikring.JORDBRUKER, kr("0.10"), kr("1200.55"))
            dao.insert(andreMelding, KollektivForsikring.JORDBRUKER, kr("0.15"), kr("0.45"))
        }

        val summer = summerPerForsikringstype(fom = LocalDate.of(2026, 7, 2), tom = LocalDate.of(2026, 7, 3))

        val kollektiv = assertNotNull(summer.singleOrNull())
        assertBeløp("0.25", kollektiv.utbetaltIVentetid)
        assertBeløp("1201.00", kollektiv.utbetaltUtenomVentetid)
        assertBeløp("1201.25", kollektiv.totalt)
    }

    private fun kr(beløp: String): BigDecimal = BigDecimal(beløp)

    private fun assertBeløp(
        forventet: String,
        faktisk: BigDecimal,
    ) = assertEquals(
        0,
        BigDecimal(forventet).compareTo(faktisk),
        "Forventet $forventet, men var ${faktisk.toPlainString()}",
    )

    private fun summerPerForsikringstype(
        fom: LocalDate,
        tom: LocalDate,
    ): List<SumPerForsikringstype> =
        dataSource.inTransaction { transaction ->
            UtbetalingPerForsikringstypeDao(transaction).summerPerForsikringstype(fom = fom, tom = tom)
        }

    private data class UtbetalingPerForsikringstypeRad(
        val id: UUID,
        val vedtakFattetMeldingId: UUID,
        val utbetaltIVentetid: BigDecimal,
        val utbetaltUtenomVentetid: BigDecimal,
        val kollektivForsikringType: String?,
        val individuellForsikringType: String?,
    )

    private fun hentUtbetalingerFor(vedtakFattetMeldingId: UUID): List<UtbetalingPerForsikringstypeRad> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT id, vedtak_fattet_melding_id, utbetalt_i_ventetid, utbetalt_utenom_ventetid,
                           kollektiv_forsikring_type, individuell_forsikring_type
                    FROM utbetaling_per_forsikringstype
                    WHERE vedtak_fattet_melding_id = ?
                    """.trimIndent(),
                    vedtakFattetMeldingId,
                ).map { row ->
                    UtbetalingPerForsikringstypeRad(
                        id = row.uuid("id"),
                        vedtakFattetMeldingId = row.uuid("vedtak_fattet_melding_id"),
                        utbetaltIVentetid = row.bigDecimal("utbetalt_i_ventetid"),
                        utbetaltUtenomVentetid = row.bigDecimal("utbetalt_utenom_ventetid"),
                        kollektivForsikringType = row.stringOrNull("kollektiv_forsikring_type"),
                        individuellForsikringType = row.stringOrNull("individuell_forsikring_type"),
                    )
                }.asList,
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
        vedtakFattetTidspunkt: Instant = Instant.parse("2026-07-01T12:51:09.553707Z"),
    ) {
        VedtakFattetMeldingDao(transaction).insert(
            id = meldingId,
            forsikringsvurderingId = null,
            identitetsnummer = lagIdentitetsnummer(),
            behandlingId = UUID.randomUUID(),
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            json = """{"@event_name":"vedtak_fattet"}""",
        )
    }
}
