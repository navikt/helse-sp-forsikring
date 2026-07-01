package no.nav.helse.sykepenger.forsikring.shared.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level

val teamLogs: Logger = LoggerFactory.getLogger("tjenestekall")
inline val <reified T> T.logg: Logger
    get() = LoggerFactory.getLogger(T::class.java)

inline fun <reified T> T.loggError(
    melding: String,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.ERROR, melding, teamLogsDetaljer.toList())
}

inline fun <reified T> T.loggError(
    melding: String,
    throwable: Throwable?,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.ERROR, melding, teamLogsDetaljer.toList(), throwable)
}

inline fun <reified T> T.loggWarn(
    melding: String,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.WARN, melding, teamLogsDetaljer.toList())
}

inline fun <reified T> T.loggWarn(
    melding: String,
    throwable: Throwable?,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.WARN, melding, teamLogsDetaljer.toList(), throwable)
}

inline fun <reified T> T.loggInfo(
    melding: String,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.INFO, melding, teamLogsDetaljer.toList())
}

inline fun <reified T> T.loggDebug(
    melding: String,
    vararg teamLogsDetaljer: Pair<String, Any?>
) {
    loggMedDetaljer(logg, Level.DEBUG, melding, teamLogsDetaljer.toList())
}

fun loggMedDetaljer(
    logger: Logger,
    level: Level,
    melding: String,
    teamLogsDetaljer: List<Pair<String, Any?>>,
    throwable: Throwable? = null
) {
    logger
        .atLevel(level)
        .setMessage(melding)
        .addMarker(SKIP_TEAM_LOGS_MARKER)
        .log()
    teamLogs
        .atLevel(level)
        .setMessage(melding.medTeamLogsDetaljer(teamLogsDetaljer))
        .also { if (throwable != null) it.setCause(throwable) }
        .log()
}

private fun String.medTeamLogsDetaljer(teamLogsDetaljer: List<Pair<String, Any?>>): String =
    buildString {
        append(this@medTeamLogsDetaljer)
        if (teamLogsDetaljer.isNotEmpty()) {
            append(" - ")
            teamLogsDetaljer.forEach { (name, value) ->
                append(name)
                append(": ")
                append(if (value is String) "\"$value\"" else value.toString())
            }
        }
    }

enum class MdcKey(
    val value: String
) {
    HENDELSE_ID("hendelseId"),
    MELDING_ID("meldingId")
}

fun <T> medMdc(
    vararg pairs: Pair<MdcKey, String>?,
    block: () -> T
): T {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        MDC.setContextMap(contextMap + pairs.filterNotNull().map { it.first.value to it.second })
        return block()
    } finally {
        MDC.setContextMap(contextMap)
    }
}
