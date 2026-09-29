package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.kafka.EndretForsikringsvurderingPubliserer
import no.nav.helse.sykepenger.forsikring.kafka.OutboxRepositoryFactory
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.subsumsjon.Subsumsjonspubliserer
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.medMdc
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Endringssjekker en tidligere forsikringsvurdering på grunnlag av gjeldende data i Infotrygd, og lagrer
 * en ny vurdering dersom utfallet er endret.
 */
internal class EndringssjekkService(
    private val spForsikringDataSource: DataSource,
    private val forsikringsvurderingService: ForsikringsvurderingService,
    private val subsumsjonspubliserer: Subsumsjonspubliserer,
    private val endretForsikringsvurderingPubliserer: EndretForsikringsvurderingPubliserer,
    private val outboxRepositoryFactory: OutboxRepositoryFactory,
) {
    fun endringssjekk(
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
        saksbehandlerIdent: String,
        requestBody: String,
    ): Endringssjekkresultat =
        spForsikringDataSource.inTransaction { transactionalSession ->
            val repository = ForsikringsvurderingRepository(transactionalSession)
            val endringssjekkLoggDao = EndringssjekkLoggDao(transactionalSession)
            val sisteForsikringsvurdering =
                repository.finn(
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                ) ?: return@inTransaction Endringssjekkresultat.IngenTidligereVurdering

            // Den nye vurderingen gjelder samme vedtaksperiode og behandling som den forrige
            val vedtaksperiodeId =
                requireNotNull(sisteForsikringsvurdering.vedtaksperiodeId) {
                    "Forrige forsikringsvurdering mangler vedtaksperiodeId"
                }
            val behandlingId =
                requireNotNull(sisteForsikringsvurdering.behandlingId) {
                    "Forrige forsikringsvurdering mangler behandlingId"
                }

            medMdc(
                MdcKey.VEDTAKSPERIODE_ID to vedtaksperiodeId.toString(),
                MdcKey.SPLEIS_BEHANDLING_ID to behandlingId.toString(),
            ) {
                // Yrkesaktivitetstype og spesielle yrkesgrupper er ikke en del av forespørselen, og arves derfor
                // fra den forrige vurderingen. Vi gjør altså endringssjekk kun på grunnlag av endringer i Infotrygd.
                val (råkopi, nyVurdering) =
                    forsikringsvurderingService.gjørForsikringsvurdering(
                        input = sisteForsikringsvurdering.input,
                        vedtaksperiodeId = vedtaksperiodeId,
                        behandlingId = behandlingId,
                        forrigeForsikringsvurderingId = sisteForsikringsvurdering.id,
                    )

                endringssjekkLoggDao.insert(sisteForsikringsvurdering.id, saksbehandlerIdent, Instant.now())

                if (sisteForsikringsvurdering.harSammeUtfallSom(nyVurdering)) {
                    return@medMdc Endringssjekkresultat.UendretVurdering
                }

                // Råkopien må lagres før vurderingen, siden vurderingen peker på den med fremmednøkler
                RåkopiRepository(transactionalSession).lagre(råkopi)
                repository.lagre(
                    forsikringsvurdering = nyVurdering,
                    behovEllerRequestBody = requestBody,
                )

                val outboxRepository = outboxRepositoryFactory.lag(transactionalSession)
                endretForsikringsvurderingPubliserer.publiser(
                    outboxRepository = outboxRepository,
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    forsikringsvurderingId = nyVurdering.id.value,
                )
                subsumsjonspubliserer.publiser(
                    outboxRepository = outboxRepository,
                    forsikringsvurdering = nyVurdering,
                    vedtaksperiodeId = vedtaksperiodeId,
                    behandlingId = behandlingId,
                )
                Endringssjekkresultat.EndretVurdering(nyVurdering)
            }
        }
}

internal sealed interface Endringssjekkresultat {
    data object IngenTidligereVurdering : Endringssjekkresultat

    data object UendretVurdering : Endringssjekkresultat

    data class EndretVurdering(
        val forsikringsvurdering: Forsikringsvurdering,
    ) : Endringssjekkresultat
}
