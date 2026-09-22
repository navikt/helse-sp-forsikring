package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.testsupport.lagreRåkopiOgForsikringsvurdering
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EndringssjekkLoggDaoTest {
    private val dataSource = TestcontainersSpForsikringDatabase.dataSource

    @BeforeEach
    fun beforeEach() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `lagrer rad i sist_hentet`() {
        val utførtAvSaksbehandlerIdent = "Z123456"
        val sistHentetTidspunkt = Instant.parse("2020-01-01T12:00:00.000Z")
        val forsikringsvurdering = lagForsikringsvurdering(skjæringstidspunkt = LocalDate.parse("2026-07-01"))
        lagreRåkopiOgForsikringsvurdering(forsikringsvurdering)
        val forsikringsvurderingId = forsikringsvurdering.id

        dataSource.inTransaction { transaction ->
            EndringssjekkLoggDao(transaction).insert(
                utførtAvSaksbehandlerIdent = utførtAvSaksbehandlerIdent,
                forsikringsvurderingId = forsikringsvurderingId,
                tidspunkt = sistHentetTidspunkt,
            )
        }
        val sistHentetRad = hentSistHentet(forsikringsvurderingId)
        assertNotNull(sistHentetRad)
        assertEquals(utførtAvSaksbehandlerIdent, sistHentetRad.utførtAvSaksbehandlerIdent)
        assertEquals(sistHentetTidspunkt, sistHentetRad.tidspunkt)
        assertEquals(forsikringsvurderingId, sistHentetRad.forsikringsvurderingId)
    }

    private fun hentSistHentet(forsikringsvurderingId: Forsikringsvurdering.Id): EndringssjekkLogg? =
        dataSource.inTransaction { transaction ->
            EndringssjekkLoggDao(transaction).hentSistHentet(forsikringsvurderingId)
        }
}
