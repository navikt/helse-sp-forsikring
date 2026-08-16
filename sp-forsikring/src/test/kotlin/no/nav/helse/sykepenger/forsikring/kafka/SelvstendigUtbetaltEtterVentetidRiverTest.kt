package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.shared.testsupport.Infotrygdforsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.OppgaveOppsamler
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TESTFØDSELSNUMMER
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreForsikringsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
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
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, oppgave.årsak)
        assertEquals(TESTFØDSELSNUMMER, oppgave.fødselsnummer)
    }

    @Test
    fun `oppretter gosysoppgave på 100 prosent fra dag 1 for jordbruker`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.JORDBRUKER),
                forsikringer =
                    listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker, oppgave.årsak)
        assertEquals(TESTFØDSELSNUMMER, oppgave.fødselsnummer)
    }

    @Test
    fun `lager ingen oppgave for forsikring som ikke gjelder fra dag én`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17)),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave for 100 prosent fra dag 1 uten jordbruk`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                forsikringer = listOf(Infotrygdforsikring(type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1)),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ingen oppgave når det bare er kollektiv forsikring`() {
        val forsikringsvurderingId =
            lagreForsikringsvurdering(
                spesielleYrkesgrupper = setOf(SpesiellYrkesgruppe.FISKER_BLAD_B),
            )

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `kaster error om det ikke finnes vurdering for forsikringsvurderingId`() {
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(event(Forsikringsvurdering.Id.ny())) }

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Language("JSON")
    private fun event(forsikringsvurderingId: Forsikringsvurdering.Id) =
        """
        {
            "@event_name": "selvstendig_utbetalt_etter_ventetid",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$TESTFØDSELSNUMMER",
            "skjæringstidspunkt": "2026-01-01",
            "behandlingId": "${UUID.randomUUID()}",
            "forsikringsvurderingId": "${forsikringsvurderingId.value}"
        }
        """.trimIndent()
}
