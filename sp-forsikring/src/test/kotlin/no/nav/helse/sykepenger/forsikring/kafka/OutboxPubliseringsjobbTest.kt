package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import no.nav.helse.sykepenger.forsikring.shared.testsupport.TestcontainersSpForsikringDatabase
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated

@Isolated
internal class OutboxPubliseringsjobbTest {
    private val dataSource = TestcontainersSpForsikringDatabase.dataSource

    @BeforeEach
    fun setUp() {
        TestcontainersSpForsikringDatabase.reset()
    }

    @Test
    fun `plukker opp upubliserte meldinger, publiserer dem og markerer dem som publisert`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(dataSource = dataSource, rapidsConnection = rapid, leaderElection = { true })
        val eventName = "test_event"
        val melding = """{"@event_name":"$eventName"}"""
        val key = "key"

        dataSource.inTransaction { transaction ->
            val outboxRepository = PgOutboxRepository(transaction)
            outboxRepository.push(key, melding)
        }

        jobb.kjørEnRunde()

        assertEquals(1, rapid.inspektør.size)
        val publisert = rapid.inspektør.message(0)
        assertEquals(eventName, publisert.path("@event_name").asString())
        assertEquals(key, rapid.inspektør.key(0))

        val nesteMeldingIOutbox = dataSource.inTransaction { transaction -> PgOutboxRepository(transaction).popFirst() }
        assertNull(nesteMeldingIOutbox)
    }

    @Test
    fun `flere meldinger publiseres i rekkefølge`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(dataSource = dataSource, rapidsConnection = rapid, leaderElection = { true })

        dataSource.inTransaction { transaction ->
            val outboxRepository = PgOutboxRepository(transaction)
            outboxRepository.push("en", """{"@event_name":"første_event"}""")
            outboxRepository.push("en", """{"@event_name":"andre_event"}""")
            outboxRepository.push("en", """{"@event_name":"tredje_event"}""")
        }

        jobb.kjørEnRunde()
        jobb.kjørEnRunde()
        jobb.kjørEnRunde()

        assertEquals(3, rapid.inspektør.size)

        val nesteMeldingIOutbox = dataSource.inTransaction { transaction -> PgOutboxRepository(transaction).popFirst() }
        assertNull(nesteMeldingIOutbox)
    }

    @Test
    fun `ingen upubliserte meldinger gir ingen publisering`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapidsConnection = rapid, dataSource = dataSource, leaderElection = { true })

        jobb.kjørEnRunde()

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `publiserer ikke når poden ikke er leader`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapidsConnection = rapid, dataSource = dataSource, leaderElection = { false })

        dataSource.inTransaction { transaction ->
            PgOutboxRepository(transaction).push("en", """{"@event_name":"første_event"}""")
        }

        jobb.kjørEnRundeHvisLeader()

        assertEquals(0, rapid.inspektør.size)
        val nesteMeldingIOutbox = dataSource.inTransaction { transaction -> PgOutboxRepository(transaction).popFirst() }
        assertEquals("en", nesteMeldingIOutbox?.key)
    }

    @Test
    fun `publiserer når poden er leader`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapidsConnection = rapid, dataSource = dataSource, leaderElection = { true })

        dataSource.inTransaction { transaction ->
            PgOutboxRepository(transaction).push("en", """{"@event_name":"første_event"}""")
        }

        jobb.kjørEnRundeHvisLeader()

        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `feil under publisering markerer ikke meldingen som publisert, og jobben kastes ikke videre`() {
        val sviktendeRapid = SvikterVedPubliseringRapid()
        val jobb = OutboxPubliseringsjobb(sviktendeRapid, dataSource, leaderElection = { true })

        dataSource.inTransaction { transaction ->
            val outboxRepository = PgOutboxRepository(transaction)
            outboxRepository.push("en", """{"@event_name": "første_event"}""")
        }

        jobb.kjørEnRunde()

        val nesteMeldingIOutbox = dataSource.inTransaction { transaction -> PgOutboxRepository(transaction).popFirst() }
        assertEquals("en", nesteMeldingIOutbox?.key)
        assertEquals("""{"@event_name": "første_event"}""", nesteMeldingIOutbox?.melding)
    }

    private class SvikterVedPubliseringRapid : RapidsConnection() {
        override fun publish(message: String): Unit = error("Kafka er nede")

        override fun publish(
            key: String,
            message: String,
        ): Unit = error("Kafka er nede")

        override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> = error("Kafka er nede")

        override fun rapidName() = "svikter-rapid"

        override fun start() = Unit

        override fun stop() = Unit
    }
}
