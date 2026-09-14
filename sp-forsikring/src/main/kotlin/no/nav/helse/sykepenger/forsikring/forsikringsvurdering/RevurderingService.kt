package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Revurderer en tidligere forsikringsvurdering på grunnlag av gjeldende data i Infotrygd, og lagrer
 * en ny vurdering dersom utfallet er endret.
 */
internal class RevurderingService(
    private val spForsikringDataSource: DataSource,
    private val forsikringsvurderingService: ForsikringsvurderingService,
) {
    fun revurder(
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
        behovJson: (forrigeForsikringsvurdering: Forsikringsvurdering) -> String,
    ): Revurderingsresultat =
        spForsikringDataSource.inTransaction { transactionalSession ->
            val repository = ForsikringsvurderingRepository(transactionalSession)
            val sisteForsikringsvurdering =
                repository.finn(
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                ) ?: return@inTransaction Revurderingsresultat.IngenTidligereVurdering

            // Yrkesaktivitetstype og spesielle yrkesgrupper er ikke en del av forespørselen, og arves derfor
            // fra den forrige vurderingen. Vi revurderer altså kun på grunnlag av endringer i Infotrygd.
            val (råkopi, nyVurdering) =
                forsikringsvurderingService.gjørForsikringsvurdering(
                    identitetsnummer = identitetsnummer,
                    yrkesaktivitetstype = sisteForsikringsvurdering.yrkesaktivitetstype,
                    spesielleYrkesgrupper = sisteForsikringsvurdering.spesielleYrkesgrupper,
                    skjæringstidspunkt = skjæringstidspunkt,
                )

            if (sisteForsikringsvurdering.harSammeUtfallSom(nyVurdering)) {
                return@inTransaction Revurderingsresultat.UendretVurdering
            }

            // Råkopien må lagres før vurderingen, siden vurderingen peker på den med fremmednøkler
            RåkopiRepository(transactionalSession).lagre(råkopi)
            repository.lagre(nyVurdering, behovJson(sisteForsikringsvurdering))

            Revurderingsresultat.EndretVurdering(nyVurdering)
        }
}

internal sealed interface Revurderingsresultat {
    data object IngenTidligereVurdering : Revurderingsresultat

    data object UendretVurdering : Revurderingsresultat

    data class EndretVurdering(
        val forsikringsvurdering: Forsikringsvurdering,
    ) : Revurderingsresultat
}
