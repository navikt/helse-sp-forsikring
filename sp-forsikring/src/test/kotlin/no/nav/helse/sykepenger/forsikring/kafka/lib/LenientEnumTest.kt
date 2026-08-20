package no.nav.helse.sykepenger.forsikring.kafka.lib

import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LenientEnumTest {
    enum class Farge { RØD, BLÅ }

    enum class Størrelse { LITEN, STOR }

    data class Holder(
        val farge: LenientEnum<Farge>,
    )

    private val bareMapper = jacksonMapperBuilder().build()

    @Test
    fun `deserialiserer uten noen modulregistrering`() {
        assertEquals(
            Holder(LenientEnum.Known(Farge.RØD)),
            bareMapper.readValue<Holder>("""{"farge":"RØD"}"""),
        )
        assertEquals(
            Holder(LenientEnum.Unknown("GRØNN", Farge::class.java)),
            bareMapper.readValue<Holder>("""{"farge":"GRØNN"}"""),
        )
    }

    @Test
    fun `deserialiserer som rot-type`() {
        assertEquals(
            LenientEnum.Known(Farge.BLÅ),
            bareMapper.readValue<LenientEnum<Farge>>(""""BLÅ""""),
        )
        assertEquals(
            LenientEnum.Unknown("LILLA", Farge::class.java),
            bareMapper.readValue<LenientEnum<Farge>>(""""LILLA""""),
        )
    }

    @Test
    fun `ukjent verdi av ulik enum-type er ikke lik`() {
        assertNotEquals<LenientEnum<*>>(
            LenientEnum.Unknown("LILLA", Farge::class.java),
            LenientEnum.Unknown("LILLA", Størrelse::class.java),
        )
    }
}
