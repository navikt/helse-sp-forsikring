package no.nav.helse.sykepenger.forsikring.forsikringsvurdering.application

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.adapter.postgres.PgForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.oppslag.adapter.oracle.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.oppslag.adapter.oracle.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.oppslag.application.OppslagService
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ForsikringsvurderingTest {
    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `gjørVurdering returnerer løsning med forsikring uten TestRapid`() {
        insertBetaltVedfrivt(IF10_TYPE = '2')

        val vurdering = medService { session ->
            gjørVurdering(
                session = session,
                behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                fødselsnummer = FØDSELSNUMMER,
                spesielleYrkesgrupper = emptySet(),
                yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
            )
        }

        val dekning = assertIs<Forsikringsvurdering.Dekning>(vurdering.dekning)
        val valgtType = assertIs<Forsikringskategori.NavKjøptForsikring>(vurdering.forsikringskategori)
        val hentet = PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource).hent(vurdering.id)

        assertTrue(vurdering.harForsikring)
        assertNull(vurdering.opphørsdato)
        assertEquals(100, dekning.grad)
        assertEquals(false, dekning.iVentetid)
        assertEquals(valgtType.id, (hentet?.forsikringskategori as? Forsikringskategori.NavKjøptForsikring)?.id)
        assertEquals(1, TestcontainersSpForsikringDatabase.countOppslag(vurdering.id.value.toString()))
    }

    @Test
    fun `gjørVurdering lagrer kollektiv forsikringstype`() {
        val vurdering = medService { session ->
            gjørVurdering(
                session = session,
                behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                fødselsnummer = FØDSELSNUMMER,
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.Fisker(SpesiellYrkesgruppe.Fisker.Blad.B)),
                yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
            )
        }

        val hentet = PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource).hent(vurdering.id)
        assertEquals(Forsikringskategori.KollektivForsikring, hentet?.forsikringskategori)
    }

    @Test
    fun `gjørVurdering inneholder opphørsdato når den er satt`() {
        insertBetaltVedfrivt(IF10_TYPE = '2', IF10_FORSTOM = 20260331)
        val vurdering = medService { session ->
            gjørVurdering(
                session = session,
                behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                fødselsnummer = FØDSELSNUMMER,
                spesielleYrkesgrupper = emptySet(),
                yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
            )
        }
        val dekning = assertIs<Forsikringsvurdering.Dekning>(vurdering.dekning)
        assertTrue(vurdering.harForsikring)
        assertEquals(LocalDate.of(2026, 3, 31), vurdering.opphørsdato)
        assertEquals(100, dekning.grad)
        assertEquals(false, dekning.iVentetid)
        assertEquals(1, TestcontainersSpForsikringDatabase.countOppslag(vurdering.id.value.toString()))
    }

    @ParameterizedTest(name = "{0} særskilt {1} infotrygd-type {2}", quoteTextArguments = false)
    @CsvSource(
        "ARBEIDSTAKER, , 1",
        "ARBEIDSTAKER, , 2",
        "ARBEIDSTAKER, , 3",
        "ARBEIDSTAKER, , 4",
        "ARBEIDSTAKER, , 5",
        "SELVSTENDIG, , 4",
        "FRILANS, , 1",
        "FRILANS, , 2",
        "FRILANS, , 3",
        "FRILANS, , 4"
    )
    fun `gjørVurdering feiler ved ugyldig kombinasjon`(yrkesaktivitetstype: String, særskiltGruppe: String?, IF10_TYPE: Char?) {
        IF10_TYPE?.let { insertBetaltVedfrivt(IF10_TYPE = it) }

        assertThrows<AbstractNavKjøptForsikring.Valideringsfeil> {
            medService { session ->
                gjørVurdering(
                    session = session,
                    behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                    skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                    fødselsnummer = FØDSELSNUMMER,
                    spesielleYrkesgrupper = særskiltGruppe?.let {
                        setOf(
                            when (it) {
                                "JORDBRUKER" -> SpesiellYrkesgruppe.Jordbruker
                                "REINDRIFTER" -> SpesiellYrkesgruppe.Reindrifter
                                "FISKER_BLAD_B" -> SpesiellYrkesgruppe.Fisker(SpesiellYrkesgruppe.Fisker.Blad.B)
                                else -> SpesiellYrkesgruppe.Ukjent(it)
                            }
                        )
                    } ?: emptySet(),
                    yrkesaktivitetstype = enumValueOf(yrkesaktivitetstype)
                )
            }
        }
    }

    @Test
    fun `gjørVurdering feiler når dekninger har ulike grader`() {
        insertBetaltVedfrivt(IF10_FORSFOM_SEQ = 1, IF10_TYPE = '1')
        insertBetaltVedfrivt(IF10_FORSFOM_SEQ = 2, IF10_TYPE = '2')

        assertThrows<IllegalStateException> {
            medService { session ->
                gjørVurdering(
                    session = session,
                    behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                    skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                    fødselsnummer = FØDSELSNUMMER,
                    spesielleYrkesgrupper = emptySet(),
                    yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
                )
            }
        }
    }

    @Test
    fun `gjørVurdering lagrer ekskluderinger for forsikringer som ikke er kandidater`() {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 1,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260102
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 1,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 2,
            IF10_TYPE = '2',
            IF10_FORSTOM = 20251231
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 2,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 3,
            IF10_TYPE = '2'
        )
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 4,
            IF10_TYPE = '2',
            IF10_VIRKDATO = 20260601
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = 4,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20260101
        )

        val vurdering = medService { session ->
            gjørVurdering(
                session = session,
                behovJson = """{"@behov":["Forsikringsvurdering"]}""",
                skjæringstidspunkt = SKJÆRINGSTIDSPUNKT,
                fødselsnummer = FØDSELSNUMMER,
                spesielleYrkesgrupper = emptySet(),
                yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG
            )
        }

        val ekskluderinger = TestcontainersSpForsikringDatabase.hentEkskluderinger(vurdering.id.value)
        assertEquals(
            mapOf(
                1 to "SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO",
                2 to "OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT",
                3 to "ALDRI_BETALT",
                4 to "SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO"
            ),
            ekskluderinger
        )
    }

    companion object {
        private const val FØDSELSNUMMER = "01020312345"
        private const val INFOTRYGD_FØDSELSNUMMER = 3020112345L
        private val SKJÆRINGSTIDSPUNKT: LocalDate = LocalDate.parse("2026-01-01")
    }

    private fun insertBetaltVedfrivt(
        IF10_FORSFOM_SEQ: Int = 0,
        IF10_TYPE: Char = '1',
        IF10_FORSFOM: Int = 0,
        IF10_VIRKDATO: Int = 20260101,
        IF10_FORSTOM: Int = 0,
    ) = insertVedfrivtMedBetaling(
        IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
        IF10_TYPE = IF10_TYPE,
        IF10_FORSFOM = IF10_FORSFOM,
        IF10_VIRKDATO = IF10_VIRKDATO,
        IF10_FORSTOM = IF10_FORSTOM,
        IF12_BETDATO = 20260101,
    )

    private fun insertVedfrivtMedBetaling(
        IF10_FORSFOM_SEQ: Int = 0,
        IF10_TYPE: Char = '1',
        IF10_FORSFOM: Int = 0,
        IF10_VIRKDATO: Int = 20260101,
        IF10_FORSTOM: Int = 0,
        IF12_BETDATO: Int? = 20260101,
    ) {
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_TYPE = IF10_TYPE,
            IF10_FORSFOM = IF10_FORSFOM,
            IF10_VIRKDATO = IF10_VIRKDATO,
            IF10_FORSTOM = IF10_FORSTOM,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = INFOTRYGD_FØDSELSNUMMER,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = 1,
            IF12_FOM = 20250101,
            IF12_TOM = 20261231,
            IF12_BETDATO = IF12_BETDATO,
        )
    }

    private fun <T> medService(block: ForsikringsvurderingService.(TransactionalSession) -> T): T {
        val repository = PgForsikringsvurderingRepository(TestcontainersSpForsikringDatabase.dataSource)
        val replikabaseDao = ReplikabaseDao(TestcontainersReplikadatabase.dataSource)
        val oppslagService = OppslagService(replikabaseDao)
        val service = ForsikringsvurderingService(repository, oppslagService)
        return TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
            service.block(transaction)
        }
    }
}
