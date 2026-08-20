package no.nav.helse.sykepenger.forsikring.kafka.lib

import no.nav.helse.sykepenger.forsikring.kafka.lib.LenientEnum.Known
import no.nav.helse.sykepenger.forsikring.kafka.lib.LenientEnum.Unknown
import tools.jackson.core.JsonParser
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(using = LenientEnumDeserializer::class)
sealed interface LenientEnum<E : Enum<E>> {
    data class Known<E : Enum<E>>(
        val e: E,
    ) : LenientEnum<E> {
        override fun isKnown(e: E) = this.e == e
    }

    data class Unknown<E : Enum<E>>(
        val value: String,
        val enumClass: Class<E>,
    ) : LenientEnum<E> {
        override fun isKnown(e: E) = false
    }

    fun isKnown(e: E): Boolean
}

class LenientEnumDeserializer<E : Enum<E>>(
    private val enumKlasse: Class<E>?,
) : ValueDeserializer<LenientEnum<E>>() {
    constructor() : this(null)

    @Suppress("UNCHECKED_CAST")
    override fun createContextual(
        ctxt: DeserializationContext,
        property: BeanProperty?,
    ): ValueDeserializer<*> {
        val type = property?.type ?: ctxt.contextualType ?: return this
        val enumType = type.containedTypeOrUnknown(0).rawClass
        if (!Enum::class.java.isAssignableFrom(enumType)) return this
        return LenientEnumDeserializer(enumType as Class<E>)
    }

    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): LenientEnum<E> {
        val verdi = p.string
        val klasse =
            checkNotNull(enumKlasse) {
                "Klarte ikke utlede enum-typen for LenientEnum. Er den deklarert med en konkret typeparameter?"
            }
        val konstant = klasse.enumConstants.firstOrNull { it.name.equals(verdi, ignoreCase = true) }
        return if (konstant == null) Unknown(verdi, klasse) else Known(konstant)
    }
}
