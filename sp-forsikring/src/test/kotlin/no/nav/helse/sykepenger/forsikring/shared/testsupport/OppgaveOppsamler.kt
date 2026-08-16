package no.nav.helse.sykepenger.forsikring.shared.testsupport

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.sykepenger.forsikring.gosys.GosysOppgaveClient
import no.nav.helse.sykepenger.forsikring.gosys.Årsak
import java.time.LocalDate
import java.util.UUID

/**
 * Fanger opp oppgavene rivere ber om, slik at testene kan sjekke hva som ville blitt sendt til Gosys.
 */
class OppgaveOppsamler {
    private val opprettedeOppgaver = mutableListOf<Oppgave>()

    val client: GosysOppgaveClient =
        mockk<GosysOppgaveClient> {
            coEvery { lagOppgave(any(), any(), any(), any()) } answers {
                opprettedeOppgaver.add(
                    Oppgave(
                        duplikatkontrollId = firstArg(),
                        fødselsnummer = secondArg(),
                        årsak = thirdArg(),
                        skjæringstidspunkt = arg(3),
                    ),
                )
            }
        }

    val oppgaver: List<Oppgave> get() = opprettedeOppgaver.toList()

    val sisteOppgave: Oppgave? get() = opprettedeOppgaver.lastOrNull()

    data class Oppgave(
        val duplikatkontrollId: UUID,
        val fødselsnummer: String,
        val årsak: Årsak,
        val skjæringstidspunkt: LocalDate,
    )
}
