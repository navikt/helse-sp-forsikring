package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.github.navikt.tbd_libs.kafka.Config
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException

object TestcontainersRapid {
    const val RAPID_TOPIC = "tbd.rapid.v1"

    val kafkaConfig =
        KafkaContainer("apache/kafka-native:3.9.1")
            .also { it.start() }
            .let { KafkaConfig(it.bootstrapServers) }
            .also { opprettTopic(RAPID_TOPIC, it) }

    private val producer by lazy {
        KafkaProducer(kafkaConfig.producerConfig(Properties()), StringSerializer(), StringSerializer())
            .also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
    }

    fun sendPåRapid(
        key: String,
        melding: String,
    ) {
        producer.send(ProducerRecord(RAPID_TOPIC, key, melding)).get()
    }

    private val objectMapper = jacksonObjectMapper()

    private val rapidPartisjon = TopicPartition(RAPID_TOPIC, 0)

    private fun nyConsumer() =
        KafkaConsumer(
            kafkaConfig.consumerConfig("testcontainers-rapid-${UUID.randomUUID()}", Properties()),
            StringDeserializer(),
            StringDeserializer(),
        )

    /**
     * Offseten neste melding på rapiden vil få. Les den av før du sender en melding, og bruk den som
     * startpunkt i [ventPåMelding] for å slippe å lese meldinger som ble produsert tidligere i testen.
     */
    fun nesteOffset(): Long = nyConsumer().use { consumer -> consumer.endOffsets(listOf(rapidPartisjon))[rapidPartisjon] ?: 0L }

    fun ventPåMelding(
        fraOffset: Long,
        timeout: Duration = Duration.ofSeconds(30),
        predikat: (JsonNode) -> Boolean,
    ): JsonNode =
        nyConsumer().use { consumer ->
            consumer.assign(listOf(rapidPartisjon))
            consumer.seek(rapidPartisjon, fraOffset)
            val frist = Instant.now().plus(timeout)
            while (Instant.now() < frist) {
                for (record in consumer.poll(Duration.ofMillis(500))) {
                    val melding = runCatching { objectMapper.readTree(record.value()) }.getOrNull() ?: continue
                    if (predikat(melding)) return melding
                }
            }
            error("Fant ingen melding på rapiden som matchet predikatet innen $timeout")
        }

    fun opprettTopic(
        topic: String,
        config: KafkaConfig,
    ) {
        AdminClient.create(config.adminConfig(Properties())).use { adminClient ->
            try {
                adminClient.createTopics(listOf(NewTopic(topic, 1, 1))).all().get()
            } catch (err: ExecutionException) {
                // Topicet finnes allerede, og det er helt greit
                if (err.cause !is org.apache.kafka.common.errors.TopicExistsException) throw err
            }
        }
    }

    class KafkaConfig(
        val bootstrapServers: String,
    ) : Config {
        override fun producerConfig(properties: Properties) =
            Properties().apply {
                put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(SECURITY_PROTOCOL_CONFIG, SecurityProtocol.PLAINTEXT.name)
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.LINGER_MS_CONFIG, "0")
                put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
                putAll(properties)
            }

        override fun consumerConfig(
            groupId: String,
            properties: Properties,
        ) = Properties().apply {
            put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(SECURITY_PROTOCOL_CONFIG, SecurityProtocol.PLAINTEXT.name)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            putAll(properties)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        }

        override fun adminConfig(properties: Properties) =
            Properties().apply {
                put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(SECURITY_PROTOCOL_CONFIG, SecurityProtocol.PLAINTEXT.name)
                putAll(properties)
            }
    }
}
