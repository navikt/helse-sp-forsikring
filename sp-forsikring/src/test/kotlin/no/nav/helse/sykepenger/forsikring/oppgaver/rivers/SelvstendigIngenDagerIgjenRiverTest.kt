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

class SelvstendigIngenDagerIgjenRiverTest {

    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val oppgaveClient = TestOppgaveClient()
    private val forsikringsvurderingRepository = object : IForsikringsvurderingRepository {
        private val vurderinger = mutableListOf<Forsikringsvurdering>()
        override fun lagre(forsikringsvurdering: Forsikringsvurdering) {
            vurderinger.add(forsikringsvurdering)
        }
        override fun hent(id: ForsikringsvurderingId): Forsikringsvurdering? {
            return vurderinger.firstOrNull() { it.id == id }
        }
    }

    init {
        SelvstendigIngenDagerIgjenRiver(
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
                grad = 100
            ),
            opphørsdato = null
        ))

        // when
        testRapid.sendTestMessage(lagEvent(forsikringsvurderingId))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `Lager ikke oppgave for meldinger uten forsikringsvurderingId`() {
        // given
        // when
        testRapid.sendTestMessage(
            """
            {
                "@event_name": "selvstendig_ingen_dager_igjen",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01",
                "behandlingId": "${UUID.randomUUID()}"
            }
        """.trimIndent()
        )

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Test
    fun `Kaster error om det ikke finnes vurdering for forsikringsvurderingId`() {
        // given
        // when
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(lagEvent(ForsikringsvurderingId.ny())) }

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Language("JSON")
    private fun lagEvent(forsikringsvurderingId: ForsikringsvurderingId) = """
            {
                "@event_name": "selvstendig_ingen_dager_igjen",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01",
                "forsikringsvurderingId": "${forsikringsvurderingId.value}",
                "behandlingId": "${UUID.randomUUID()}"
            }
        """.trimIndent()
}
