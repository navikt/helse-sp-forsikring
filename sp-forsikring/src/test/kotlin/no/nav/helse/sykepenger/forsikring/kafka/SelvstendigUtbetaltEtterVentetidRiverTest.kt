package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.shared.testsupport.OppgaveOppsamler
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelvstendigUtbetaltEtterVentetidRiverTest {
    private val testRapid = TestRapid()
    private val oppgaveOppsamler = OppgaveOppsamler()

    init {
        SelvstendigUtbetaltEtterVentetidRiver(
            rapidsConnection = testRapid,
            gosysOppgaveClient = oppgaveOppsamler.client,
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
        )
    }

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
        testRapid.reset()
    }

    @Test
    fun `oppretter gosysoppgave på 80 prosent fra dag 1`() {
        val identitetsnummer = lagIdentitetsnummer()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                identitetsnummer = identitetsnummer,
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId, identitetsnummer.value))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, oppgave.årsak)
        assertEquals(identitetsnummer.value, oppgave.fødselsnummer)
    }

    @Test
    fun `oppretter gosysoppgave på 100 prosent fra dag 1 for jordbruker`() {
        val identitetsnummer = lagIdentitetsnummer()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                identitetsnummer = identitetsnummer,
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
                kollektivForsikring = KollektivForsikring.JORDBRUKER,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId, identitetsnummer.value))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker, oppgave.årsak)
        assertEquals(identitetsnummer.value, oppgave.fødselsnummer)
    }

    @Test
    fun `lager ingen oppgave for forsikring som ikke gjelder fra dag én`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave for 100 prosent fra dag 1 uten jordbruk`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave når det bare er kollektiv forsikring`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
                kollektivForsikring = KollektivForsikring.FISKER_BLAD_B,
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `kaster error om det ikke finnes vurdering for forsikringsvurderingId`() {
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(event(Forsikringsvurdering.Id.ny())) }

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Language("JSON")
    private fun event(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        fødselsnummer: String = lagIdentitetsnummer().value,
    ) = """
        {
            "@event_name": "selvstendig_utbetalt_etter_ventetid",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$fødselsnummer",
            "skjæringstidspunkt": "2026-01-01",
            "behandlingId": "${UUID.randomUUID()}",
            "forsikringsvurderingId": "${forsikringsvurderingId.value}"
        }
        """.trimIndent()
}
