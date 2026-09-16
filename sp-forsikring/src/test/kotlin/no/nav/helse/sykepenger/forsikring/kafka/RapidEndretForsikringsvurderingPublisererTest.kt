package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class RapidEndretForsikringsvurderingPublisererTest {
    private val rapid = TestRapid()
    private val publiserer = RapidEndretForsikringsvurderingPubliserer(rapid)

    @BeforeEach
    fun beforeEach() {
        rapid.reset()
    }

    @Test
    fun `publiserer melding med forventet innhold`() {
        val forsikringsvurderingId = UUID.randomUUID()

        publiserer.publiser(
            identitetsnummer = Identitetsnummer("01020312345"),
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            forsikringsvurderingId = forsikringsvurderingId,
        )

        assertEquals(1, rapid.inspektør.size)
        val melding = rapid.inspektør.message(0)
        assertEquals("endret_forsikringsvurdering", melding["@event_name"].asString())
        assertEquals("01020312345", melding["identitetsnummer"].asString())
        assertEquals("2026-01-01", melding["skjæringstidspunkt"].asString())
        assertEquals(forsikringsvurderingId.toString(), melding["forsikringsvurderingId"].asString())
    }

    @Test
    fun `melding har rapids and rivers-metadata`() {
        publiserer.publiser(
            identitetsnummer = Identitetsnummer("01020312345"),
            skjæringstidspunkt = LocalDate.parse("2026-01-01"),
            forsikringsvurderingId = UUID.randomUUID(),
        )

        val melding = rapid.inspektør.message(0)
        assertNotNull(UUID.fromString(melding["@id"].asString()))
        assertNotNull(melding["@opprettet"].asString())
        assertNotNull(melding["@opprettetUTC"].asString())
    }

    @Test
    fun `hver melding får sin egen id`() {
        repeat(2) {
            publiserer.publiser(
                identitetsnummer = Identitetsnummer("01020312345"),
                skjæringstidspunkt = LocalDate.parse("2026-01-01"),
                forsikringsvurderingId = UUID.randomUUID(),
            )
        }

        assertEquals(2, rapid.inspektør.size)
        assertEquals(
            2,
            (0 until rapid.inspektør.size).map { rapid.inspektør.message(it)["@id"].asString() }.toSet().size,
        )
    }
}
