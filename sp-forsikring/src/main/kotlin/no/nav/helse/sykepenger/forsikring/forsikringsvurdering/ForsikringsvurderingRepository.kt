package no.nav.helse.sykepenger.forsikring.forsikringsvurdering

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper

class ForsikringsvurderingRepository(private val transaction: TransactionalSession) {
    private val objectMapper = jacksonObjectMapper()

    fun lagre(forsikringsvurdering: Forsikringsvurdering) {
        lagreForsikringsvurdering(forsikringsvurdering)
        forsikringsvurdering.ekskluderinger.forEach { ekskludering ->
            lagreEkskludering(forsikringsvurdering.id, ekskludering)
        }
    }

    private fun lagreForsikringsvurdering(forsikringsvurdering: Forsikringsvurdering) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering (id, oppslag_id, behov, løsning)
            VALUES (:id, :oppslag_id, :behov::jsonb, :losning::jsonb)
        """
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "id" to forsikringsvurdering.id.value,
                    "oppslag_id" to forsikringsvurdering.oppslagId.value,
                    "behov" to forsikringsvurdering.behovJson,
                    "losning" to objectMapper.writeValueAsString(forsikringsvurdering.løsning),
                )
            ).asUpdate
        )
    }

    private fun lagreEkskludering(
        forsikringsvurderingId: ForsikringsvurderingId,
        ekskludering: Forsikringsvurdering.EkskluderingNavKjøptForsikring,
    ) {
        @Language("PostgreSQL")
        val statement = """
            INSERT INTO forsikringsvurdering_ekskludering_navkjopt_forsikring
                (forsikringsvurdering_id, oppslag_IF_VEDFRIVT_10_id, ekskluderingsaarsak)
            VALUES
                (:forsikringsvurdering_id, :oppslag_IF_VEDFRIVT_10_id, :ekskluderingsaarsak)
        """
        transaction.run(
            queryOf(
                statement,
                mapOf(
                    "forsikringsvurdering_id" to forsikringsvurderingId.value,
                    "oppslag_IF_VEDFRIVT_10_id" to ekskludering.oppslagIfVedfrivt10Id.value,
                    "ekskluderingsaarsak" to ekskludering.ekskluderingsårsak.name,
                )
            ).asUpdate
        )
    }
}
