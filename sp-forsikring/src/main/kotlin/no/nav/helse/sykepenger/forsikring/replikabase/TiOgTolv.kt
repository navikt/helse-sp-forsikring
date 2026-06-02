package no.nav.helse.sykepenger.forsikring.replikabase

data class TiOgTolv(
    val ifVedfrivt10Rad: IF_VEDFRIVT_10_Rad,
    val ifFkonto12Rader: List<IF_FKONTO_12_Rad>
)
