package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.time.LocalDate
import javax.sql.DataSource
import kotlin.collections.any
import kotlin.collections.filter
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO
import no.nav.helse.sykepenger.forsikring.AbstractNavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Forsikringsvurdering.EkskluderingNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.kollektiveForsikringerFor
import no.nav.helse.sykepenger.forsikring.loggError
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.replikabase.mapTilRåNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.toLocalDate

class ForsikringsvurderingService(
    spForsikringTransaction: TransactionalSession,
    replikabaseDataSource: DataSource
) {
    private val replikabaseDao = ReplikabaseDao(dataSource = replikabaseDataSource)
    private val oppslagService = OppslagService(spForsikringTransaction, replikabaseDao)
    private val forsikringsvurderingRepository = ForsikringsvurderingRepository(spForsikringTransaction)

    fun gjørApiVurdering(
        skjæringstidspunkt: LocalDate,
        fødselsnummer: String,
    ) : ApiForsikringsvurdering {
        val forsikringer = replikabaseDao.hentIfVedfrivt10Rader(fødselsnummer).map { it.mapTilRåNavKjøptForsikring(skjæringstidspunkt) }

        val aktuelleForsikringer = forsikringer.filter {
            it.erInnen28DagerFørVirkningsdato(skjæringstidspunkt)
                || it.harVirkningPå(skjæringstidspunkt)
                || it.erOpphørtPå(skjæringstidspunkt)
        }

        val harDekningIVentetid = aktuelleForsikringer.any { it.dekningFraDag() == 1 }
        val erBetalt = aktuelleForsikringer.any { it.erBetalt }

        return ApiForsikringsvurdering(
            harDekningIVentetid = harDekningIVentetid,
            erBetalt = erBetalt
        )
    }

    fun gjørVurdering(
        behovJson: String,
        skjæringstidspunkt: LocalDate,
        fødselsnummer: String,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        yrkesaktivitetstype: Yrkesaktivitetstype
    ): Forsikringsvurdering {
        val oppslag = oppslagService.gjørNyttOppslag(fødselsnummer)

        val navKjøpteForsikringer = oppslag.navKjøpteForsikringer.toMutableList()
        val ekskluderinger = mutableListOf<EkskluderingNavKjøptForsikring>()

        // Skjæringstidspunkt må ikke være i opptjeningstid [IF10_FORSFOM, IF10_VIRKDATO)
       navKjøpteForsikringer.filter {
            it.erInnen28DagerFørVirkningsdato(skjæringstidspunkt)
       }.forEach {
           ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, SKJÆRINGSTIDSPUNKT_INNEN_28_DAGER_FØR_VIRKNINGSDATO))
           navKjøpteForsikringer.remove(it)
       }

        // Skjæringstidspunkt må være etter eller lik virkningsdato
        navKjøpteForsikringer.filterNot {
            it.harVirkningPå(skjæringstidspunkt)
        }.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, SKJÆRINGSTIDSPUNKT_MER_ENN_28_DAGER_FØR_VIRKNINGSDATO))
            navKjøpteForsikringer.remove(it)
        }


        // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
        navKjøpteForsikringer.filter { forsikring ->
            forsikring.erOpphørtPå(skjæringstidspunkt)
        }.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT))
            navKjøpteForsikringer.remove(it)
        }

        // Forsikringen må være betalt noen gang
        navKjøpteForsikringer.filterNot(NavKjøptForsikring::erBetaltNoenGang)
            .forEach {
                ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, ALDRI_BETALT))
                navKjøpteForsikringer.remove(it)
            }

        // Kontroller mismatch mellom yrkesaktivitetstype og type forsikring i Infotrygd
        navKjøpteForsikringer.forEach {
            it.validerType(yrkesaktivitetstype, spesielleYrkesgrupper)
        }

        val alleForsikringer = navKjøpteForsikringer + kollektiveForsikringerFor(spesielleYrkesgrupper)

        val dekninger = alleForsikringer.map {
            Løsning.MedForsikring.Dekning(grad = it.dekningGrad(), fraDag = it.dekningFraDag())
        }

        if (alleForsikringer.distinctBy { it.dekningGrad() }.size > 1) {
            val message = "Bruker har flere gyldige forsikringer med ulike dekningsgrader"
            loggError(message, "forsikringer" to alleForsikringer.map {
                when (it) {
                    is KollektivForsikring -> "Kollektiv forsikring for ${it.spesiellYrkesgruppe}"
                    is AbstractNavKjøptForsikring -> "Nav-kjøpt forsikring av type ${it.type}"
                }
            })
            error(message)
        }

        val forsikringsvurderingId = ForsikringsvurderingId.ny()

        val dekning = dekninger.minByOrNull { it.fraDag }
        val løsning = dekning?.let { Løsning.MedForsikring(forsikringsvurderingId = forsikringsvurderingId, dekning = it) }
            ?: Løsning.UtenForsikring(forsikringsvurderingId = forsikringsvurderingId)

        val forsikringsvurdering = Forsikringsvurdering.ny(
            id = forsikringsvurderingId,
            oppslagId = oppslag.id,
            behovJson = behovJson,
            løsning = løsning,
            ekskluderinger = ekskluderinger,
        )

        forsikringsvurderingRepository.lagre(forsikringsvurdering)

        return forsikringsvurdering
    }
}
