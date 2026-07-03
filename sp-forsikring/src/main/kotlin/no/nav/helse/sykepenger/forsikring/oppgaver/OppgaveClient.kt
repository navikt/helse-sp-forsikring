package no.nav.helse.sykepenger.forsikring.oppgaver.seam

import java.time.LocalDate
import java.util.*
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak

interface OppgaveClient {
    suspend fun lagOppgave(duplikatkontrollId: UUID, fødselsnummer: String, årsak: Årsak, skjæringstidspunkt: LocalDate)
}
