package no.nav.helse.sykepenger.forsikring.oppgaver.adapter.rapids

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur.SelvstendigUtbetaltEtterVentetidRiver
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows

class SelvstendigUtbetaltEtterVentetidRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsvurderingRepository = FakeForsikringsvurderingRepository()
    private val oppgaveClient = TestOppgaveClient()

    init {
        SelvstendigUtbetaltEtterVentetidRiver(
            rapidsConnection = testRapid,
            oppgaveClient = oppgaveClient,
            forsikringsvurderingRepository = forsikringsvurderingRepository
        )
    }

    @Test
    fun `oppretter gosysoppgave`() {

        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        forsikringsvurderingRepository.seed(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = true,
                grad = 80
            ),
            opphørsdato = null,
            forsikringskategori = null
        ))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `Kaster error om det ikke finnes vurdering for forsikringsvurderingId`() {
        // given
        // when
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(event(ForsikringsvurderingId.ny())) }

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Test
    fun `får ikke utbetalt sykepenger fra dag én`() {
        // given
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        forsikringsvurderingRepository.seed(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = false,
                grad = 100
            ),
            opphørsdato = null,
            forsikringskategori = null
        ))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Test
    fun `ikke forsikret med 80 prosent dekningsgrad`() {
        /// given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        forsikringsvurderingRepository.seed(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = true,
                grad = 100
            ),
            opphørsdato = null,
            forsikringskategori = null
        ))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Language("JSON")
    private fun event(forsikringsvurderingId: ForsikringsvurderingId) = """
            {
                "@event_name": "selvstendig_utbetalt_etter_ventetid",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01",
                "behandlingId": "${UUID.randomUUID()}",
                "forsikringsvurderingId": "${forsikringsvurderingId.value}"
            }
        """.trimIndent()
}
