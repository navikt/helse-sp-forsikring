package no.nav.helse.sykepenger.forsikring.replikabase

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.helse.sykepenger.forsikring.Betaling
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.RåForsikring

private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

private fun Int?.tilLocalDate(): LocalDate? =
    if (this == null || this == 0) null
    else LocalDate.parse(toString().padStart(8, '0'), dateFormatter)

fun List<IF_VEDFRIVT_10_Rad>.tilRåForsikringer(): List<RåForsikring> = map { rad ->
    RåForsikring(
        id = rad.IF10_FORSFOM_SEQ,
        type = when (val type = rad.IF10_TYPE) {
            '1' -> NavKjøptForsikring.Type.SELVSTENDIG_80_PROSENT_FRA_DAG_1
            '2' -> NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_17
            '3' -> NavKjøptForsikring.Type.SELVSTENDIG_100_PROSENT_FRA_DAG_1
            '4' -> NavKjøptForsikring.Type.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1
            '5' -> NavKjøptForsikring.Type.FRILANSER_100_PROSENT_FRA_DAG_1
            else -> error("Ukjent forsikringstype: $type")
        },
        forsikringFom = rad.IF10_FORSFOM.tilLocalDate(),
        virkningsdato = rad.IF10_VIRKDATO.tilLocalDate()
            ?: error("IF10_VIRKDATO er 0 for rad med IF10_FORSFOM_SEQ=${rad.IF10_FORSFOM_SEQ}"),
        opphørsdato = rad.IF10_FORSTOM.tilLocalDate(),
        betalinger = rad.IF_FKONTO_12_rader.map { fkonto ->
            Betaling(
                fom = fkonto.IF12_FOM.tilLocalDate(),
                tom = fkonto.IF12_TOM.tilLocalDate(),
                betdato = fkonto.IF12_BETDATO.tilLocalDate(),
            )
        }
    )
}
