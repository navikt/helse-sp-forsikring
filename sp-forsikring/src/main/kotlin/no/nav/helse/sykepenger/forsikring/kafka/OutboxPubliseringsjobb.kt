package no.nav.helse.sykepenger.forsikring.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

internal class OutboxPubliseringsjobb(
    private val rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : RapidsConnection.StatusListener {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onStartup(rapidsConnection: RapidsConnection) {
        loggInfo("Starter outbox-publiseringsjobb")
        job =
            scope.launch {
                while (isActive) {
                    kjørEnRunde()
                    delay(0.5.seconds)
                }
            }
    }

    override fun onShutdownSignal(rapidsConnection: RapidsConnection) {
        loggInfo("Stopper outbox-publiseringsjobb")
        runBlocking {
            job?.cancelAndJoin()
        }
    }

    internal fun kjørEnRunde() {
        try {
            dataSource.inTransaction { transaction ->
                val outboxRepository = PgOutboxRepository(transaction)
                val konvolutt = outboxRepository.hent() ?: return@inTransaction
                rapidsConnection.publish(konvolutt.key, konvolutt.melding)
                outboxRepository.fjern(konvolutt.id)
            }
        } catch (e: Exception) {
            loggError("Feil under publisering av outbox-meldinger. Prøver igjen ved neste poll", e)
        }
    }
}
