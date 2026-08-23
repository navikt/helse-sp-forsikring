package no.nav.helse.sykepenger.forsikring.shared.testsupport

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.VurdertNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfFkonto12
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiIfVedfrivt10
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun lagIdentitetsnummer(): Identitetsnummer =
    Identitetsnummer.fraString(
        com.github.navikt.tbd_libs.testdata
            .lagIdentitetsnummer(),
    )

fun lagForsikringsvurdering(
    skjæringstidspunkt: LocalDate,
    id: Forsikringsvurdering.Id = Forsikringsvurdering.Id.ny(),
    identitetsnummer: Identitetsnummer = lagIdentitetsnummer(),
    yrkesaktivitetstype: Yrkesaktivitetstype = Yrkesaktivitetstype.SELVSTENDIG,
    spesielleYrkesgrupper: Set<SpesiellYrkesgruppe> = emptySet(),
    råkopiId: Råkopi.Id = Råkopi.Id.ny(),
    navKjøpteForsikringer: List<VurdertNavKjøptForsikring> = emptyList(),
    kollektivForsikring: KollektivForsikring? = null,
    vurdertTidspunkt: Instant = Instant.now(),
): Forsikringsvurdering =
    Forsikringsvurdering.fraLagring(
        id = id,
        identitetsnummer = identitetsnummer,
        yrkesaktivitetstype = yrkesaktivitetstype,
        spesielleYrkesgrupper = spesielleYrkesgrupper,
        skjæringstidspunkt = skjæringstidspunkt,
        råkopiId = råkopiId,
        navKjøpteForsikringer = navKjøpteForsikringer,
        kollektivForsikring = kollektivForsikring,
        vurdertTidspunkt = vurdertTidspunkt,
    )

fun lagVurdertNavKjøptForsikring(
    virkningsdato: LocalDate,
    råkopiIfVedfrivt10Id: RåkopiIfVedfrivt10.Id = RåkopiIfVedfrivt10.Id.ny(),
    type: NavKjøptForsikringType = NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1,
    opphører: Boolean = false,
    opphørsdato: LocalDate? = null,
    premiegrunnlag: Int = 0,
    erBetaltNoenGang: Boolean = true,
    konklusjon: VurdertNavKjøptForsikring.Konklusjon = VurdertNavKjøptForsikring.Konklusjon.GYLDIG,
): VurdertNavKjøptForsikring =
    VurdertNavKjøptForsikring.fraLagring(
        råkopiIfVedfrivt10Id = råkopiIfVedfrivt10Id,
        type = type,
        virkningsdato = virkningsdato,
        opphører = opphører,
        opphørsdato = opphørsdato,
        premiegrunnlag = premiegrunnlag,
        erBetaltNoenGang = erBetaltNoenGang,
        konklusjon = konklusjon,
    )

fun lagRåkopi(
    id: Råkopi.Id = Råkopi.Id.ny(),
    lestTidspunkt: Instant = Instant.now(),
    ifVedfrivt10er: List<RåkopiIfVedfrivt10> = emptyList(),
    ifFKonto12er: List<RåkopiIfFkonto12> = emptyList(),
): Råkopi =
    Råkopi.fraLagring(
        id = id,
        lestTidspunkt = lestTidspunkt,
        ifVedfrivt10er = ifVedfrivt10er,
        ifFKonto12er = ifFKonto12er,
    )

fun lagRåkopiIfVedfrivt10(
    IF01_AGNR_FNR: Long,
    id: RåkopiIfVedfrivt10.Id = RåkopiIfVedfrivt10.Id.ny(),
    IF01_KODE: Char = '1',
    IF10_FORSFOM_SEQ: Int = 0,
    IF10_GODKJ: Char = 'J',
    IF10_FORSFOM: Int = 0,
    IF10_VIRKDATO: Int = 0,
    IF10_TYPE: Char = '1',
    IF10_SELVFOM: String = " ",
    IF10_KOMBI: Char = ' ',
    IF10_PREMGRL: Int = 0,
    IF10_FOM: Int = 0,
    IF10_PREMIE: Int = 0,
    IF10_GML_PREMGRL: Int = 0,
    IF10_GML_FOM: Int = 0,
    IF10_GML_PREMIE: Int = 0,
    IF10_FRIFOM: Int = 0,
    IF10_FORSTOM: Int = 0,
    IF10_OPPHGR: String = " ",
    IF10_VARSEL: Int = 0,
    IF10_TERM_KV: Char = ' ',
    IF10_TERM_AAR: String = " ",
    IF10_VARSEL_BELOEP: Int = 0,
    IF10_BETALT_BELOEP: Int = 0,
    IF10_PURR: Int = 0,
    IF10_TKNR_BOST: Int = 0,
    IF10_TKNR_BEH: Int = 0,
    OPPRETTET: Instant = Instant.now(),
    ENDRET_I_KILDE: Instant = Instant.now(),
    KILDE_IF: String = " ",
    ID_VED: BigDecimal = BigDecimal.ZERO,
    OPPDATERT: Instant? = null,
): RåkopiIfVedfrivt10 =
    RåkopiIfVedfrivt10(
        id = id,
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

fun lagRåkopiIfFkonto12(
    IF01_AGNR_FNR: Long,
    IF01_KODE: Char = '1',
    IF10_FORSFOM_SEQ: Int = 0,
    IF12_BETDATO_SEQ: Int = 1,
    IF12_FOM: Int? = null,
    IF12_TOM: Int? = null,
    IF12_BET_KODE: Char? = null,
    IF12_FRIUKER: String? = null,
    IF12_BELOEP: BigDecimal? = null,
    IF12_BETDATO: Int = 0,
    OPPRETTET: Instant = Instant.now(),
    ENDRET_I_KILDE: Instant = Instant.now(),
    KILDE_IF: String = " ",
    ID_KONT: BigDecimal = BigDecimal.ZERO,
    OPPDATERT: Instant? = null,
): RåkopiIfFkonto12 =
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

fun lagRåkopiFor(forsikringsvurdering: Forsikringsvurdering): Råkopi =
    lagRåkopi(
        id = forsikringsvurdering.råkopiId,
        ifVedfrivt10er =
            forsikringsvurdering.navKjøpteForsikringer.mapIndexed { indeks, navKjøptForsikring ->
                lagRåkopiIfVedfrivt10(
                    IF01_AGNR_FNR = forsikringsvurdering.identitetsnummer.tilInfotrygdFødselsnummer(),
                    id = navKjøptForsikring.råkopiIfVedfrivt10Id,
                    IF10_FORSFOM_SEQ = indeks,
                    IF10_VIRKDATO = navKjøptForsikring.virkningsdato.tilInfotrygddato(),
                    IF10_TYPE =
                        when (navKjøptForsikring.type) {
                            NavKjøptForsikringType.SELVSTENDIG_80_PROSENT_FRA_DAG_1 -> '1'
                            NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_17 -> '2'
                            NavKjøptForsikringType.SELVSTENDIG_100_PROSENT_FRA_DAG_1 -> '3'
                            NavKjøptForsikringType.SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1 -> '4'
                            NavKjøptForsikringType.FRILANSER_100_PROSENT_FRA_DAG_1 -> '5'
                        },
                    IF10_PREMGRL = navKjøptForsikring.premiegrunnlag,
                    IF10_FORSTOM = navKjøptForsikring.opphørsdato.tilInfotrygddato(),
                )
            },
        ifFKonto12er =
            forsikringsvurdering.navKjøpteForsikringer
                .mapIndexedNotNull { indeks, navKjøptForsikring ->
                    if (navKjøptForsikring.erBetaltNoenGang) {
                        lagRåkopiIfFkonto12(
                            IF01_AGNR_FNR = forsikringsvurdering.identitetsnummer.tilInfotrygdFødselsnummer(),
                            IF10_FORSFOM_SEQ = indeks,
                            IF12_BETDATO = navKjøptForsikring.virkningsdato.tilInfotrygddato(),
                        )
                    } else {
                        null
                    }
                },
    )

private fun LocalDate?.tilInfotrygddato(): Int = this?.format(DateTimeFormatter.ofPattern("yyyyMMdd"))?.toInt() ?: 0
