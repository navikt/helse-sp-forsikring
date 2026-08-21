package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import no.nav.helse.sykepenger.forsikring.shared.testsupport.OppgaveOppsamler
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VedtakFattetRiverTest {
    private val testRapid = TestRapid()
    private val oppgaveOppsamler = OppgaveOppsamler()

    init {
        VedtakFattetRiver(
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
    fun `lager oppgave når vi har avvik`() {
        val premiegrunnlag = 200000
        val identitetsnummer = lagIdentitetsnummer()
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                identitetsnummer = identitetsnummer,
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                            premiegrunnlag = premiegrunnlag,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId, identitetsnummer.value))

        val oppgave = oppgaveOppsamler.sisteOppgave
        assertNotNull(oppgave)
        val årsak = assertIs<Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag>(oppgave.årsak)
        assertEquals(0, årsak.sykepengegrunnlag.compareTo(BigDecimal("400000")))
        assertEquals(0, årsak.premiegrunnlag.compareTo(BigDecimal(premiegrunnlag)))
        assertEquals(0, årsak.avviksprosent.compareTo(BigDecimal("50.00")))
        assertEquals(identitetsnummer.value, oppgave.fødselsnummer)
    }

    @Test
    fun `lager ingen oppgave når det ikke er avvik`() {
        val forsikringsvurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                navKjøpteForsikringer =
                    listOf(
                        lagVurdertNavKjøptForsikring(
                            type = NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                            premiegrunnlag = 400000,
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
    fun `lager ingen oppgave når det er kollektiv forsikring`() {
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
    fun `lager ingen oppgave når det ikke er forsikring i vurderingen`() {
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-01-01"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId =
            forsikringsvurdering.id

        testRapid.sendTestMessage(event(forsikringsvurderingId))

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Test
    fun `kaster exception på ukjent forsikringsvurderingId`() {
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(event(Forsikringsvurdering.Id.ny()))
        }

        assertNull(oppgaveOppsamler.sisteOppgave)
    }

    @Language("JSON")
    private fun event(
        forsikringsvurderingId: Forsikringsvurdering.Id,
        fødselsnummer: String = lagIdentitetsnummer().value,
    ) = """
        {
            "@event_name": "vedtak_fattet",
            "@id": "${UUID.randomUUID()}",
            "yrkesaktivitetstype": "SELVSTENDIG",
            "tags": ["Førstegangsbehandling"],
            "skjæringstidspunkt": "2026-01-01",
            "sykepengegrunnlag": 400000,
            "fødselsnummer": "$fødselsnummer",
            "forsikringsvurderingId": "${forsikringsvurderingId.value}"
        }
        """.trimIndent()
}
