package no.nav.helse.sykepenger.forsikring.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring
import no.nav.helse.sykepenger.forsikring.api.SpesialistForsikringsvurderingResponse.IndividuellForsikring.Konklusjon
import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.VurdertIndividuellForsikring
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.RevurderingService
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Revurderingsresultat
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

fun Route.spesialistApi(
    spForsikringDataSource: DataSource,
    forsikringsvurderingService: ForsikringsvurderingService,
) {
    val revurderingService = RevurderingService(spForsikringDataSource, forsikringsvurderingService)

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

        val response = forsikringsvurdering.tilSpesialistResponse()

        loggInfo("Svarer på GET /forsikringsvurderinger/$id", "response" to response.toString())

        call.respond(response)
    }

    post("/revurdering") {
        val request = call.receive<RevurderingRequest>()
        loggInfo(
            "Mottok kall til POST /revurdering",
            "identitetsnummer" to request.identitetsnummer,
            "skjæringstidspunkt" to request.skjæringstidspunkt.toString(),
        )

        val identitetsnummer = Identitetsnummer.fraString(request.identitetsnummer)

        val revurderingsresultat =
            revurderingService.revurder(
                identitetsnummer = identitetsnummer,
                skjæringstidspunkt = request.skjæringstidspunkt,
            ) { forrigeForsikringsvurdering -> request.tilBehovJson(forrigeForsikringsvurdering) }

        when (revurderingsresultat) {
            is Revurderingsresultat.IngenTidligereVurdering -> {
                call.respond(
                    HttpStatusCode.NotFound,
                    ProblemResponse(
                        title = "Forsikringsvurderinger ikke funnet",
                        status = HttpStatusCode.NotFound.value,
                        detail = "Fant ingen forsikringsvurderinger for skjæringstidspunkt ${request.skjæringstidspunkt}",
                        instance = call.request.uri,
                    ),
                )
            }

            is Revurderingsresultat.UendretVurdering -> {
                loggInfo("Svarer på POST /revurdering ingen ny vurdering")
                call.respond(HttpStatusCode.OK)
            }

            is Revurderingsresultat.EndretVurdering -> {
                val response = revurderingsresultat.forsikringsvurdering.tilSpesialistResponse()
                loggInfo("Svarer på POST /revurdering med ny vurdering", "response" to response.toString())
                call.respond(response)
            }
        }
    }
}

private fun Forsikringsvurdering.tilSpesialistResponse(): SpesialistForsikringsvurderingResponse =
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

/**
 * Forsikringsvurderinger som kommer fra Kafka lagrer behovmeldingen som utløste vurderingen. For revurderinger
 * utløst av dette API-et lagrer vi forespørselen på samme form, slik at grunnlaget for vurderingen er sporbart.
 */
private fun RevurderingRequest.tilBehovJson(forrigeVurdering: Forsikringsvurdering): String =
    behovJsonMapper.writeValueAsString(
        RevurderingBehov(
            fødselsnummer = identitetsnummer,
            yrkesaktivitetstype = forrigeVurdering.yrkesaktivitetstype.name,
            forrigeForsikringsvurderingId = forrigeVurdering.id.value.toString(),
            forsikringsvurdering =
                RevurderingBehov.Vurderingsgrunnlag(
                    spesielleYrkesgrupper = forrigeVurdering.spesielleYrkesgrupper.map { it.name },
                    skjæringstidspunkt = skjæringstidspunkt.toString(),
                ),
        ),
    )

private val behovJsonMapper = ObjectMapper()

@JsonPropertyOrder("kilde", "fødselsnummer", "yrkesaktivitetstype", "forrigeForsikringsvurderingId", "Forsikringsvurdering")
private data class RevurderingBehov(
    val kilde: String = "POST /revurdering",
    val fødselsnummer: String,
    val yrkesaktivitetstype: String,
    val forrigeForsikringsvurderingId: String,
    @JsonProperty("Forsikringsvurdering") val forsikringsvurdering: Vurderingsgrunnlag,
) {
    data class Vurderingsgrunnlag(
        val spesielleYrkesgrupper: List<String>,
        val skjæringstidspunkt: String,
    )
}

data class RevurderingRequest(
    val identitetsnummer: String,
    val skjæringstidspunkt: LocalDate,
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

data class Folketrygdlovenreferanse(
    val kapittel: Int,
    val paragrafIKapittel: Int,
    val ledd: Int?,
    val bokstav: Char?,
)

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

data class ProblemResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
)
