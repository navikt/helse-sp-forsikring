package no.nav.helse.sykepenger.forsikring.e2e

import no.nav.sykepenger.libs.testing.testdata.jul
import no.nav.sykepenger.libs.testing.testdata.sep
import org.junit.jupiter.api.Test

class SelvstendigÅttiProsentFraDagEnE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `happy path`() {
        brukerenHarEnBetaltForsikringIInfotrygd(
            virkningsdato = 1 jul 2026,
            infotrygdType = '1',
            premiegrunnlag = 12345,
        )
        utbetalingsstatistikkenForIÅrErTom()

        flexSjekkerOmDetErNoeVitsIÅSøkeIVentetiden(forventetSvar = true)

        spleisSenderBehovForForsikringsvurdering()
        val forsikringsvurderingId = detBlirPublisertEnForsikringsvurderingLøsning()
        detBlirPublisertEnSubsumsjonsmeldingForSykefraværstilfellet(
            referansedel =
                """
                "lovverksversjon" : "2019-10-01",
                "paragraf" : "8-36",
                "ledd" : 1,
                "bokstav" : "a"
                """.trimIndent(),
            forsikringsvurderingId = forsikringsvurderingId,
        )

        // Første vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(førsteVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 80,
                "iVentetid" : true
              },
              "forsikringsvurderingId" : "$forsikringsvurderingId",
              "harForsikring" : true,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : true,
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
                  "individuelleForsikringer" : [ {
                    "dekningFolketrygdlovenreferanse" : {
                      "bokstav" : "a",
                      "kapittel" : 8,
                      "ledd" : 1,
                      "paragrafIKapittel" : 36
                    },
                    "konklusjon" : {
                      "folketrygdlovenreferanse" : null,
                      "forklaring" : "Lagt til grunn"
                    },
                    "lagtTilGrunn" : true,
                    "navn" : "Selvstendig næringsdrivende 80 % fra 1. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "2026-07-01"
                  } ],
                  "kollektivForsikring" : null,
                  "samletDekning" : {
                    "fraDag" : 1,
                    "grad" : 80
                  }
                }
                """.trimIndent(),
        )

        // Første vedtaksperiode utbetales
        val selvstendigUtbetaltEtterVentetidMelding =
            spleisSenderSelvstendigUtbetaltEtterVentetid(
                vedtaksperiode = førsteVedtaksperiode,
                forsikringsvurderingId = forsikringsvurderingId,
            )

        detBlirOpprettetEnGosysoppgave(
            uuid = selvstendigUtbetaltEtterVentetidMelding["@id"].stringValue(),
            forventetBeskrivelse =
                "Bruker har forsikring som kun gir tilleggsykepenger i ventetid," +
                    " og har fått utbetalt sykepenger utover ventetid." +
                    " Utbetalingen skjedde for sykefravær med skjæringstidspunkt 01.09.2026.",
        )

        spesialistSenderVedtakFattet(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekningsgradEtterVentetid = 80,
            sykepengegrunnlag = 12345,
            utbetalingIVentetid = true,
            dagsbeløp = 2521,
        )

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 80 % fra 1. dag",
                "totalt" : 30252.0,
                "utbetaltIVentetid" : 30252.0,
                "utbetaltUtenomVentetid" : 0.0
              }
            """.trimIndent(),
        )

        // Andre vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(andreVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 80,
                "iVentetid" : true
              },
              "forsikringsvurderingId" : "$forsikringsvurderingId",
              "harForsikring" : true,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : true,
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
            utbetalingIVentetid = true,
            dagsbeløp = 2521,
        )

        detBlirIkkeOpprettetFlereGosysoppgaver(antallOppgaverTotalt = 1)

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 80 % fra 1. dag",
                "totalt" : 30252.0,
                "utbetaltIVentetid" : 30252.0,
                "utbetaltUtenomVentetid" : 0.0
              }
            """.trimIndent(),
        )
    }
}
