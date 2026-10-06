package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringType
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.kafka.EndretForsikringsvurderingPubliserer
import no.nav.helse.sykepenger.forsikring.kafka.InMemoryOutboxRepository
import no.nav.helse.sykepenger.forsikring.kafka.OutboxRepository
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersReplikadatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagIdentitetsnummer
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagVurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.tilInfotrygdFødselsnummer
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class EndringssjekkServiceTest {
    private val subumsjonspubliserer =
        object : Subsumsjonspubliserer {
            val subsumsjoner = mutableListOf<Forsikringsvurdering>()

            override fun publiser(
                outboxRepository: OutboxRepository,
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
                outboxRepository: OutboxRepository,
                identitetsnummer: Identitetsnummer,
                skjæringstidspunkt: LocalDate,
                forsikringsvurderingId: UUID,
            ) {
                publiserteMeldinger.add(Triple(identitetsnummer.value, skjæringstidspunkt, forsikringsvurderingId))
            }
        }

    private val endringssjekkService =
        EndringssjekkService(
            spForsikringDataSource = TestcontainersSpForsikringDatabase.dataSource,
            forsikringsvurderingService = ForsikringsvurderingService(TestcontainersReplikadatabase.dataSource),
            subsumsjonspubliserer = subumsjonspubliserer,
            endretForsikringsvurderingPubliserer = endretForsikringsvurderingPubliserer,
            outboxRepositoryFactory = { InMemoryOutboxRepository() },
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
            endringssjekkService.endringssjekk(
                identitetsnummer = lagIdentitetsnummer(),
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                saksbehandlerIdent = "Z123456",
                requestBody = """{"identitetsnummer": "ukjent"}""",
            )

        assertIs<Endringssjekkresultat.IngenTidligereVurdering>(resultat)
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
            endringssjekkService.endringssjekk(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                saksbehandlerIdent = "Z123456",
                requestBody = """{"identitetsnummer": "${identitetsnummer.value}"}""",
            )

        val endretVurdering = assertIs<Endringssjekkresultat.EndretVurdering>(resultat)
        assert(endretVurdering.forsikringsvurdering.id != forrigeVurdering.id) {
            "Forventet en ny forsikringsvurdering-id"
        }
        assertEquals(
            forrigeVurdering.vedtaksperiodeId,
            endretVurdering.forsikringsvurdering.vedtaksperiodeId,
            "Forventet at den nye vurderingen arver vedtaksperiodeId fra den forrige",
        )
        assertEquals(
            forrigeVurdering.behandlingId,
            endretVurdering.forsikringsvurdering.behandlingId,
            "Forventet at den nye vurderingen arver behandlingId fra den forrige",
        )
        assertEquals(
            forrigeVurdering.id,
            endretVurdering.forsikringsvurdering.forrigeForsikringsvurderingId,
            "Forventet at den nye vurderingen peker på den forrige vurderingen",
        )
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

    @ParameterizedTest
    @CsvSource(
        "2025-12-31, 2026-01-02, false",
        "2026-01-02, 2026-01-01, true",
    )
    fun `lagrer og publiserer ny vurdering når fom flyttes over skjæringstidspunktet`(
        forrigeFom: LocalDate,
        nyFom: LocalDate,
        erIOpptjeningstid: Boolean,
    ) {
        val identitetsnummer = lagIdentitetsnummer()
        val skjæringstidspunkt = LocalDate.parse("2026-01-01")
        val forrigeVurdering =
            lagForsikringsvurdering(
                skjæringstidspunkt = skjæringstidspunkt,
                identitetsnummer = identitetsnummer,
                individuelleForsikringer =
                    listOf(
                        lagVurdertIndividuellForsikring(
                            fom = forrigeFom,
                            virkningsdato = LocalDate.parse("2026-01-29"),
                            erBetaltNoenGang = false,
                            konklusjon = VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO,
                        ),
                    ),
            )
        lagreRåkopiOgForsikringsvurdering(forrigeVurdering)
        TestcontainersReplikadatabase.insertVedfrivt(
            IF01_AGNR_FNR = identitetsnummer.tilInfotrygdFødselsnummer(),
            IF10_FORSFOM = nyFom.format(DateTimeFormatter.BASIC_ISO_DATE).toInt(),
            IF10_VIRKDATO = 20260129,
        )

        val resultat =
            endringssjekkService.endringssjekk(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                saksbehandlerIdent = "Z123456",
                requestBody = """{"identitetsnummer": "${identitetsnummer.value}"}""",
            )

        val nyVurdering = assertIs<Endringssjekkresultat.EndretVurdering>(resultat).forsikringsvurdering
        val lagretVurdering =
            TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
                assertNotNull(ForsikringsvurderingRepository(transaction).finn(identitetsnummer, skjæringstidspunkt))
            }
        assertEquals(2, TestcontainersSpForsikringDatabase.countAlleForsikringsvurderinger())
        assertEquals(nyVurdering.id, lagretVurdering.id)
        assertEquals(forrigeVurdering.id, lagretVurdering.forrigeForsikringsvurderingId)
        assertEquals(nyFom, lagretVurdering.individuelleForsikringer.single().fom)
        assertEquals(
            forrigeVurdering.individuelleForsikringer.single().konklusjon,
            lagretVurdering.individuelleForsikringer.single().konklusjon,
        )
        assertEquals(!erIOpptjeningstid, forrigeVurdering.harIndividuellForsikringIOpptjeningstid())
        assertEquals(erIOpptjeningstid, lagretVurdering.harIndividuellForsikringIOpptjeningstid())
        assertEquals(listOf(nyVurdering), subumsjonspubliserer.subsumsjoner)
        assertEquals(
            listOf(Triple(identitetsnummer.value, skjæringstidspunkt, nyVurdering.id.value)),
            endretForsikringsvurderingPubliserer.publiserteMeldinger,
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
            endringssjekkService.endringssjekk(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                saksbehandlerIdent = "Z123456",
                requestBody = """{"identitetsnummer": "${identitetsnummer.value}"}""",
            )

        assertIs<Endringssjekkresultat.UendretVurdering>(resultat)
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
