package no.nav.helse.sykepenger.forsikring.oppgaver.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.IForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.oppgaver.Årsak
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows

class SelvstendigUtbetaltEtterVentetidRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsvurderingRepository = object : IForsikringsvurderingRepository {
        private val vurderinger = mutableListOf<Forsikringsvurdering>()
        override fun lagre(forsikringsvurdering: Forsikringsvurdering) {
            vurderinger.add(forsikringsvurdering)
        }
        override fun hent(id: ForsikringsvurderingId): Forsikringsvurdering? {
            return vurderinger.firstOrNull() { it.id == id }
        }
    }
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
        forsikringsvurderingRepository.lagre(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = true,
                grad = 80
            ),
            opphørsdato = null
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
        forsikringsvurderingRepository.lagre(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = false,
                grad = 100
            ),
            opphørsdato = null
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
        forsikringsvurderingRepository.lagre(Forsikringsvurdering.fraLagring(
            id = forsikringsvurderingId,
            oppslagId = OppslagId.ny(),
            behovJson = "{}",
            ekskluderinger = emptyList(),
            harForsikring = true,
            dekning = Forsikringsvurdering.Dekning(
                iVentetid = true,
                grad = 100
            ),
            opphørsdato = null
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
