package no.nav.helse.sykepenger.forsikring.oppslag.adapter.postgres

import javax.sql.DataSource
import no.nav.helse.sykepenger.forsikring.oppslag.domain.Oppslag
import no.nav.helse.sykepenger.forsikring.oppslag.domain.OppslagId
import no.nav.helse.sykepenger.forsikring.oppslag.seam.OppslagRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction

class PgOppslagRepository(private val dataSource: DataSource): OppslagRepository {
    private val oppslagDao = OppslagDao()

    override fun hent(id: OppslagId): Oppslag {
        return dataSource.inTransaction { transaction ->
            oppslagDao.hentOppslag(
                oppslagId = id,
                session = transaction
            )
        }
    }
}
