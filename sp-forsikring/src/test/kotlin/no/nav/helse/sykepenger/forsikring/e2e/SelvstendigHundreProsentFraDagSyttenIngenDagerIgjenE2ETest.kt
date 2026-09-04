package no.nav.helse.sykepenger.forsikring.e2e

import no.nav.sykepenger.libs.testing.testdata.jul
import no.nav.sykepenger.libs.testing.testdata.sep
import org.junit.jupiter.api.Test

class SelvstendigHundreProsentFraDagSyttenIngenDagerIgjenE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `bruker går tom for sykepengedager i siste vedtaksperiode`() {
        brukerenHarEnBetaltForsikringIInfotrygd(
            virkningsdato = 1 jul 2026,
            infotrygdType = '2',
            premiegrunnlag = 12345,
        )
        utbetalingsstatistikkenForIÅrErTom()

        flexSjekkerOmDetErNoeVitsIÅSøkeIVentetiden(forventetSvar = false)

        spleisSenderBehovForForsikringsvurdering()
        val forsikringsvurderingId = detBlirPublisertEnForsikringsvurderingLøsning()
        detBlirPublisertEnSubsumsjonsmeldingForSykefraværstilfellet(
            referansedel =
                """
                "lovverksversjon" : "2019-10-01",
                "paragraf" : "8-36",
                "ledd" : 1,
                "bokstav" : "b"
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
                "iVentetid" : false
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
                      "bokstav" : "b",
                      "kapittel" : 8,
                      "ledd" : 1,
                      "paragrafIKapittel" : 36
                    },
                    "konklusjon" : {
                      "folketrygdlovenreferanse" : null,
                      "forklaring" : "Lagt til grunn"
                    },
                    "lagtTilGrunn" : true,
                    "navn" : "Selvstendig næringsdrivende 100 % fra 17. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "2026-07-01"
                  } ],
                  "kollektivForsikring" : null,
                  "samletDekning" : {
                    "fraDag" : 17,
                    "grad" : 100
                  }
                }
                """.trimIndent(),
        )

        // Første vedtaksperiode utbetales
        spesialistSenderVedtakFattet(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekning = """{ "dekningsgrad": 100, "gjelderFraDag": 17 }""",
            dekningsgradIVentetid = 80,
            dekningsgradEtterVentetid = 100,
            sykepengegrunnlag = 12345,
            dagbeløpIVentetid = 0,
            dagsbeløpEtterVentetid = 3151,
        )

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 100 % fra 17. dag",
                "totalt" : 6302.0,
                "utbetaltIVentetid" : 0.0,
                "utbetaltUtenomVentetid" : 6302.0
              }
            """.trimIndent(),
        )

        detBlirIkkeOpprettetFlereGosysoppgaver(antallOppgaverTotalt = 0)

        // Andre vedtaksperiode beregnes
        spleisSenderBehovForForsikringsvurderingResultat(andreVedtaksperiode, forsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 100,
                "iVentetid" : false
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
        val ingenDagerIgjenMelding =
            spleisSenderSelvstendigIngenDagerIgjen(
                vedtaksperiode = andreVedtaksperiode,
                forsikringsvurderingId = forsikringsvurderingId,
            )

        detBlirOpprettetEnGosysoppgave(
            uuid = ingenDagerIgjenMelding["@id"].stringValue(),
            forventetBeskrivelse =
                "Årsak: Sykepengerett har opphørt som følge av ingen gjenstående dager. " +
                    "Skjæringstidspunkt: 01.09.2026.",
        )

        spesialistSenderVedtakFattet(
            vedtaksperiode = andreVedtaksperiode,
            forsikringsvurderingId = forsikringsvurderingId,
            dekning = """{ "dekningsgrad": 100, "gjelderFraDag": 17 }""",
            dekningsgradIVentetid = 80,
            dekningsgradEtterVentetid = 100,
            sykepengegrunnlag = 12345,
            dagbeløpIVentetid = 0,
            dagsbeløpEtterVentetid = 3151,
        )

        utbetalingsstatistikkenForIÅrErTomBortsettFra(
            """
            {
                "navn" : "Selvstendig næringsdrivende 100 % fra 17. dag",
                "totalt" : 20166.4,
                "utbetaltIVentetid" : 0.0,
                "utbetaltUtenomVentetid" : 20166.4
              }
            """.trimIndent(),
        )

        detBlirIkkeOpprettetFlereGosysoppgaver(antallOppgaverTotalt = 1)
    }
}
