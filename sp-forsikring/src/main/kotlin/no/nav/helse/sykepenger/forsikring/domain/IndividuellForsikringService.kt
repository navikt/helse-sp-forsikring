package no.nav.helse.sykepenger.forsikring.domain

import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfFkonto12
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ofPattern

class IndividuellForsikringService {
    fun tolkTilIndividuelleForsikringer(råkopi: Råkopi): List<IndividuellForsikring> =
        råkopi.ifVedfrivt10er
            .map { ifVedfrivt10 ->
                val opphørsdato = ifVedfrivt10.IF10_FORSTOM.infotrygdIntDatoTilLocalDate()
                IndividuellForsikring.ny(
                    råkopiIfVedfrivt10Id = ifVedfrivt10.id,
                    type =
                        when (ifVedfrivt10.IF10_TYPE) {
                            '1' -> IndividuellForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1
                            '2' -> IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17
                            '3' -> IndividuellForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1
                            '4' -> IndividuellForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1
                            '5' -> IndividuellForsikringType.FRILANSER_100_PROSENT_FRA_DAG_1
                            else -> error("Ukjent forsikringstype: ${ifVedfrivt10.IF10_TYPE}")
                        },
                    virkningsdato = ifVedfrivt10.IF10_VIRKDATO.infotrygdIntDatoTilLocalDate()!!,
                    opphører = opphørsdato != null || ifVedfrivt10.IF10_OPPHGR.isNotBlank(),
                    opphørsdato = opphørsdato,
                    premiegrunnlag = ifVedfrivt10.IF10_PREMGRL,
                    erBetaltNoenGang =
                        råkopi
                            .ifFkonto12ErFor(ifVedfrivt10)
                            .any { ifFKonto12 -> ifFKonto12.IF12_BETDATO?.infotrygdIntDatoTilLocalDate() != null },
                )
            }.also { individuelleForsikringer ->
                loggInfo(
                    "Tolket råkopi til ${individuelleForsikringer.size} individuelle forsikringer",
                    "individuelleForsikringer" to
                        individuelleForsikringer
                            .map {
                                mapOf(
                                    "type" to it.type,
                                    "virkningsdato" to it.virkningsdato,
                                    "opphører" to it.opphører,
                                    "opphørsdato" to it.opphørsdato,
                                    "premiegrunnlag" to it.premiegrunnlag,
                                    "erBetaltNoenGang" to it.erBetaltNoenGang,
                                )
                            }.joinToString(prefix = "[", postfix = "]", separator = ", "),
                )
            }

    private fun Råkopi.ifFkonto12ErFor(ifVedfrivt10: RåkopiIfVedfrivt10): List<RåkopiIfFkonto12> =
        ifFkonto12er
            .filter { ifFKonto12 ->
                ifFKonto12.IF01_KODE == ifVedfrivt10.IF01_KODE &&
                    ifFKonto12.IF01_AGNR_FNR == ifVedfrivt10.IF01_AGNR_FNR &&
                    ifFKonto12.IF10_FORSFOM_SEQ == ifVedfrivt10.IF10_FORSFOM_SEQ
            }

    private fun Int.infotrygdIntDatoTilLocalDate(): LocalDate? =
        if (this == 0) {
            null
        } else {
            LocalDate.parse(toString().padStart(8, '0'), ofPattern("yyyyMMdd"))
        }
}
