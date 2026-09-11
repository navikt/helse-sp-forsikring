package no.nav.helse.sykepenger.forsikring.kafka.lib

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.treeToValue
import javax.sql.DataSource

inline fun <reified M> JsonMessage.medParsetMeldingOgTransaction(
    mdcMapping: Map<MdcKey, M.() -> Any?> = emptyMap(),
    dataSource: DataSource,
    crossinline block: (melding: M, transaction: TransactionalSession) -> Unit,
) {
    medParsetMelding<M>(mdcMapping) { melding ->
        dataSource.inTransaction { transaction ->
            block(melding, transaction)
        }
    }
}

inline fun <reified M> JsonMessage.medParsetMelding(
    mdcMapping: Map<MdcKey, M.() -> Any?>,
    crossinline block: (M) -> Unit,
) {
    val meldingJson = toJson()
    val meldingJsonNode =
        runCatching { objectMapper.readTree(meldingJson) }
            .getOrElse { throwable ->
                loggError(
                    "Klarte ikke tolke melding som JSON",
                    throwable,
                    "meldingJson" to meldingJson,
                )
                throw throwable
            }

    val parsetMelding =
        runCatching { objectMapper.treeToValue<M>(meldingJsonNode) }
            .getOrElse { throwable ->
                loggError(
                    "Klarte ikke tolke JSON til forventet meldingstype",
                    throwable,
                    "meldingJson" to meldingJson,
                )
                throw throwable
            }

    val mdcKeyValues =
        automatiskeMdcVerdier(meldingJsonNode)
            .plus(mdcMapping.map { (mdcKey, hentVerdi) -> mdcKey to hentVerdi(parsetMelding)?.toString() })
            .toList()
            .toTypedArray()

    medMdc(*mdcKeyValues) {
        loggInfo("Mottok og tolket ${M::class.simpleName}", "melding" to meldingJson)
        block(parsetMelding)
    }
}

fun automatiskeMdcVerdier(melding: JsonNode): Map<MdcKey, String?> =
    runCatching {
        buildMap {
            putIfString(MdcKey.IDENTITETSNUMMER, melding["fødselsnummer"])
            putIfString(MdcKey.MELDING_ID, melding["@id"])
            putIfString(MdcKey.VEDTAKSPERIODE_ID, melding["vedtaksperiodeId"])
            putIfString(MdcKey.SPLEIS_BEHANDLING_ID, melding["behandlingId"])
            melding.meldingnavn()?.let { put(MdcKey.MELDINGNAVN, it) }
        }
    }.getOrElse { emptyMap() }

private fun MutableMap<MdcKey, String>.putIfString(
    key: MdcKey,
    jsonNode: JsonNode?,
) {
    jsonNode?.stringValueIfString()?.let { put(key, it) }
}

private fun JsonNode.meldingnavn(): String? =
    when (val eventName = get("@event_name")?.stringValueIfString()) {
        null -> null
        "behov" ->
            eventName +
                get("@behov")
                    ?.takeIf { it.isArray }
                    ?.mapNotNull { it.stringValueIfString() }
                    ?.takeUnless { it.isEmpty() }
                    ?.joinToString(prefix = "[", separator = ",", postfix = "]")
                    .orEmpty()

        else -> eventName
    }

private fun JsonNode?.stringValueIfString(): String? = this?.takeIf { it.isString }?.stringValue()

val objectMapper: JsonMapper =
    jacksonMapperBuilder()
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
