package no.nav.helse.sykepenger.forsikring.oppslag.application

import java.time.Instant
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.oppslag.adapter.oracle.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.oppslag.adapter.postgres.OppslagDao
import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId

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
