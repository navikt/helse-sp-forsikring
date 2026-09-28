package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.ForsikringsvurderingInput
import no.nav.helse.sykepenger.forsikring.domain.IndividuellForsikringService
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikringService
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiService
import java.util.*
import javax.sql.DataSource

class ForsikringsvurderingService(
    replikabaseDataSource: DataSource,
) {
    private val råkopiService: RåkopiService = RåkopiService(replikabaseDataSource)
    private val individuellForsikringService: IndividuellForsikringService = IndividuellForsikringService()
    private val kollektivForsikringService: KollektivForsikringService = KollektivForsikringService()

    fun gjørForsikringsvurdering(
        input: ForsikringsvurderingInput,
        vedtaksperiodeId: UUID?,
        behandlingId: UUID?,
        forrigeForsikringsvurderingId: Forsikringsvurdering.Id?,
    ): Pair<Råkopi, Forsikringsvurdering> {
        // Ta en ny råkopi av data fra replikabasen
        val råkopi = råkopiService.hentNyRåkopi(input.identitetsnummer)

        // Tolk råkopi til individuelle forsikringer
        val individuelleForsikringer = individuellForsikringService.tolkTilIndividuelleForsikringer(råkopi)
        val kollektiveForsikringer = kollektivForsikringService.utledKollektiveForsikringer(input.spesielleYrkesgrupper)

        val forsikringsvurdering =
            Forsikringsvurdering.utførVurdering(
                input = input,
                råkopiId = råkopi.id,
                kollektiveForsikringer = kollektiveForsikringer,
                individuelleForsikringer = individuelleForsikringer,
                vedtaksperiodeId = vedtaksperiodeId,
                behandlingId = behandlingId,
                forrigeForsikringsvurderingId = forrigeForsikringsvurderingId,
            )
        return Pair(råkopi, forsikringsvurdering)
    }
}
