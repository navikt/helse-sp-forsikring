package no.nav.helse.sykepenger.forsikring.oppgaver.adapter.rapids

import java.time.LocalDate
import java.util.*
import no.nav.helse.sykepenger.forsikring.oppgaver.domain.Årsak
import no.nav.helse.sykepenger.forsikring.oppgaver.seam.OppgaveoppretterClient

class TestOppgaveClient : OppgaveoppretterClient {
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
