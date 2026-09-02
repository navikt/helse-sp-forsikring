package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.github.navikt.tbd_libs.kafka.Config
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.testcontainers.kafka.KafkaContainer
import java.util.*
import java.util.concurrent.ExecutionException

object TestcontainersRapid {
    val kafkaConfig =
        KafkaContainer("apache/kafka-native:3.9.1")
            .also { it.start() }
            .let { KafkaConfig(it.bootstrapServers) }
            .also { opprettTopic("tbd.rapid.v1", it) }

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
