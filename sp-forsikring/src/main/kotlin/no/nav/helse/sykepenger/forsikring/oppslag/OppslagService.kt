package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.Instant
import javax.sql.DataSource
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao

class OppslagService(
    spForsikringTransaction: TransactionalSession,
    private val replikabaseDao: ReplikabaseDao,
) {
    private val oppslagDao = OppslagDao(spForsikringTransaction)

    fun gjørNyttOppslag(fødselsnummer: String): Oppslag {
        val oppslagId = OppslagId.ny()
        oppslagDao.lagreOppslag(oppslagId, Instant.now())
        val vedfrivt10Rader = replikabaseDao.hentIfVedfrivt10Rader(fødselsnummer)
        oppslagDao.lagreIfVedfrivt10Rader(oppslagId, vedfrivt10Rader)

        return oppslagDao.hentOppslag(oppslagId)
    }
}
