package no.nav.helse.sykepenger.forsikring.oppslag

import java.time.Instant
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao

class OppslagService(
    private val spForsikringTransaction: TransactionalSession,
    replikabaseDataSource: DataSource
) {
    private val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)

    @OptIn(ExperimentalUuidApi::class)
    fun gjørNyttOppslag(fødselsnummer: String, behovJson: String): Oppslag {
        val oppslagId = Uuid.generateV7().toJavaUuid()
        val oppslagDao = OppslagDao(spForsikringTransaction)
        oppslagDao.lagreOppslag(oppslagId, behovJson, Instant.now())
        val vedfrivt10Rader = replikabaseDao.hentIfVedfrivt10Rader(fødselsnummer)
        oppslagDao.lagreIfVedfrivt10Rader(oppslagId, vedfrivt10Rader)

        return oppslagDao.hentOppslag(oppslagId)
    }
}
