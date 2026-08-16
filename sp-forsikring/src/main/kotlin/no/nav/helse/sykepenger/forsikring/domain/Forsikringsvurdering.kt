package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.time.Instant
import java.time.LocalDate
import java.util.*

class Forsikringsvurdering private constructor(
    val id: Id,
    val identitetsnummer: Identitetsnummer,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    val skjæringstidspunkt: LocalDate,
    val råkopiId: Råkopi.Id,
    val navKjøpteForsikringer: List<VurdertNavKjøptForsikring>,
    val kollektivForsikring: KollektivForsikring?,
    val vurdertTidspunkt: Instant,
) {
    init {
        val gyldigeNavKjøpteForsikringer = navKjøpteForsikringer.filter { it.erGyldig() }

        // Støtter ikke overlappende nav-kjøpte forsikringer
        if (gyldigeNavKjøpteForsikringer.size > 1) {
            error(
                "Fant flere nav-kjøpte forsikringer som var gyldige for skjæringstidspunktet." +
                    " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                    " til økt utbetaling (med tanke på senere justering av premiesats)",
            )
        }
        val gjeldendeNavKjøptForsikring = gyldigeNavKjøpteForsikringer.firstOrNull()

        // Det er bare mulig å ha både kollektiv og Nav-kjøpt forsikring
        // dersom den Nav-kjøpte er en tilleggsforsikring for den kollektive
        if (kollektivForsikring != null && gjeldendeNavKjøptForsikring != null) {
            if (gjeldendeNavKjøptForsikring.type.tilleggsforsikringFor == kollektivForsikring) {
                if (gjeldendeNavKjøptForsikring.type.dekning.fraDag < kollektivForsikring.dekning.fraDag &&
                    gjeldendeNavKjøptForsikring.opphørsdato != null &&
                    gjeldendeNavKjøptForsikring.opphørsdato.isBefore(
                        gjeldendeNavKjøptForsikring.virkningsdato
                            .plusDays(kollektivForsikring.dekning.fraDag.toLong())
                            .minusDays(2),
                    )
                ) {
                    error(
                        "Tilleggsforsikringen opphører i ventetiden." +
                            " Slike hull i dekningen av tilleggsforsikring og kollektiv forsikring støttes ikke av Spleis per nå.",
                    )
                }
            } else {
                error(
                    "Bruker har en ugyldig kombinasjon av kollektiv og nav-kjøpt forsikring." +
                        " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                        " til økt utbetaling (med tanke på senere justering av premiesats)",
                )
            }
        }
    }

    fun harForsikring(): Boolean = navKjøpteForsikringer.any { it.erGyldig() } || kollektivForsikring != null

    fun villeHattForsikringOmDenVarBetalt(): Boolean = navKjøpteForsikringer.any { it.konklusjon == VurdertNavKjøptForsikring.Konklusjon.ALDRI_BETALT }

    fun gjeldendeNavKjøptForsikring(): VurdertNavKjøptForsikring? = navKjøpteForsikringer.singleOrNull { it.erGyldig() }

    fun dekning(): Forsikringsdekning? =
        listOfNotNull(
            gjeldendeNavKjøptForsikring()?.type?.dekning,
            kollektivForsikring?.dekning,
        ).minByOrNull { it.fraDag }

    fun opphørsdato(): LocalDate? = gjeldendeNavKjøptForsikring()?.opphørsdato

    fun harNavKjøptForsikring() = gjeldendeNavKjøptForsikring() != null

    fun harKollektivForsikring() = kollektivForsikring != null

    fun harDekningIVentetidUavhengigAvBetaling(): Boolean =
        kollektivForsikring?.dekning?.fraDag == 1 ||
            navKjøpteForsikringer
                .filter { it.erGyldig() || it.konklusjon == VurdertNavKjøptForsikring.Konklusjon.ALDRI_BETALT }
                .any { it.type.dekning.fraDag == 1 }

    companion object {
        fun utførVurdering(
            identitetsnummer: Identitetsnummer,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            skjæringstidspunkt: LocalDate,
            råkopiId: Råkopi.Id,
            kollektiveForsikringer: Set<KollektivForsikring>,
            navKjøpteForsikringer: List<NavKjøptForsikring>,
        ): Forsikringsvurdering =
            Forsikringsvurdering(
                id = Id.ny(),
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                spesielleYrkesgrupper = spesielleYrkesgrupper,
                skjæringstidspunkt = skjæringstidspunkt,
                råkopiId = råkopiId,
                navKjøpteForsikringer =
                    navKjøpteForsikringer.map {
                        it.vurder(
                            skjæringstidspunkt = skjæringstidspunkt,
                            yrkesaktivitetstype = yrkesaktivitetstype,
                            spesielleYrkesgrupper = spesielleYrkesgrupper,
                        )
                    },
                kollektivForsikring =
                    kollektiveForsikringer
                        .also {
                            if (it.size > 1) {
                                error(
                                    "Utledet mer enn én gjeldende kollektiv forsikring for bruker." +
                                        " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                                        " til økt utbetaling (med tanke på senere justering av premiesats)",
                                )
                            }
                        }.firstOrNull(),
                vurdertTidspunkt = Instant.now(),
            )

        fun fraLagring(
            id: Id,
            identitetsnummer: Identitetsnummer,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            skjæringstidspunkt: LocalDate,
            råkopiId: Råkopi.Id,
            navKjøpteForsikringer: List<VurdertNavKjøptForsikring>,
            kollektivForsikring: KollektivForsikring?,
            vurdertTidspunkt: Instant,
        ) = Forsikringsvurdering(
            id = id,
            identitetsnummer = identitetsnummer,
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            skjæringstidspunkt = skjæringstidspunkt,
            råkopiId = råkopiId,
            navKjøpteForsikringer = navKjøpteForsikringer,
            kollektivForsikring = kollektivForsikring,
            vurdertTidspunkt = vurdertTidspunkt,
        )
    }

    @JvmInline
    value class Id(
        val value: UUID,
    ) {
        companion object {
            fun ny() = Id(generateUuidV7())

            fun fromString(id: String) = Id(UUID.fromString(id))
        }
    }
}
