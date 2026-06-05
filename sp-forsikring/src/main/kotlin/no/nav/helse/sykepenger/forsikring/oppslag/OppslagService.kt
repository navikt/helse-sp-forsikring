package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.Instant
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.replikabase.IF_VEDFRIVT_10_Rad

class OppslagService(spForsikringTransaction: TransactionalSession) {
    private val oppslagDao = OppslagDao(spForsikringTransaction)

    fun gjørNyttOppslag(rader: List<IF_VEDFRIVT_10_Rad>): Oppslag {
        val oppslagId = OppslagId.ny()
        oppslagDao.lagreOppslag(oppslagId, Instant.now())
        oppslagDao.lagreIfVedfrivt10Rader(oppslagId, rader)
        return oppslagDao.hentOppslag(oppslagId)
    }
}
