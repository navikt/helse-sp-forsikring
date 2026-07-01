package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.Instant
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao

class OppslagService(
    private val replikabaseDao: ReplikabaseDao,
) {
    private val oppslagDao = OppslagDao()

    fun gjørNyttOppslag(session: TransactionalSession, fødselsnummer: String): Oppslag {
        val oppslagId = OppslagId.ny()
        oppslagDao.lagreOppslag(oppslagId, Instant.now(), session)
        val vedfrivt10Rader = replikabaseDao.hentIfVedfrivt10Rader(fødselsnummer)
        oppslagDao.lagreIfVedfrivt10Rader(oppslagId, vedfrivt10Rader, session)

        return oppslagDao.hentOppslag(oppslagId, session)
    }
}
