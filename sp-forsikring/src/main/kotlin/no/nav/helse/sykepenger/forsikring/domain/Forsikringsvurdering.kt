package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.time.Instant
import java.time.LocalDate
import java.util.*

class Forsikringsvurdering private constructor(
    val id: Id,
    val input: ForsikringsvurderingInput,
    val råkopiId: Råkopi.Id,
    val individuelleForsikringer: List<VurdertIndividuellForsikring>,
    val kollektivForsikring: KollektivForsikring?,
    val vurdertTidspunkt: Instant,
    val vedtaksperiodeId: UUID?,
    val behandlingId: UUID?,
    val forrigeForsikringsvurderingId: Id?,
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

    fun harIndividuellForsikringIOpptjeningstid(): Boolean = individuelleForsikringer.any { it.erIOpptjeningstidPå(input.skjæringstidspunkt) }

    fun dekning(): Forsikringsdekning? =
        listOfNotNull(
            gjeldendeIndividuellForsikring()?.type?.dekning,
            kollektivForsikring?.dekning,
        ).minByOrNull { it.fraDag }

    fun opphørsdato(): LocalDate? = gjeldendeIndividuellForsikring()?.opphørsdato

    fun harIndividuellForsikring() = gjeldendeIndividuellForsikring() != null

    fun harKollektivForsikring() = kollektivForsikring != null

    /**
     * Sammenligner utfallet av to vurderinger av samme person og skjæringstidspunkt, for å avgjøre om en ny
     * vurdering gir et annet resultat enn en tidligere lagret vurdering.
     * Forsikringens fom-dato inngår fordi den påvirker opprettelse av oppgave om premiefritak.
     *
     * Identifikatorer ([id], [råkopiId], [forrigeForsikringsvurderingId],
     * [VurdertIndividuellForsikring.råkopiIfVedfrivt10Id]) og [vurdertTidspunkt]
     * inngår ikke, siden de alltid er nye for hver vurdering. Det samme gjelder felter som ikke påvirker utfallet
     * (premiegrunnlag og betalingsstatus), som endrer seg i Infotrygd uten at vurderingen endrer seg.
     */
    fun harSammeUtfallSom(annen: Forsikringsvurdering): Boolean = utfall() == annen.utfall()

    private fun utfall(): Utfall =
        Utfall(
            harForsikring = harForsikring(),
            dekning = dekning(),
            opphørsdato = opphørsdato(),
            kollektivForsikring = kollektivForsikring,
            individuelleForsikringer =
                individuelleForsikringer
                    .map { forsikring ->
                        IndividuellForsikringUtfall(
                            type = forsikring.type,
                            fom = forsikring.fom,
                            virkningsdato = forsikring.virkningsdato,
                            opphørsdato = forsikring.opphørsdato,
                            konklusjon = forsikring.konklusjon,
                        )
                    }.toSet(),
        )

    private data class Utfall(
        val harForsikring: Boolean,
        val dekning: Forsikringsdekning?,
        val opphørsdato: LocalDate?,
        val kollektivForsikring: KollektivForsikring?,
        val individuelleForsikringer: Set<IndividuellForsikringUtfall>,
    )

    private data class IndividuellForsikringUtfall(
        val type: IndividuellForsikringType,
        val fom: LocalDate?,
        val virkningsdato: LocalDate,
        val opphørsdato: LocalDate?,
        val konklusjon: VurdertIndividuellForsikring.Konklusjon,
    )

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
            input: ForsikringsvurderingInput,
            råkopiId: Råkopi.Id,
            kollektiveForsikringer: Set<KollektivForsikring>,
            individuelleForsikringer: List<IndividuellForsikring>,
            vedtaksperiodeId: UUID?,
            behandlingId: UUID?,
            forrigeForsikringsvurderingId: Id?,
        ): Forsikringsvurdering =
            Forsikringsvurdering(
                id = Id.ny(),
                input = input,
                råkopiId = råkopiId,
                individuelleForsikringer = individuelleForsikringer.map { it.vurder(input) },
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
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                forrigeForsikringsvurderingId = forrigeForsikringsvurderingId,
            )

        fun fraLagring(
            id: Id,
            input: ForsikringsvurderingInput,
            råkopiId: Råkopi.Id,
            individuelleForsikringer: List<VurdertIndividuellForsikring>,
            kollektivForsikring: KollektivForsikring?,
            vurdertTidspunkt: Instant,
            vedtaksperiodeId: UUID,
            behandlingId: UUID,
            forrigeForsikringsvurderingId: Id?,
        ) = Forsikringsvurdering(
            id = id,
            input = input,
            råkopiId = råkopiId,
            individuelleForsikringer = individuelleForsikringer,
            kollektivForsikring = kollektivForsikring,
            vurdertTidspunkt = vurdertTidspunkt,
            vedtaksperiodeId = vedtaksperiodeId,
            behandlingId = behandlingId,
            forrigeForsikringsvurderingId = forrigeForsikringsvurderingId,
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
