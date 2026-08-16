package no.nav.helse.sykepenger.forsikring.shared.testsupport

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikringService
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringService
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfFkonto12
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val TESTFØDSELSNUMMER = "01020312345"
val TESTSKJÆRINGSTIDSPUNKT: LocalDate = LocalDate.parse("2026-01-01")

/**
 * Beskriver én forsikringsrad i Infotrygd, slik testene ønsker å se den. Feltene svarer til
 * kolonnene i IF_VEDFRIVT_10 og hvorvidt det finnes en betaling i IF_FKONTO_12.
 */
data class Infotrygdforsikring(
    val forsikringssekvensnummer: Int = 0,
    val type: NavKjøptForsikringType = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
    val virkningsdato: LocalDate = TESTSKJÆRINGSTIDSPUNKT,
    val opphørsdato: LocalDate? = null,
    val premiegrunnlag: Int = 0,
    val erBetalt: Boolean = true,
)

/**
 * Lagrer en råkopi og en forsikringsvurdering i sp-forsikring-databasen på samme måte som
 * [no.nav.helse.sykepenger.forsikring.kafka.ForsikringsvurderingBehovRiver] gjør i produksjon,
 * slik at rivere og API-et kan lese vurderingen tilbake fra basen.
 */
fun lagreForsikringsvurdering(
    identitetsnummer: String = TESTFØDSELSNUMMER,
    yrkesaktivitetstype: Yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
    spesielleYrkesgrupper: Set<SpesiellYrkesgruppe> = emptySet(),
    skjæringstidspunkt: LocalDate = TESTSKJÆRINGSTIDSPUNKT,
    forsikringer: List<Infotrygdforsikring> = emptyList(),
): Forsikringsvurdering.Id {
    val råkopi = råkopi(forsikringer)
    val forsikringsvurdering =
        Forsikringsvurdering.utførVurdering(
            identitetsnummer = Identitetsnummer.fraString(identitetsnummer),
            yrkesaktivitetstype = yrkesaktivitetstype,
            spesielleYrkesgrupper = spesielleYrkesgrupper,
            skjæringstidspunkt = skjæringstidspunkt,
            råkopiId = råkopi.id,
            kollektiveForsikringer = KollektivForsikringService().utledKollektiveForsikringer(spesielleYrkesgrupper),
            navKjøpteForsikringer = NavKjøptForsikringService().tolkTilNavKjøpteForsikringer(råkopi),
        )
    TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
        RåkopiRepository(transaction).lagre(råkopi)
        ForsikringsvurderingRepository(transaction).lagre(
            forsikringsvurdering,
            behovJson(
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                spesielleYrkesgrupper = spesielleYrkesgrupper,
                skjæringstidspunkt = skjæringstidspunkt,
            ),
        )
    }
    return forsikringsvurdering.id
}

private fun råkopi(forsikringer: List<Infotrygdforsikring>): Råkopi {
    val ifVedfrivt10er = forsikringer.map { it.tilRåkopiIfVedfrivt10() }
    val ifFkonto12er =
        forsikringer
            .filter { it.erBetalt }
            .map { it.tilRåkopiIfFkonto12() }
    return Råkopi.ny(
        lestTidspunkt = Instant.now(),
        ifVedfrivt10er = ifVedfrivt10er,
        ifFKonto12er = ifFkonto12er,
    )
}

private fun behovJson(
    identitetsnummer: String,
    yrkesaktivitetstype: Yrkesaktivitetstype,
    spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
    skjæringstidspunkt: LocalDate,
): String =
    """
    {
        "@behov": [ "Forsikringsvurdering" ],
        "fødselsnummer": "$identitetsnummer",
        "yrkesaktivitetstype": "$yrkesaktivitetstype",
        "Forsikringsvurdering": {
            "spesielleYrkesgrupper": [ ${spesielleYrkesgrupper.joinToString(",") { "\"${it.tilBehovsverdi()}\"" }} ],
            "skjæringstidspunkt": "$skjæringstidspunkt"
        }
    }
    """.trimIndent()

private fun SpesiellYrkesgruppe.tilBehovsverdi(): String =
    when (this) {
        SpesiellYrkesgruppe.FISKER_BLAD_B -> "FISKER_BLAD_B"
        SpesiellYrkesgruppe.JORDBRUKER -> "JORDBRUKER"
        SpesiellYrkesgruppe.REINDRIFTER -> "REINDRIFTER"
    }

private val NavKjøptForsikringType.infotrygdkode: Char
    get() =
        when (this) {
            NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> '1'
            NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> '2'
            NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> '3'
            NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> '4'
            NavKjøptForsikringType.FRILANSER_100_PROSENT_FRA_DAG_1 -> '5'
        }

private fun Infotrygdforsikring.tilRåkopiIfVedfrivt10(): RåkopiIfVedfrivt10 =
    RåkopiIfVedfrivt10(
        id = RåkopiIfVedfrivt10.Id.ny(),
        IF01_KODE = IF01_KODE,
        IF01_AGNR_FNR = IF01_AGNR_FNR,
        IF10_FORSFOM_SEQ = forsikringssekvensnummer,
        IF10_GODKJ = 'J',
        IF10_FORSFOM = 0,
        IF10_VIRKDATO = virkningsdato.tilInfotrygddato(),
        IF10_TYPE = type.infotrygdkode,
        IF10_SELVFOM = " ",
        IF10_KOMBI = ' ',
        IF10_PREMGRL = premiegrunnlag,
        IF10_FOM = 0,
        IF10_PREMIE = 0,
        IF10_GML_PREMGRL = 0,
        IF10_GML_FOM = 0,
        IF10_GML_PREMIE = 0,
        IF10_FRIFOM = 0,
        IF10_FORSTOM = opphørsdato.tilInfotrygddato(),
        IF10_OPPHGR = " ",
        IF10_VARSEL = 0,
        IF10_TERM_KV = ' ',
        IF10_TERM_AAR = " ",
        IF10_VARSEL_BELOEP = 0,
        IF10_BETALT_BELOEP = 0,
        IF10_PURR = 0,
        IF10_TKNR_BOST = 0,
        IF10_TKNR_BEH = 0,
        OPPRETTET = Instant.now(),
        ENDRET_I_KILDE = Instant.now(),
        KILDE_IF = " ",
        ID_VED = BigDecimal.ZERO,
        OPPDATERT = null,
    )

private fun Infotrygdforsikring.tilRåkopiIfFkonto12(): RåkopiIfFkonto12 =
    RåkopiIfFkonto12(
        IF01_KODE = IF01_KODE,
        IF01_AGNR_FNR = IF01_AGNR_FNR,
        IF10_FORSFOM_SEQ = forsikringssekvensnummer,
        IF12_BETDATO_SEQ = 1,
        IF12_FOM = null,
        IF12_TOM = null,
        IF12_BET_KODE = null,
        IF12_FRIUKER = null,
        IF12_BELOEP = null,
        IF12_BETDATO = virkningsdato.tilInfotrygddato(),
        OPPRETTET = Instant.now(),
        ENDRET_I_KILDE = Instant.now(),
        KILDE_IF = " ",
        ID_KONT = BigDecimal.ZERO,
        OPPDATERT = null,
    )

private const val IF01_KODE = '1'
private const val IF01_AGNR_FNR = 3020112345L

private fun LocalDate?.tilInfotrygddato(): Int = this?.format(DateTimeFormatter.ofPattern("yyyyMMdd"))?.toInt() ?: 0
