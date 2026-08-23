package no.nav.helse.sykepenger.forsikring.shared.testsupport

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import kotlin.test.assertEquals

private val objectMapper =
    jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

fun assertJsonEquals(
    expectedJson: String,
    actualJson: String,
    bortsettFraProperties: Set<String> = emptySet(),
) = assertJsonEquals(
    expectedJson = expectedJson,
    actualJsonNode = objectMapper.readTree(actualJson),
    bortsettFraProperties = bortsettFraProperties,
)

fun assertJsonEquals(
    expectedJson: String,
    actualJsonNode: JsonNode,
    bortsettFraProperties: Set<String> = emptySet(),
) {
    val expected =
        objectMapper
            .readTree(expectedJson)
            .deepSortedObjectNodeCopy()
            .apply { bortsettFraProperties.forEach { remove(it) } }
    val actual =
        actualJsonNode
            .deepSortedObjectNodeCopy()
            .apply { bortsettFraProperties.forEach { remove(it) } }
    assertEquals(
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
    )
}

private fun JsonNode.sortedDeep(): JsonNode =
    when (this) {
        is ObjectNode ->
            objectMapper.createObjectNode().also { sorted ->
                properties()
                    .asSequence()
                    .sortedBy { (name, _) -> name }
                    .forEach { (name, value) -> sorted.set(name, value.sortedDeep()) }
            }

        is ArrayNode ->
            objectMapper.createArrayNode().also { sortedArray ->
                forEach { sortedArray.add(it.sortedDeep()) }
            }

        else -> this.deepCopy()
    }

private fun JsonNode.deepSortedObjectNodeCopy(): ObjectNode = sortedDeep() as ObjectNode

val RAPIDS_GENERERTE_PROPERTIES =
    setOf(
        "@opprettet",
        "system_read_count",
        "system_participating_services",
    )
