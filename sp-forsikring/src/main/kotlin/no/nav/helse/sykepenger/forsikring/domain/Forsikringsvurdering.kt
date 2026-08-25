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
    val individuelleForsikringer: List<VurdertIndividuellForsikring>,
    val kollektivForsikring: KollektivForsikring?,
    val vurdertTidspunkt: Instant,
) {
    init {
        val gyldigeIndividuelleForsikringer = individuelleForsikringer.filter { it.erGyldig() }

        // Støtter ikke overlappende individuelle forsikringer
        if (gyldigeIndividuelleForsikringer.size > 1) {
            error(
                "Fant flere individuelle forsikringer som var gyldige for skjæringstidspunktet." +
                    " Kan ikke fortsette med dette, siden det er tvetydig hvilken forsikring som bidrar" +
                    " til økt utbetaling (med tanke på senere justering av premiesats)",
            )
        }
        val gjeldendeIndividuellForsikring = gyldigeIndividuelleForsikringer.firstOrNull()

        // Støtter ikke hull mellom dekningen til tilleggsforsikringen og den kollektive forsikringen
        if (kollektivForsikring != null &&
            gjeldendeIndividuellForsikring != null &&
            gjeldendeIndividuellForsikring.type.dekning.fraDag < kollektivForsikring.dekning.fraDag &&
            gjeldendeIndividuellForsikring.opphørsdato != null &&
            gjeldendeIndividuellForsikring.opphørsdato.isBefore(
                gjeldendeIndividuellForsikring.virkningsdato
                    .plusDays(kollektivForsikring.dekning.fraDag.toLong())
                    .minusDays(2),
            )
        ) {
            error(
                "Tilleggsforsikringen opphører i ventetiden." +
                    " Slike hull i dekningen av tilleggsforsikring og kollektiv forsikring støttes ikke av Spleis per nå.",
            )
        }
    }

    fun harForsikring(): Boolean = individuelleForsikringer.any { it.erGyldig() } || kollektivForsikring != null

    fun villeHattForsikringOmDenVarBetalt(): Boolean = individuelleForsikringer.any { it.konklusjon == VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT }

    fun harForsikringSomIkkePasserMedSøknadstype(): Boolean = individuelleForsikringer.any { it.passerIkkeMedSøknadstype() }

    fun gjeldendeIndividuellForsikring(): VurdertIndividuellForsikring? = individuelleForsikringer.singleOrNull { it.erGyldig() }

    fun dekning(): Forsikringsdekning? =
        listOfNotNull(
            gjeldendeIndividuellForsikring()?.type?.dekning,
            kollektivForsikring?.dekning,
        ).minByOrNull { it.fraDag }

    fun opphørsdato(): LocalDate? = gjeldendeIndividuellForsikring()?.opphørsdato

    fun harIndividuellForsikring() = gjeldendeIndividuellForsikring() != null

    fun harKollektivForsikring() = kollektivForsikring != null

    fun harDekningIVentetidUavhengigAvBetaling(): Boolean =
        kollektivForsikring?.dekning?.fraDag == 1 ||
            individuelleForsikringer
                .filter {
                    it.erGyldig() ||
                        it.konklusjon == VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT ||
                        it.passerIkkeMedSøknadstype()
                }.any { it.type.dekning.fraDag == 1 }

    companion object {
        fun utførVurdering(
            identitetsnummer: Identitetsnummer,
            yrkesaktivitetstype: Yrkesaktivitetstype,
            spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
            skjæringstidspunkt: LocalDate,
            råkopiId: Råkopi.Id,
            kollektiveForsikringer: Set<KollektivForsikring>,
            individuelleForsikringer: List<IndividuellForsikring>,
        ): Forsikringsvurdering =
            Forsikringsvurdering(
                id = Id.ny(),
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                spesielleYrkesgrupper = spesielleYrkesgrupper,
                skjæringstidspunkt = skjæringstidspunkt,
                råkopiId = råkopiId,
                individuelleForsikringer =
                    individuelleForsikringer.map {
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
            individuelleForsikringer: List<VurdertIndividuellForsikring>,
            kollektivForsikring: KollektivForsikring?,
            vurdertTidspunkt: Instant,
        ) = Forsikringsvurdering(
            id = id,
            identitetsnummer = identitetsnummer,
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            skjæringstidspunkt = skjæringstidspunkt,
            råkopiId = råkopiId,
            individuelleForsikringer = individuelleForsikringer,
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
