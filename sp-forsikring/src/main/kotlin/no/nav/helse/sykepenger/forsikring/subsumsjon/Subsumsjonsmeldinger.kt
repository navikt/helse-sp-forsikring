package no.nav.helse.sykepenger.forsikring.subsumsjon

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.sykepenger.forsikring.domain.Folketrygdlovenreferanse
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

fun Forsikringsvurdering.tilSubsumsjonsmeldinger(
    vedtaksperiodeId: UUID,
    behandlingId: UUID,
    versjonAvKode: String,
): List<Subsumsjonsmelding> =
    listOfNotNull(
        KollektivForsikring.KOLLEKTIV_FORSIKRING_GENERELL_FOLKETRYGDLOVENREFERANSE
            .takeIf { harKollektivForsikring() },
        kollektivForsikring
            ?.folketrygdlovenreferanse
            ?.takeUnless { harIndividuellForsikring() },
        gjeldendeIndividuellForsikring()?.type?.folketrygdlovenreferanse,
    ).map {
        it.tilSubsumsjonsmelding(
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            versjonAvKode = versjonAvKode,
            forsikringsvurdering = this,
        )
    }

private fun Folketrygdlovenreferanse.tilSubsumsjonsmelding(
    vedtaksperiodeId: UUID,
    behandlingId: UUID,
    versjonAvKode: String,
    forsikringsvurdering: Forsikringsvurdering,
): Subsumsjonsmelding =
    Subsumsjonsmelding(
        fødselsnummer = forsikringsvurdering.identitetsnummer.value,
        versjonAvKode = versjonAvKode,
        vedtaksperiodeId = vedtaksperiodeId,
        behandlingId = behandlingId,
        lovverk = "folketrygdloven",
        lovverksversjon = versjon,
        paragraf = "$kapittel-$paragrafIKapittel",
        ledd = ledd,
        punktum = punktum,
        bokstav = bokstav,
        input =
            mapOf(
                "skjæringstidspunkt" to forsikringsvurdering.skjæringstidspunkt,
                "yrkesaktivitetstype" to forsikringsvurdering.yrkesaktivitetstype,
                "spesielleYrkesgrupper" to
                    forsikringsvurdering.spesielleYrkesgrupper.map(
                        SpesiellYrkesgruppe::name,
                    ),
            ),
        output =
            mapOf(
                "forsikringsvurderingId" to forsikringsvurdering.id.value,
            ),
        utfall = "VILKAR_BEREGNET",
    )

class Subsumsjonsmelding(
    val fødselsnummer: String,
    versjonAvKode: String,
    vedtaksperiodeId: UUID,
    behandlingId: UUID,
    lovverk: String,
    lovverksversjon: LocalDate,
    paragraf: String,
    ledd: Int?,
    punktum: Int?,
    bokstav: Char?,
    input: Map<String, Any>,
    output: Map<String, Any>,
    utfall: String,
) {
    @JsonProperty("@event_name")
    val event_name: String = "subsumsjon"

    @JsonProperty("@id")
    val id: String = UUID.randomUUID().toString()

    @JsonProperty("@opprettetUTC")
    val opprettetUTC: Instant = Instant.now()

    @JsonProperty("@opprettet")
    val opprettet: LocalDateTime = opprettetUTC.atZone(ZoneId.of("Europe/Oslo")).toLocalDateTime()

    val subsumsjon: Subsumsjon =
        Subsumsjon(
            id = id,
            tidsstempel = opprettet,
            versjonAvKode = versjonAvKode,
            fodselsnummer = fødselsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            lovverk = lovverk,
            lovverksversjon = lovverksversjon,
            paragraf = paragraf,
            ledd = ledd,
            punktum = punktum,
            bokstav = bokstav,
            input = input,
            output = output,
            utfall = utfall,
        )

    class Subsumsjon(
        val id: String,
        val tidsstempel: LocalDateTime,
        val versjonAvKode: String,
        val fodselsnummer: String,
        val vedtaksperiodeId: UUID,
        val behandlingId: UUID,
        val lovverk: String,
        val lovverksversjon: LocalDate,
        val paragraf: String,
        val ledd: Int?,
        val punktum: Int?,
        val bokstav: Char?,
        val input: Map<String, Any>,
        val output: Map<String, Any>,
        val utfall: String,
    ) {
        val eventName: String = "subsumsjon"
        val versjon: String = "1.1.0"
        val kilde: String = "sp-forsikring"
        val sporing: Sporing =
            Sporing(
                vedtaksperiode = listOf(vedtaksperiodeId),
            )

        class Sporing(
            val vedtaksperiode: List<UUID>,
        )
    }

    fun tilJson(): String = objectMapper.writeValueAsString(this)

    companion object {
        private val objectMapper =
            jacksonMapperBuilder()
                .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
                .build()
    }
}
