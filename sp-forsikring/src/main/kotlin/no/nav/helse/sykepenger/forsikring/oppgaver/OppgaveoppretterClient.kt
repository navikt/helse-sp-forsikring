package no.nav.helse.sykepenger.forsikring.oppgaver

import java.time.LocalDate
import java.util.*

interface OppgaveoppretterClient {
    suspend fun lagOppgave(duplikatkontrollId: UUID, fødselsnummer: String, årsak: Årsak, skjæringstidspunkt: LocalDate)
}
