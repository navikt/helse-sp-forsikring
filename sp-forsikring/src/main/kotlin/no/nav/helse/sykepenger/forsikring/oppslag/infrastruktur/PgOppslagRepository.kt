package no.nav.helse.sykepenger.forsikring.oppslag.infrastruktur

import no.nav.helse.sykepenger.forsikring.oppslag.OppslagRepository
import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import javax.sql.DataSource

class PgOppslagRepository(
    private val dataSource: DataSource,
) : OppslagRepository {
    private val oppslagDao = OppslagDao()

    override fun hent(id: OppslagId): Oppslag =
        dataSource.inTransaction { transaction ->
            oppslagDao.hentOppslag(
                oppslagId = id,
                session = transaction,
            )
        }
}
