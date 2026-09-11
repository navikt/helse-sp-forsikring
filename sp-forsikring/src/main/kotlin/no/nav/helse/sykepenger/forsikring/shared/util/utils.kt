package no.nav.helse.sykepenger.forsikring.shared.util

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

fun <T> DataSource.inTransaction(block: (TransactionalSession) -> T): T = sessionOf(this).use { it.transaction(block) }
