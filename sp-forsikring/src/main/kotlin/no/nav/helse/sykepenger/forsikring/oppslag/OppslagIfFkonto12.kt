package no.nav.helse.sykepenger.forsikring.oppslag

import no.nav.helse.sykepenger.forsikring.replikabase.IF_FKONTO_12_Rad

class OppslagIfFkonto12(
    val id: OppslagIfFkonto12Id,
    val oppslagIfVedfrivt10Id: OppslagIfVedrift10Id,
    val rad: IF_FKONTO_12_Rad
)
