package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikringService
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringService
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.domain.Yrkesaktivitetstype
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiService
import java.time.LocalDate
import javax.sql.DataSource

class ForsikringsvurderingService(
    replikabaseDataSource: DataSource,
) {
    private val råkopiService: RåkopiService = RåkopiService(replikabaseDataSource)
    private val navKjøptForsikringService: NavKjøptForsikringService = NavKjøptForsikringService()
    private val kollektivForsikringService: KollektivForsikringService = KollektivForsikringService()

    fun gjørForsikringsvurdering(
        identitetsnummer: Identitetsnummer,
        yrkesaktivitetstype: Yrkesaktivitetstype,
        spesielleYrkesgrupper: Set<SpesiellYrkesgruppe>,
        skjæringstidspunkt: LocalDate,
    ): Pair<Råkopi, Forsikringsvurdering> {
        // Ta en ny råkopi av data fra replikabasen
        val råkopi = råkopiService.hentNyRåkopi(identitetsnummer)

        // Tolk råkopi til nav-kjøpte forsikringer
        val navKjøpteForsikringer = navKjøptForsikringService.tolkTilNavKjøpteForsikringer(råkopi)
        val kollektiveForsikringer = kollektivForsikringService.utledKollektiveForsikringer(spesielleYrkesgrupper)

        val forsikringsvurdering =
            Forsikringsvurdering.utførVurdering(
                identitetsnummer = identitetsnummer,
                yrkesaktivitetstype = yrkesaktivitetstype,
                spesielleYrkesgrupper = spesielleYrkesgrupper,
                skjæringstidspunkt = skjæringstidspunkt,
                råkopiId = råkopi.id,
                kollektiveForsikringer = kollektiveForsikringer,
                navKjøpteForsikringer = navKjøpteForsikringer,
            )
        return Pair(råkopi, forsikringsvurdering)
    }
}
