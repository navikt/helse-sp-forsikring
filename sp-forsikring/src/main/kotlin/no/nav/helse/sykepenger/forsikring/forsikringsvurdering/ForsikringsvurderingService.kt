package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.time.LocalDate
import javax.sql.DataSource
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Forsikringsvurdering.EkskluderingNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.kollektiveForsikringerFor
import no.nav.helse.sykepenger.forsikring.loggError
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService

class ForsikringsvurderingService(
    spForsikringTransaction: TransactionalSession,
    replikabaseDataSource: DataSource
) {
    private val oppslagService = OppslagService(spForsikringTransaction, replikabaseDataSource)
    private val forsikringsvurderingRepository = ForsikringsvurderingRepository(spForsikringTransaction)

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
        val forsikringerIOpptjeningstid = navKjøpteForsikringer.filter {
            it.erIOpptjeningstid(skjæringstidspunkt)
        }
        navKjøpteForsikringer.removeAll(forsikringerIOpptjeningstid)
        forsikringerIOpptjeningstid.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, NavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID))
        }

        // Skjæringstidspunkt må være etter eller lik virkningsdato
        val forsikringerMedVirkningsdatoEtterSkjæringstidspunkt = navKjøpteForsikringer.filterNot {
            it.harVirkningPå(skjæringstidspunkt)
        }
        navKjøpteForsikringer.removeAll(forsikringerMedVirkningsdatoEtterSkjæringstidspunkt)
        forsikringerMedVirkningsdatoEtterSkjæringstidspunkt.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, NavKjøptForsikring.Ekskluderingsårsak.VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT))
        }

        // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
        val opphørteForsikringer = navKjøpteForsikringer.filter { forsikring ->
            forsikring.erOpphørtPå(skjæringstidspunkt)
        }
        navKjøpteForsikringer.removeAll(opphørteForsikringer)
        opphørteForsikringer.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, NavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT))
        }

        // Forsikringen må være betalt noen gang
        val ubetalteForsikringer = navKjøpteForsikringer.filterNot(NavKjøptForsikring::erBetaltNoenGang)
        navKjøpteForsikringer.removeAll(ubetalteForsikringer)
        ubetalteForsikringer.forEach {
            ekskluderinger.add(EkskluderingNavKjøptForsikring(it.id, NavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT))
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
                    is NavKjøptForsikring -> "Nav-kjøpt forsikring av type ${it.type}"
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
