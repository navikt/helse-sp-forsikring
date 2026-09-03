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

    private val partition = TopicPartition(RAPID_TOPIC, 0)

    private val adminClient by lazy {
        AdminClient
            .create(kafkaConfig.adminConfig(Properties()))
            .also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
    }

    /**
     * Venter til konsumentgruppen har committet et offset som er høyere enn [offset], altså at meldingen på det
     * offsetet er ferdig behandlet. KafkaRapid gjør commitSync etter at alle meldingene i en poll-batch er behandlet
     * ferdig av alle rivere, så et committet offset er en pålitelig indikasjon på at applikasjonen faktisk er ferdig
     * med meldingen - inkludert eventuelle databaseskrivinger.
     */
    fun ventTilMeldingErFerdigBehandlet(
        konsumentgruppe: String,
        offset: Long,
        timeout: Duration = Duration.ofSeconds(20),
    ) {
        val timeoutInstant = Instant.now().plus(timeout)
        var sistObserverteOffset: Long? = null
        while (Instant.now() < timeoutInstant) {
            sistObserverteOffset =
                adminClient
                    .listConsumerGroupOffsets(konsumentgruppe)
                    .partitionsToOffsetAndMetadata()
                    .get()[partition]
                    ?.offset()
            if (sistObserverteOffset != null && sistObserverteOffset > offset) return
            Thread.sleep(20)
        }
        error(
            "Konsumentgruppen $konsumentgruppe ble ikke ferdig med meldingen på offset $offset innen $timeout " +
                "(sist committede offset var $sistObserverteOffset)",
        )
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

    class Klient(
        startOffset: Long? = null,
    ) : AutoCloseable {
        private val consumer =
            KafkaConsumer(
                kafkaConfig.consumerConfig("testcontainers-rapid-${UUID.randomUUID()}", Properties()),
                StringDeserializer(),
                StringDeserializer(),
            ).apply {
                assign(listOf(partition))
                if (startOffset != null) {
                    seek(partition, startOffset)
                } else {
                    seekToEnd(setOf(partition))
                }
                // Seek er lazy, denne linjen sørger for at vi faktisk går dit nå
                position(partition)
            }

        private val producer =
            KafkaProducer(
                kafkaConfig.producerConfig(Properties()),
                StringSerializer(),
                StringSerializer(),
            )

        private val meldingsbuffer = mutableListOf<JsonNode>()
        private val objectMapper = jacksonObjectMapper()

        /** @return offsetet meldingen ble lagt på */
        fun send(
            key: String,
            melding: JsonNode,
        ): Long =
            producer
                .send(ProducerRecord(RAPID_TOPIC, key, melding.toPrettyString()))
                .get()
                .offset()

        fun konsumerMelding(
            timeoutSekunder: Int = 5,
            predicate: (JsonNode) -> Boolean,
        ): JsonNode {
            val timeoutInstant = Instant.now().plusSeconds(timeoutSekunder.toLong())
            while (Instant.now() < timeoutInstant) {
                val matchingMessage = meldingsbuffer.find { predicate(it) }
                if (matchingMessage != null) {
                    meldingsbuffer.remove(matchingMessage)
                    return matchingMessage
                } else {
                    meldingsbuffer.addAll(
                        consumer
                            .poll(Duration.ofMillis(250))
                            .map { objectMapper.readTree(it.value()) },
                    )
                }
            }
            error(
                "Fikk ingen melding som matchet forventningene innen $timeoutSekunder sekunder. " +
                    "Meldingene i bufferet var:\n" +
                    meldingsbuffer.joinToString("\n") { it.toString() },
            )
        }

        override fun close() {
            consumer.close()
            producer.close()
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
