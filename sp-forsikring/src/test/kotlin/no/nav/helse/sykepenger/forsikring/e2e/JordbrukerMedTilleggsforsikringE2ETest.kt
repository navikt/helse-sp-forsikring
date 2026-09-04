package no.nav.helse.sykepenger.forsikring.e2e

import com.github.navikt.tbd_libs.testdata.jul
import com.github.navikt.tbd_libs.testdata.sep
import org.junit.jupiter.api.Test

class JordbrukerMedTilleggsforsikringE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        spesiellYrkesgruppe = "JORDBRUKER",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `happy path`() {
        brukerenHarEnBetaltForsikringIInfotrygd(
            virkningsdato = 1 jul 2026,
            infotrygdType = '4',
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
                "ledd" : 4
                """.trimIndent(),
            forsikringsvurderingId = forsikringsvurderingId,
        )
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
                    "navn" : "Jordbruker tilleggsforsikring 100 % fra 1. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "2026-07-01"
                  } ],
                  "kollektivForsikring" : {
                    "dekningFolketrygdlovenreferanse" : {
                      "bokstav" : "b",
                      "kapittel" : 8,
                      "ledd" : 1,
                      "paragrafIKapittel" : 36
                    },
                    "kollektivFolketrygdlovenreferanse" : {
                      "bokstav" : null,
                      "kapittel" : 8,
                      "ledd" : 4,
                      "paragrafIKapittel" : 36
                    },
                    "navn" : "Jordbruker 100 % fra 17. dag"
                  },
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
              "navn" : "Jordbruker 100 % fra 17. dag",
              "totalt" : 6302.0,
              "utbetaltIVentetid" : 0.0,
              "utbetaltUtenomVentetid" : 6302.0
            }
            """.trimIndent(),
            """
            {
              "navn" : "Jordbruker tilleggsforsikring 100 % fra 1. dag",
              "totalt" : 37812.0,
              "utbetaltIVentetid" : 37812.0,
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
              "navn" : "Jordbruker 100 % fra 17. dag",
              "totalt" : 20166.4,
              "utbetaltIVentetid" : 0.0,
              "utbetaltUtenomVentetid" : 20166.4
            }
            """.trimIndent(),
            """
            {
              "navn" : "Jordbruker tilleggsforsikring 100 % fra 1. dag",
              "totalt" : 37812.0,
              "utbetaltIVentetid" : 37812.0,
              "utbetaltUtenomVentetid" : 0.0
            }
            """.trimIndent(),
        )
    }
}
