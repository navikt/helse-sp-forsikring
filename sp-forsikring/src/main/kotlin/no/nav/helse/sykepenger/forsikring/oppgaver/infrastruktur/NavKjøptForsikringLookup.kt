package no.nav.helse.sykepenger.forsikring.oppgaver.infrastruktur

import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringskategori
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.ForsikringsvurderingId
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.domain.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.oppslag.OppslagRepository

internal fun hentNavKjøptForsikring(
    forsikringsvurdering: Forsikringsvurdering,
    oppslagRepository: OppslagRepository,
    forsikringsvurderingId: ForsikringsvurderingId
): NavKjøptForsikring {
    val navKjøptForsikringId = (forsikringsvurdering.forsikringskategori as? Forsikringskategori.NavKjøptForsikring)?.id
        ?: error("Forventet NavKjøptForsikring, men fikk ${forsikringsvurdering.forsikringskategori}")

    val oppslag = oppslagRepository.hent(forsikringsvurdering.oppslagId)
    return oppslag.navKjøpteForsikringer.find { it.id == navKjøptForsikringId }
        ?: error("Fant ikke NavKjøptForsikring for forsikringsvurderingId=$forsikringsvurderingId")
}
