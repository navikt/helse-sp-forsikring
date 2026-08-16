package no.nav.helse.sykepenger.forsikring.gosys

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class OpprettOppgaveRequest(
    val personident: String,
    val uuid: String,
    val aktivDato: LocalDate,
    val prioritet: Prioritet,
    val oppgavetype: String,
    val tema: String,
    val behandlingstype: String,
    val beskrivelse: String,
)
