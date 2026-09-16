package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.kafka.EndretForsikringsvurderingPubliserer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.*
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class RevurderingServiceTest {
    private val subumsjonspubliserer =
        object : Subsumsjonspubliserer {
            val subsumsjoner = mutableListOf<Forsikringsvurdering>()

            override fun publiser(
                forsikringsvurdering: Forsikringsvurdering,
                vedtaksperiodeId: UUID,
                behandlingId: UUID,
            ) {
                subsumsjoner.add(forsikringsvurdering)
            }
        }

    private val endretForsikringsvurderingPubliserer =
        object : EndretForsikringsvurderingPubliserer {
            val publiserteMeldinger = mutableListOf<Triple<String, LocalDate, UUID>>()

            override fun publiser(
                identitetsnummer: Identitetsnummer,
                skjæringstidspunkt: LocalDate,
                forsikringsvurderingId: UUID,
            ) {
                publiserteMeldinger.add(Triple(identitetsnummer.value, skjæringstidspunkt, forsikringsvurderingId))
            }
        }

    private val revurderingService =
        RevurderingService(
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
            forsikringsvurderingService = ForsikringsvurderingService(TestcontainersReplikadatabase.dataSource),
            subsumsjonspubliserer = subumsjonspubliserer,
            endretForsikringsvurderingPubliserer = endretForsikringsvurderingPubliserer,
        )

    @BeforeEach
    fun beforeEach() {
        TestcontainersReplikadatabase.reset()
        TestcontainersSpForsikringDatabase.reset()
        subumsjonspubliserer.subsumsjoner.clear()
        endretForsikringsvurderingPubliserer.publiserteMeldinger.clear()
    }

    @Test
    fun `returnerer IngenTidligereVurdering når det ikke finnes noen forsikringsvurdering fra før`() {
        val resultat =
            revurderingService.revurder(
                identitetsnummer = lagIdentitetsnummer(),
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                vedtaksperiodeId = UUID.randomUUID(),
                behandlingId = UUID.randomUUID(),
            ) { error("skal ikke bygge behov når det ikke finnes noen tidligere vurdering") }

        assertIs<Revurderingsresultat.IngenTidligereVurdering>(resultat)
        assertTrue(subumsjonspubliserer.subsumsjoner.isEmpty(), "Forventet at ingen subsumsjoner ble publisert")
        assertTrue(
            endretForsikringsvurderingPubliserer.publiserteMeldinger.isEmpty(),
            "Forventet at ingen endret_forsikringsvurdering-melding ble publisert",
        )
    }

    @Test
    fun `returnerer EndretVurdering når forsikringen har falt bort siden forrige vurdering`() {
        val identitetsnummer = lagIdentitetsnummer()
        val skjæringstidspunkt = LocalDate.parse("2026-01-01")
        val forrigeVurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = skjæringstidspunkt,
                identitetsnummer = identitetsnummer,
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forrigeVurdering)

        // Replikabasen er tom, altså har brukeren ingen forsikring lenger
        val resultat =
            revurderingService.revurder(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vedtaksperiodeId = UUID.randomUUID(),
                behandlingId = UUID.randomUUID(),
            ) { forrigeForsikringsvurdering ->
                """{"forrigeForsikringsvurderingId": "${forrigeForsikringsvurdering.id.value}"}"""
            }

        val endretVurdering = assertIs<Revurderingsresultat.EndretVurdering>(resultat)
        assert(endretVurdering.forsikringsvurdering.id != forrigeVurdering.id) {
            "Forventet en ny forsikringsvurdering-id"
        }
        assertTrue(
            subumsjonspubliserer.subsumsjoner.contains(resultat.forsikringsvurdering),
            "Forventet at subsumsjon ble publisert for den nye vurderingen",
        )
        assertEquals(
            listOf(
                Triple(
                    identitetsnummer.value,
                    skjæringstidspunkt,
                    endretVurdering.forsikringsvurdering.id.value,
                ),
            ),
            endretForsikringsvurderingPubliserer.publiserteMeldinger,
            "Forventet én endret_forsikringsvurdering-melding som peker på den nye vurderingen",
        )
    }

    @Test
    fun `returnerer UendretVurdering og lagrer ingenting når utfallet ikke har endret seg`() {
        val identitetsnummer = lagIdentitetsnummer()
        val skjæringstidspunkt = LocalDate.parse("2026-01-01")
        val forrigeVurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = skjæringstidspunkt,
                identitetsnummer = identitetsnummer,
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            type = IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
                            virkningsdato = LocalDate.parse("2025-06-01"),
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forrigeVurdering)

        // Replikabasen inneholder den samme forsikringen som vurderingen over bygger på
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_TYPE = '1',
            IF10_VIRKDATO = 20250601,
        )
        TestcontainersReplikadatabase.insertFkonto12(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_FORSFOM_SEQ = 0,
            IF12_BETDATO_SEQ = 1,
            IF12_BETDATO = 20250601,
        )

        val resultat =
            revurderingService.revurder(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vedtaksperiodeId = UUID.randomUUID(),
                behandlingId = UUID.randomUUID(),
            ) { error("skal ikke bygge behov når utfallet er uendret") }

        assertIs<Revurderingsresultat.UendretVurdering>(resultat)
        assert(TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger() == 1) {
            "Forventet at ingen ny forsikringsvurdering ble lagret"
        }
        assertTrue(subumsjonspubliserer.subsumsjoner.isEmpty(), "Forventet at ingen subsumsjoner ble publisert")
        assertTrue(
            endretForsikringsvurderingPubliserer.publiserteMeldinger.isEmpty(),
            "Forventet at ingen endret_forsikringsvurdering-melding ble publisert",
        )
    }
}
