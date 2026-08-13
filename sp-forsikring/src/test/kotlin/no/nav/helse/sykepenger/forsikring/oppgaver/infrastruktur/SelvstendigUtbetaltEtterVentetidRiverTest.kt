package no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagIfVedrift10Id
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelvstendigUtbetaltEtterVentetidRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsvurderingRepository = FakeForsikringsvurderingRepository()
    private val oppslagRepository = FakeOppslagRepository()
    private val oppgaveClient = TestOppgaveClient()

    init {
        SelvstendigUtbetaltEtterVentetidRiver(
            rapidsConnection = testRapid,
            oppgaveClient = oppgaveClient,
            forsikringsvurderingRepository = forsikringsvurderingRepository,
            oppslagRepository = oppslagRepository,
        )
    }

    @Test
    fun `oppretter gosysoppgave på 80 % fra dag 1`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = BigDecimal("200000.0"),
                erBetaltNoenGang = true,
            )

        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = oppslagId,
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = true,
                dekning =
                    Forsikringsvurdering.Dekning(
                        iVentetid = true,
                        grad = 80,
                    ),
                opphørsdato = null,
                forsikringskategori = Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id),
            ),
        )

        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `oppretter gosysoppgave på 100 % fra dag 1 jordbrukerforsikring`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = BigDecimal("200000.0"),
                erBetaltNoenGang = true,
            )

        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = oppslagId,
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = true,
                dekning =
                    Forsikringsvurdering.Dekning(
                        iVentetid = true,
                        grad = 100,
                    ),
                opphørsdato = null,
                forsikringskategori = Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id),
            ),
        )

        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker, actual.årsak)
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
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = BigDecimal("200000.0"),
                erBetaltNoenGang = true,
            )

        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = oppslagId,
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = true,
                dekning =
                    Forsikringsvurdering.Dekning(
                        iVentetid = false,
                        grad = 100,
                    ),
                opphørsdato = null,
                forsikringskategori = Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id),
            ),
        )
        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Test
    fun `ikke forsikret med 80 prosent dekningsgrad`() {
        // / given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = BigDecimal("200000.0"),
                erBetaltNoenGang = true,
            )

        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = oppslagId,
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = true,
                dekning =
                    Forsikringsvurdering.Dekning(
                        iVentetid = true,
                        grad = 100,
                    ),
                opphørsdato = null,
                forsikringskategori = Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id),
            ),
        )
        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId))

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Language("JSON")
    private fun event(forsikringsvurderingId: ForsikringsvurderingId) =
        """
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
