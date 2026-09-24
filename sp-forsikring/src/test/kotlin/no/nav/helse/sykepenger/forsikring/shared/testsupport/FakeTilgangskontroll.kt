package no.nav.helse.sykepenger.forsikring.shared.testsupport

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat

class FakeTilgangskontroll(
    var resultat: TilgangskontrollResultat = TilgangskontrollResultat.Ok,
) : PopulasjonstilgangskontrollProvider {
    var antallKall = 0

    override fun kontrollerKomplettTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat {
        antallKall++
        return resultat
    }

    override fun kontrollerKjerneTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = resultat

    override fun kontrollerKjerneTilgangForAnsatt(
        ansattId: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = resultat
}
