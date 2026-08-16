package no.nav.helse.sykepenger.forsikring.råkopi

import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.replikabase.InfotrygdIfFkonto12Dto
import no.nav.helse.sykepenger.forsikring.replikabase.InfotrygdIfVedfrivt10Dto
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import java.time.Instant
import javax.sql.DataSource

class RåkopiService(
    private val replikabaseDataSource: DataSource,
) {
    fun hentNyRåkopi(
        identitetsnummer: Identitetsnummer,
    ): Råkopi =
        replikabaseDataSource.inTransaction { transactionalSession ->
            val transaksjonStartetTidspunkt = Instant.now()
            val replikabaseDao = ReplikabaseDao(transactionalSession)
            Råkopi
                .ny(
                    lestTidspunkt = transaksjonStartetTidspunkt,
                    ifVedfrivt10er =
                        replikabaseDao
                            .hentAlleIfVedfrivt10(identitetsnummer)
                            .map { it.tilRåkopiIfVedfrivt10() },
                    ifFKonto12er =
                        replikabaseDao
                            .hentAlleIfFkonto12(identitetsnummer)
                            .map { it.tilRåkopiIfFkonto12() },
                ).also {
                    loggInfo("Hentet ny råkopi med ${it.ifVedfrivt10er.size} IF_VEDFRIVT_10-rader og ${it.ifFkonto12er.size} IF_FKONTO_12-rader")
                }
        }

    private fun InfotrygdIfVedfrivt10Dto.tilRåkopiIfVedfrivt10(): RåkopiIfVedfrivt10 =
        RåkopiIfVedfrivt10(
            id = RåkopiIfVedfrivt10.Id.ny(),
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF10_GODKJ = IF10_GODKJ,
            IF10_FORSFOM = IF10_FORSFOM,
            IF10_VIRKDATO = IF10_VIRKDATO,
            IF10_TYPE = IF10_TYPE,
            IF10_SELVFOM = IF10_SELVFOM,
            IF10_KOMBI = IF10_KOMBI,
            IF10_PREMGRL = IF10_PREMGRL,
            IF10_FOM = IF10_FOM,
            IF10_PREMIE = IF10_PREMIE,
            IF10_GML_PREMGRL = IF10_GML_PREMGRL,
            IF10_GML_FOM = IF10_GML_FOM,
            IF10_GML_PREMIE = IF10_GML_PREMIE,
            IF10_FRIFOM = IF10_FRIFOM,
            IF10_FORSTOM = IF10_FORSTOM,
            IF10_OPPHGR = IF10_OPPHGR,
            IF10_VARSEL = IF10_VARSEL,
            IF10_TERM_KV = IF10_TERM_KV,
            IF10_TERM_AAR = IF10_TERM_AAR,
            IF10_VARSEL_BELOEP = IF10_VARSEL_BELOEP,
            IF10_BETALT_BELOEP = IF10_BETALT_BELOEP,
            IF10_PURR = IF10_PURR,
            IF10_TKNR_BOST = IF10_TKNR_BOST,
            IF10_TKNR_BEH = IF10_TKNR_BEH,
            OPPRETTET = OPPRETTET,
            ENDRET_I_KILDE = ENDRET_I_KILDE,
            KILDE_IF = KILDE_IF,
            ID_VED = ID_VED,
            OPPDATERT = OPPDATERT,
        )

    private fun InfotrygdIfFkonto12Dto.tilRåkopiIfFkonto12(): RåkopiIfFkonto12 =
        RåkopiIfFkonto12(
            IF01_KODE = IF01_KODE,
            IF01_AGNR_FNR = IF01_AGNR_FNR,
            IF10_FORSFOM_SEQ = IF10_FORSFOM_SEQ,
            IF12_BETDATO_SEQ = IF12_BETDATO_SEQ,
            IF12_FOM = IF12_FOM,
            IF12_TOM = IF12_TOM,
            IF12_BET_KODE = IF12_BET_KODE,
            IF12_FRIUKER = IF12_FRIUKER,
            IF12_BELOEP = IF12_BELOEP,
            IF12_BETDATO = IF12_BETDATO,
            OPPRETTET = OPPRETTET,
            ENDRET_I_KILDE = ENDRET_I_KILDE,
            KILDE_IF = KILDE_IF,
            ID_KONT = ID_KONT,
            OPPDATERT = OPPDATERT,
        )
}
