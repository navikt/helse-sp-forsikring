package no.nav.helse.sykepenger.forsikring.api

import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring
import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring.Konklusjon
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import java.time.Instant
import java.time.LocalDate

/**
 * Responsen som sendes til spesialist for både GET /forsikringsvurderinger/{id} og POST /revurdering,
 * siden begge endepunktene svarer med den (eventuelt nye) forsikringsvurderingen.
 */
data class SpesialistForsikringsvurderingResponse(
    val id: String,
    val identitetsnummer: String,
    val samletDekning: Dekning?,
    val kollektivForsikring: KollektivForsikring?,
    val individuelleForsikringer: List<IndividuellForsikring>,
    val vurdertTidspunkt: Instant,
) {
    data class Dekning(
        val grad: Int,
        val fraDag: Int,
    )

    data class KollektivForsikring(
        val navn: String,
        val dekningFolketrygdlovenreferanse: Folketrygdlovenreferanse,
        val kollektivFolketrygdlovenreferanse: Folketrygdlovenreferanse,
    )

    data class IndividuellForsikring(
        val navn: String,
        val dekningFolketrygdlovenreferanse: Folketrygdlovenreferanse,
        val virkningsdato: LocalDate,
        val opphørsdato: LocalDate?,
        val konklusjon: Konklusjon,
        val lagtTilGrunn: Boolean,
    ) {
        data class Konklusjon(
            val forklaring: String,
            val folketrygdlovenreferanse: Folketrygdlovenreferanse?,
        )
    }
}

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    val ledd: Int?,
    val bokstav: Char?,
)

internal fun Forsikringsvurdering.tilSpesialistResponse(): SpesialistForsikringsvurderingResponse =
    SpesialistForsikringsvurderingResponse(
        id = id.value.toString(),
        identitetsnummer = identitetsnummer.value,
        samletDekning =
            dekning()?.let {
                SpesialistForsikringsvurderingResponse.Dekning(
                    grad = it.grad,
                    fraDag = it.fraDag,
                )
            },
        kollektivForsikring =
            kollektivForsikring?.let {
                SpesialistForsikringsvurderingResponse.KollektivForsikring(
                    navn = it.navn,
                    dekningFolketrygdlovenreferanse = it.folketrygdlovenreferanse.tilApiFolketrygdlovenReferanse(),
                    kollektivFolketrygdlovenreferanse = KollektivForsikring.KOLLEKTIV_FORSIKRING_GENERELL_FOLKETRYGDLOVENREFERANSE.tilApiFolketrygdlovenReferanse(),
                )
            },
        individuelleForsikringer =
            individuelleForsikringer.map { forsikring ->
                IndividuellForsikring(
                    navn = forsikring.type.navn,
                    dekningFolketrygdlovenreferanse =
                        forsikring.type.folketrygdlovenreferanse
                            .tilApiFolketrygdlovenReferanse(),
                    virkningsdato = forsikring.virkningsdato,
                    opphørsdato = forsikring.opphørsdato,
                    konklusjon =
                        Konklusjon(
                            forklaring = forsikring.konklusjon.forklaring(),
                            folketrygdlovenreferanse = forsikring.konklusjon.folketrygdlovenReferanse?.tilApiFolketrygdlovenReferanse(),
                        ),
                    lagtTilGrunn = forsikring.erGyldig(),
                )
            },
        vurdertTidspunkt = vurdertTidspunkt,
    )

private fun VurdertIndividuellForsikring.Konklusjon.forklaring(): String =
    when (this) {
        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO -> {
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet"
        }

        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO -> {
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet"
        }

        VurdertIndividuellForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT -> {
            "Forsikringen opphørte før skjæringstidspunktet"
        }

        VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT -> {
            "Forsikringen er innvilget, men ikke betalt ennå"
        }

        VurdertIndividuellForsikring.Konklusjon.PASSER_IKKE_MED_SØKNADSTYPE -> {
            "Forsikringen passer ikke med søknadstypen"
        }

        VurdertIndividuellForsikring.Konklusjon.GYLDIG -> {
            "Lagt til grunn"
        }
    }

private fun no.nav.helse.sykepenger.forsikring.domain.Folketrygdlovenreferanse.tilApiFolketrygdlovenReferanse(): Folketrygdlovenreferanse =
    Folketrygdlovenreferanse(
        kapittel = kapittel,
        paragrafIKapittel = paragrafIKapittel,
        ledd = ledd,
        bokstav = bokstav,
    )
