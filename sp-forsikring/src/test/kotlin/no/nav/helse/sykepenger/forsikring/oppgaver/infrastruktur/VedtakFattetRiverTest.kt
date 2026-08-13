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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VedtakFattetRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsvurderingRepository = FakeForsikringsvurderingRepository()
    private val oppslagRepository = FakeOppslagRepository()
    private val oppgaveClient = TestOppgaveClient()

    init {
        VedtakFattetRiver(
            rapidsConnection = testRapid,
            oppgaveClient = oppgaveClient,
            forsikringsvurderingRepository = forsikringsvurderingRepository,
            oppslagRepository = oppslagRepository,
        )
    }

    @Test
    fun `Lager oppgave når vi har avvik`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val premiegrunnlag = BigDecimal("200000.0")
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = premiegrunnlag,
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
                forsikringskategori = (Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id)),
            ),
        )

        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId.value))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        val årsak = assertIs<Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag>(actual.årsak)
        assertEquals(0, årsak.sykepengegrunnlag.compareTo(BigDecimal("400000")))
        assertEquals(0, årsak.premiegrunnlag.compareTo(premiegrunnlag))
        assertEquals(0, årsak.avviksprosent.compareTo(BigDecimal("50.00")))
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `Lager ingen oppgave når det ikke er avvik`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()
        val oppslagIfVedrift10Id = OppslagIfVedrift10Id.ny()
        val premiegrunnlag = BigDecimal("400000.0")
        val navKjøptForsikring =
            NavKjøptForsikring(
                id = oppslagIfVedrift10Id,
                type = AbstractNavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                virkningsdato = LocalDate.of(2024, 1, 1),
                opphørsdato = null,
                opphørsgrunn = null,
                premiegrunnlag = premiegrunnlag,
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
                forsikringskategori = (Forsikringskategori.NavKjøptForsikring(oppslagIfVedrift10Id)),
            ),
        )

        oppslagRepository.lagre(Oppslag(oppslagId, listOf(navKjøptForsikring), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId.value))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Test
    fun `Lager ingen oppgave når det er kollektiv forsikring`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()

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
                forsikringskategori = (Forsikringskategori.KollektivForsikring),
            ),
        )

        oppslagRepository.lagre(Oppslag(oppslagId, emptyList(), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId.value))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Test
    fun `Lager ingen oppgave når det ikke er forsikring i vurderingen`() {
        // given
        val forsikringsvurderingId = ForsikringsvurderingId.ny()
        val oppslagId = OppslagId.ny()

        forsikringsvurderingRepository.seed(
            Forsikringsvurdering.fraLagring(
                id = forsikringsvurderingId,
                oppslagId = oppslagId,
                behovJson = "{}",
                ekskluderinger = emptyList(),
                harForsikring = false,
                dekning = null,
                opphørsdato = null,
                forsikringskategori = null,
            ),
        )
        oppslagRepository.lagre(Oppslag(oppslagId, emptyList(), Instant.now()))

        // when
        testRapid.sendTestMessage(event(forsikringsvurderingId.value))

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Test
    fun `kaster exception på ukjent forsikringsvurderingId`() {
        // given
        // when
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(event(UUID.randomUUID()))
        }
    }

    @Language("JSON")
    private fun event(forsikringsvurderingId: UUID) =
        """
        {
            "@event_name": "vedtak_fattet",
            "@id": "${UUID.randomUUID()}",
            "yrkesaktivitetstype": "SELVSTENDIG",
            "tags": ["Førstegangsbehandling"],
            "skjæringstidspunkt": "2024-01-01",
            "sykepengegrunnlag": 400000,
            "fødselsnummer": "$fødselsnummer",
            "forsikringsvurderingId": "$forsikringsvurderingId"
        }
        """.trimIndent()
}
