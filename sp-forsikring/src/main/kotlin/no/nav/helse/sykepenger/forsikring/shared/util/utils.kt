package no.nav.helse.sykepenger.forsikring.shared.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf

fun Int.toLocalDate() =
    if (this == 0) null else LocalDate.parse(this.toString().padStart(8, '0'), DateTimeFormatter.ofPattern("yyyyMMdd"))

fun <T> DataSource.inTransaction(block: (TransactionalSession) -> T): T =
    sessionOf(this).use { it.transaction(block) }

fun <T> DataSource.withSession(block: (Session) -> T): T =
    sessionOf(this).use { block(it) }
