package no.nav.helse.sykepenger.forsikring.kafka.lib

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.shared.logging.MdcKey
import no.nav.helse.sykepenger.forsikring.shared.logging.loggError
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.slf4j.MDC
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue
import javax.sql.DataSource

inline fun <reified M> JsonMessage.medParsetMeldingOgTransaksjon(
    mdcMapping: Map<MdcKey, M.() -> Any?>,
    dataSource: DataSource,
    crossinline block: (melding: M, transactionalSession: TransactionalSession) -> Unit,
) {
    medParsetMelding<M>(mdcMapping) { melding ->
        dataSource.inTransaction { transaction ->
            block(melding, transaction)
        }
    }
}

inline fun <reified M> JsonMessage.medParsetMelding(
    mdcMapping: Map<MdcKey, M.() -> Any?>,
    block: (M) -> Unit,
) {
    val meldingJson = toJson()
    val parsetMelding =
        runCatching { objectMapper.readValue<M>(meldingJson) }
            .getOrElse { throwable ->
                loggError(
                    "Klarte ikke tolke melding fra JSON",
                    throwable,
                    "meldingJson" to meldingJson,
                )
                throw throwable
            }

    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    try {
        val mappedKeyValues = mdcMapping.map { it.key.value to it.value(parsetMelding)?.toString() }

        val removedKeys = mappedKeyValues.filter { it.second == null }.toMap().keys
        val changedKeyValues = mappedKeyValues.filter { it.second != null }

        val newContextMap =
            contextMap
                .filterNot { (key, _) -> key in removedKeys }
                .plus(changedKeyValues)

        MDC.setContextMap(newContextMap)
        loggInfo("Mottok og tolket ${M::class.simpleName}", "melding" to meldingJson)
        block(parsetMelding)
    } finally {
        MDC.setContextMap(contextMap)
    }
}

val objectMapper: JsonMapper =
    jacksonMapperBuilder()
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
