package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import java.time.LocalDate
import javax.sql.DataSource
import kotliquery.TransactionalSession
import no.nav.helse.sykepenger.forsikring.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.RåForsikring
import no.nav.helse.sykepenger.forsikring.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.Forsikringsvurdering.EkskluderingNavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.kalkulator.Dekning
import no.nav.helse.sykepenger.forsikring.kalkulator.ForsikringsvurderingKalkulator
import no.nav.helse.sykepenger.forsikring.kollektiveForsikringerFor
import no.nav.helse.sykepenger.forsikring.loggError
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagService
import no.nav.helse.sykepenger.forsikring.replikabase.ReplikabaseDao
import no.nav.helse.sykepenger.forsikring.replikabase.tilRåForsikringer

class ForsikringsvurderingService(
    spForsikringTransaction: TransactionalSession,
    replikabaseDataSource: DataSource
) {
    private val replikabaseDao = ReplikabaseDao(replikabaseDataSource)
    private val oppslagService = OppslagService(spForsikringTransaction)
    private val forsikringsvurderingRepository = ForsikringsvurderingRepository(spForsikringTransaction)
    private val kalkulator = ForsikringsvurderingKalkulator()

    fun gjørVurdering(
        behovJson: String,
        skjæringstidspunkt: LocalDate,
        fødselsnummer: String,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        yrkesaktivitetstype: Yrkesaktivitetstype
    ): Forsikringsvurdering {
        val rawRader = replikabaseDao.hentIfVedfrivt10Rader(fødselsnummer)
        val råForsikringer = rawRader.tilRåForsikringer()

        val kalkulatorResultat = kalkulator.kalkuler(råForsikringer, skjæringstidspunkt)

        // Kontroller mismatch mellom yrkesaktivitetstype og type forsikring i Infotrygd
        kalkulatorResultat.inkluderteRåForsikringer.forEach {
            it.validerType(yrkesaktivitetstype, spesielleYrkesgrupper)
        }

        val alleForsikringer = kalkulatorResultat.inkluderteRåForsikringer + kollektiveForsikringerFor(spesielleYrkesgrupper)

        if (alleForsikringer.distinctBy { it.dekningGrad() }.size > 1) {
            val message = "Bruker har flere gyldige forsikringer med ulike dekningsgrader"
            loggError(message, "forsikringer" to alleForsikringer.map {
                when (it) {
                    is KollektivForsikring -> "Kollektiv forsikring for ${it.spesiellYrkesgruppe}"
                    is RåForsikring -> "Nav-kjøpt forsikring av type ${it.type}"
                    else -> it.toString()
                }
            })
            error(message)
        }

        val dekning = alleForsikringer.minByOrNull { it.dekningFraDag() }?.let {
            Dekning(grad = it.dekningGrad(), fraDag = it.dekningFraDag())
        }

        // Lagre oppslag og map kalkulator-ekskluderinger til DB-ekskluderinger via posisjon
        val oppslag = oppslagService.gjørNyttOppslag(rawRader)

        val ekskluderinger = kalkulatorResultat.ekskluderinger.map { (råForsikring, årsak) ->
            val indeks = råForsikringer.indexOfFirst { it === råForsikring }
            val navKjøptForsikring = oppslag.navKjøpteForsikringer[indeks]
            EkskluderingNavKjøptForsikring(navKjøptForsikring.id, årsak)
        }

        val forsikringsvurderingId = ForsikringsvurderingId.ny()

        val løsning = dekning?.let {
            Løsning.MedForsikring(forsikringsvurderingId, Løsning.MedForsikring.Dekning(it.grad, it.fraDag))
        } ?: Løsning.UtenForsikring(forsikringsvurderingId)

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

