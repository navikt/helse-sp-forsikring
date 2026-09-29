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
    private var job: Job? = null

    override fun onStartup(rapidsConnection: RapidsConnection) {
        loggInfo("Starter outbox-publiseringsjobb")
        job =
            CoroutineScope(Dispatchers.IO).launch {
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
            flushOutbox()
        } catch (e: Exception) {
            loggError("Feil under publisering av outbox-meldinger. Prøver igjen ved neste poll", e)
        }
    }

    private fun flushOutbox() {
        do {
            val konvolutt =
                dataSource.inTransaction { transaction ->
                    PgOutboxRepository(transaction)
                        .popFirst()
                        ?.also { rapidsConnection.publish(it.key, it.melding) }
                }
        } while (konvolutt != null)
    }
}
