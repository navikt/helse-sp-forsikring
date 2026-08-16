package no.nav.helse.sykepenger.forsikring.råkopi

import no.nav.helse.sykepenger.forsikring.shared.util.generateUuidV7
import java.time.Instant
import java.util.*

data class Råkopi(
    val id: Id,
    val lestTidspunkt: Instant,
    val ifVedfrivt10er: List<RåkopiIfVedfrivt10>,
    val ifFkonto12er: List<RåkopiIfFkonto12>,
) {
    companion object {
        fun ny(
            lestTidspunkt: Instant,
            ifVedfrivt10er: List<RåkopiIfVedfrivt10>,
            ifFKonto12er: List<RåkopiIfFkonto12>,
        ): Råkopi =
            Råkopi(
                id = Id.ny(),
                lestTidspunkt = lestTidspunkt,
                ifVedfrivt10er = ifVedfrivt10er,
                ifFkonto12er = ifFKonto12er,
            )

        fun fraLagring(
            id: Id,
            lestTidspunkt: Instant,
            ifVedfrivt10er: List<RåkopiIfVedfrivt10>,
            ifFKonto12er: List<RåkopiIfFkonto12>,
        ): Råkopi =
            Råkopi(
                id = id,
                lestTidspunkt = lestTidspunkt,
                ifVedfrivt10er = ifVedfrivt10er,
                ifFkonto12er = ifFKonto12er,
            )
    }

    @JvmInline
    value class Id(
        val value: UUID,
    ) {
        companion object {
            fun ny() = Id(generateUuidV7())
        }
    }
}
