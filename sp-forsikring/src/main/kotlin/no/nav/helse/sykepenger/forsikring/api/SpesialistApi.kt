package no.nav.helse.sykepenger.forsikring.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring
import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring.Konklusjon
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

fun Route.spesialistApi(spForsikringDataSource: DataSource) {
    get("/forsikringsvurderinger/{forsikringsvurderingId}") {
        val rawId = call.parameters["forsikringsvurderingId"]
        val id =
            rawId?.let { runCatching { Forsikringsvurdering.Id.fromString(it) }.getOrNull() }
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ProblemResponse(
                        title = "Ugyldig forsikringsvurderingId",
                        status = HttpStatusCode.BadRequest.value,
                        detail = "forsikringsvurderingId må være en gyldig UUID",
                        instance = call.request.uri,
                    ),
                )
        loggInfo("Mottok kall til GET /forsikringsvurderinger/${id.value}")

        val forsikringsvurdering =
            spForsikringDataSource.inTransaction { transactionalSession ->
                ForsikringsvurderingRepository(transactionalSession).hent(id)
            } ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ProblemResponse(
                    title = "Forsikringsvurdering ikke funnet",
                    status = HttpStatusCode.NotFound.value,
                    detail = "Fant ingen forsikringsvurdering med id ${id.value}",
                    instance = call.request.uri,
                ),
            )

        val response =
            SpesialistForsikringsvurderingResponse(
                identitetsnummer = forsikringsvurdering.identitetsnummer.value,
                samletDekning =
                    forsikringsvurdering.dekning()?.let {
                        SpesialistForsikringsvurderingResponse.Dekning(
                            grad = it.grad,
                            fraDag = it.fraDag,
                        )
                    },
                kollektivForsikring =
                    forsikringsvurdering.kollektivForsikring?.let {
                        SpesialistForsikringsvurderingResponse.KollektivForsikring(
                            navn = it.navn,
                            dekningFolketrygdlovenreferanse = it.dekning.folketrygdlovenreferanse.tilApiFolketrygdlovenReferanse(),
                            kollektivFolketrygdlovenreferanse = KollektivForsikring.KOLLEKTIV_FORSIKRING_GENERELL_FOLKETRYGDLOVENREFERANSE.tilApiFolketrygdlovenReferanse(),
                        )
                    },
                individuelleForsikringer =
                    forsikringsvurdering.individuelleForsikringer.map { forsikring ->
                        IndividuellForsikring(
                            navn = forsikring.type.navn,
                            dekningFolketrygdlovenreferanse =
                                forsikring.type.dekning.folketrygdlovenreferanse
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
                vurdertTidspunkt = forsikringsvurdering.vurdertTidspunkt,
            )

        loggInfo("Svarer på GET /forsikringsvurderinger/$id", "response" to response)

        call.respond(response)
    }
}

private fun VurdertIndividuellForsikring.Konklusjon.forklaring(): String =
    when (this) {
        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO ->
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet"

        VurdertIndividuellForsikring.Konklusjon.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO ->
            "Forsikringen var ikke ennå gyldig på skjæringstidspunktet"

        VurdertIndividuellForsikring.Konklusjon.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT ->
            "Forsikringen opphørte før skjæringstidspunktet"

        VurdertIndividuellForsikring.Konklusjon.ALDRI_BETALT ->
            "Forsikringen er innvilget, men ikke betalt ennå"

        VurdertIndividuellForsikring.Konklusjon.GYLDIG ->
            "Lagt til grunn"
    }

private fun no.nav.helse.sykepenger.forsikring.domain.Folketrygdlovenreferanse.tilApiFolketrygdlovenReferanse(): Folketrygdlovenreferanse =
    Folketrygdlovenreferanse(
        kapittel = kapittel,
        paragrafIKapittel = paragrafIKapittel,
        ledd = ledd,
        bokstav = bokstav,
    )

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    val ledd: Int?,
    val bokstav: Char?,
)

data class SpesialistForsikringsvurderingResponse(
    val identitetsnummer: String,
    val samletDekning: Dekning?,
    val kollektivForsikring: KollektivForsikring?,
    val individuelleForsikringer: List<IndividuellForsikring>,
    val vurdertTidspunkt: Instant,
) {
    @Deprecated("Nav-kjøpt forsikring heter nå individuell forsikring. Bruk individuelleForsikringer i stedet.")
    val navKjøpteForsikringer: List<IndividuellForsikring>
        get() = individuelleForsikringer

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

data class ProblemResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
)
