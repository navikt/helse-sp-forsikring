package no.nav.helse.sykepenger.forsikring.e2e

import no.nav.sykepenger.libs.testing.testdata.sep
import org.junit.jupiter.api.Test

class SelvstendigUtenForsikringE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `happy path`() {
        utbetalingsstatistikkenForIÅrErTom()

        flexSjekkerOmDetErNoeVitsIÅSøkeIVentetiden(forventetSvar = false)

        spleisSenderBehovForForsikringsvurdering()
        val forsikringsvurderingId = detBlirPublisertEnForsikringsvurderingLøsning()

        // Første vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(førsteVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : null,
              "forsikringsvurderingId" : "$forsikringsvurderingId",
              "harForsikring" : false,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : false,
              "opphørsdato" : null,
              "villeHattForsikringOmDenVarBetalt" : false
            }
            """.trimIndent(),
        )

        saksbehandlerSjekkerForsikringsvurderingISpeil(
            forsikringsvurderingId = forsikringsvurderingId,
            forventetResponse =
                """
                {
                  "identitetsnummer" : "${testPerson.identitetsnummer}",
                  "individuelleForsikringer" : [ ],
                  "kollektivForsikring" : null,
                  "samletDekning" : null
                }
                """.trimIndent(),
        )

        // Første vedtaksperiode utbetales
        spesialistSenderVedtakFattet(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekningsgradEtterVentetid = 80,
            sykepengegrunnlag = 12345,
            utbetalingIVentetid = false,
            dagsbeløp = 3151,
        )

        utbetalingsstatistikkenForIÅrErTom()

        // Andre vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(andreVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : null,
              "forsikringsvurderingId" : "$forsikringsvurderingId",
              "harForsikring" : false,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : false,
              "opphørsdato" : null,
              "villeHattForsikringOmDenVarBetalt" : false
            }
            """.trimIndent(),
        )

        // Andre vedtaksperiode utbetales
        spesialistSenderVedtakFattet(
            vedtaksperiode = andreVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekningsgradEtterVentetid = 80,
            sykepengegrunnlag = 12345,
            utbetalingIVentetid = false,
            dagsbeløp = 3151,
        )

        utbetalingsstatistikkenForIÅrErTom()
    }
}
