package no.nav.helse.sykepenger.forsikring.oppgaver

import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import java.time.LocalDate
import java.util.*

interface OppgaveClient {
    suspend fun lagOppgave(
        duplikatkontrollId: UUID,
        fødselsnummer: String,
        årsak: Årsak,
        skjæringstidspunkt: LocalDate,
    )
}
