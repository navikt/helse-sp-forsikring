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
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows

class SelvstendigIngenDagerIgjenRiverTest {

    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val oppgaveClient = TestOppgaveClient()
    private val forsikringsvurderingRepository = FakeForsikringsvurderingRepository()

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
