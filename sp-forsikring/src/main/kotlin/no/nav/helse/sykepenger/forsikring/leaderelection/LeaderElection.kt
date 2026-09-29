package no.nav.helse.sykepenger.forsikring.leaderelection

internal fun interface LeaderElection {
    fun isLeader(): Boolean
}
