package no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur

import java.time.LocalDate
import java.util.*
import no.nav.helse.sykepenger.forsikring.oppgaver.OppgaveClient
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak

class TestOppgaveClient : OppgaveClient {
    internal var oppgaveParams: OppgaveParams? = null
        private set

    override suspend fun lagOppgave(
        duplikatkontrollId: UUID,
        fødselsnummer: String,
        årsak: Årsak,
        skjæringstidspunkt: LocalDate
    ) {
        oppgaveParams = OppgaveParams(
            fødselsnummer = fødselsnummer,
            årsak = årsak,
            skjæringstidspunkt = skjæringstidspunkt,
            duplikatkontrollId = duplikatkontrollId
        )
    }
}

internal class OppgaveParams(
    val duplikatkontrollId: UUID,
    val fødselsnummer: String,
    val årsak: Årsak,
    val skjæringstidspunkt: LocalDate
)
