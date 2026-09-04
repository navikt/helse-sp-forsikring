package no.nav.helse.sykepenger.forsikring.e2e

import com.github.navikt.tbd_libs.testdata.jul
import com.github.navikt.tbd_libs.testdata.sep
import org.junit.jupiter.api.Test

class SelvstendigHundreProsentFraDagEnE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `happy path`() {
        brukerenHarEnBetaltForsikringIInfotrygd(
            virkningsdato = 1 jul 2026,
            infotrygdType = '3',
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
                "bokstav" : "c"
                """.trimIndent(),
            forsikringsvurderingId = forsikringsvurderingId,
        )

        // Første vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(førsteVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 100,
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
                      "bokstav" : "c",
                      "kapittel" : 8,
                      "ledd" : 1,
                      "paragrafIKapittel" : 36
                    },
                    "konklusjon" : {
                      "folketrygdlovenreferanse" : null,
                      "forklaring" : "Lagt til grunn"
                    },
                    "lagtTilGrunn" : true,
                    "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "2026-07-01"
                  } ],
                  "kollektivForsikring" : null,
                  "samletDekning" : {
                    "fraDag" : 1,
                    "grad" : 100
                  }
                }
                """.trimIndent(),
        )

        // Første vedtaksperiode utbetales
        spesialistSenderVedtakFattet(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekning = """{ "dekningsgrad": 100, "gjelderFraDag": 1 }""",
            dekningsgradIVentetid = 100,
            dekningsgradEtterVentetid = 100,
            sykepengegrunnlag = 12345,
            dagbeløpIVentetid = 3151,
            dagsbeløpEtterVentetid = 3151,
        )

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                "totalt" : 44114.0,
                "utbetaltIVentetid" : 37812.0,
                "utbetaltUtenomVentetid" : 6302.0
              }
            """.trimIndent(),
        )

        // Andre vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(andreVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 100,
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
            dekning = """{ "dekningsgrad": 100, "gjelderFraDag": 1 }""",
            dekningsgradIVentetid = 100,
            dekningsgradEtterVentetid = 100,
            sykepengegrunnlag = 12345,
            dagbeløpIVentetid = 3151,
            dagsbeløpEtterVentetid = 3151,
        )

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                "totalt" : 57978.4,
                "utbetaltIVentetid" : 37812.0,
                "utbetaltUtenomVentetid" : 20166.4
              }
            """.trimIndent(),
        )
    }
}
