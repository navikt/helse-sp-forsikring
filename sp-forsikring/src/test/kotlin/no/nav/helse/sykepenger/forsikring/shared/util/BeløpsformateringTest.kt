package no.nav.helse.sykepenger.forsikring.shared.util

import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import kotlin.test.Test

class BeløpsformateringTest {
    @Test
    fun `viser ingen desimaler når beløpet er i hele kroner`() {
        assertEquals("500 000", BigDecimal("500000").somBeløpstekst())
        assertEquals("500 000", BigDecimal("500000.00").somBeløpstekst())
        assertEquals("0", BigDecimal.ZERO.somBeløpstekst())
    }

    @Test
    fun `viser to desimaler når beløpet har øre`() {
        assertEquals("500 000,50", BigDecimal("500000.5").somBeløpstekst())
        assertEquals("100,01", BigDecimal("100.011").somBeløpstekst())
    }

    @Test
    fun `runder av til nærmeste øre`() {
        assertEquals("100", BigDecimal("99.999").somBeløpstekst())
        assertEquals("-100,50", BigDecimal("-100.495").somBeløpstekst())
    }
}
