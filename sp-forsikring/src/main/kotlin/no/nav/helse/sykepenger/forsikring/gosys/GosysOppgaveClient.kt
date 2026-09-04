package no.nav.helse.sykepenger.forsikring.gosys

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retry
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.UUID.randomUUID

class GosysOppgaveClient(
    private val baseUrl: String,
    private val tokenClient: AzureTokenProvider,
    private val httpClient: HttpClient,
    private val gosysScope: String,
) {
    suspend fun lagOppgave(
        duplikatkontrollId: UUID,
        fødselsnummer: String,
        årsak: Årsak,
        skjæringstidspunkt: LocalDate,
    ) {
        val årsakTekst =
            when (årsak) {
                Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent -> "Det er utbetalt sykepenger fra dag én og vedkommende har 80% dekningsgrad"
                Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød -> "Sykepengerett har opphørt som følge av ingen gjenstående dager"
                is Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag -> "For stort avvik mellom sykepengegrunnlag, ${årsak.sykepengegrunnlag.setScale(2)}, og premiegrunnlag, ${årsak.premiegrunnlag.setScale(2)}. Avviket er ${årsak.avviksprosent.setScale(2)}"
                Årsak.UtbetaltFraDagÉnOgDekningsgrad100ProsentJordbruker -> "Det er utbetalt sykepenger for en Jordbruker fra dag en og vedkommende har 100% dekningsgrad"
            }
        retry {
            loggInfo("Forsøker å opprette oppgave i Gosys.")
            val response =
                httpClient.post("$baseUrl/api/v1/oppgaver") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    val bearerToken = tokenClient.bearerToken(gosysScope).getOrThrow()
                    bearerAuth(bearerToken.token)
                    setBody(
                        OpprettOppgaveRequest(
                            personident = fødselsnummer,
                            uuid = duplikatkontrollId.toString(),
                            aktivDato = LocalDate.now(),
                            prioritet = Prioritet.NORM,
                            oppgavetype = "VURD_HENV",
                            tema = "FOS",
                            behandlingstype = "ae0221",
                            beskrivelse = "Årsak: $årsakTekst. Skjæringstidspunkt: ${skjæringstidspunkt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.",
                        ),
                    )
                    header("X-Correlation-ID", randomUUID().toString())
                }

            if (response.status !in listOf(HttpStatusCode.Created, HttpStatusCode.Conflict)) {
                loggError("Fikk status code ${response.status} ved oppretting av oppgave.", "Response body" to response.bodyAsText())
                throw IllegalStateException("Feil ved forsøk på å opprette oppgave")
            }

            loggInfo("Har opprettet oppgave i Gosys med respons status: ${response.status}")
        }
    }
}
