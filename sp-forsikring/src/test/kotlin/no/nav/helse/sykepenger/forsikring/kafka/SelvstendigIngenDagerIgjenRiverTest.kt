package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
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

class SelvstendigIngenDagerIgjenRiverTest {
    private val testRapid = TestRapid()
    private val oppgaveOppsamler = OppgaveOppsamler()

    init {
        SelvstendigIngenDagerIgjenRiver(
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
    fun `oppretter gosysoppgave`() {
        val forsikringsvurderingId = lagreForsikringsvurdering(forsikringer = listOf(Infotrygdforsikring()))

        testRapid.sendTestMessage(lagEvent(forsikringsvurderingId))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        assertEquals(Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød, oppgave.årsak)
        assertEquals(TESTFØDSELSNUMMER, oppgave.fødselsnummer)
    }

    @Test
    fun `lager ikke oppgave når vurderingen ikke har forsikring`() {
        val forsikringsvurderingId = lagreForsikringsvurdering()

        testRapid.sendTestMessage(lagEvent(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `lager ikke oppgave for meldinger uten forsikringsvurderingId`() {
        testRapid.sendTestMessage(
            """
            {
                "@event_name": "selvstendig_ingen_dager_igjen",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$TESTFØDSELSNUMMER",
                "skjæringstidspunkt": "2026-01-01",
                "behandlingId": "${UUID.randomUUID()}"
            }
            """.trimIndent(),
        )

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `kaster error om det ikke finnes vurdering for forsikringsvurderingId`() {
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(lagEvent(Forsikringsvurdering.Id.ny())) }

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Language("JSON")
    private fun lagEvent(forsikringsvurderingId: Forsikringsvurdering.Id) =
        """
        {
            "@event_name": "selvstendig_ingen_dager_igjen",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$TESTFØDSELSNUMMER",
            "skjæringstidspunkt": "2026-01-01",
            "forsikringsvurderingId": "${forsikringsvurderingId.value}",
            "behandlingId": "${UUID.randomUUID()}"
        }
        """.trimIndent()
}
