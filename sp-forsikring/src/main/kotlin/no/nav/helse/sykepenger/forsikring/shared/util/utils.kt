package no.nav.helse.sykepenger.forsikring.shared.util

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

fun <T> DataSource.inTransaction(block: (TransactionalSession) -> T): T = sessionOf(this).use { it.transaction(block) }

fun <T> DataSource.withSession(block: (Session) -> T): T = sessionOf(this).use { block(it) }
