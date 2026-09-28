package no.nav.helse.sykepenger.forsikring.e2e

import no.nav.sykepenger.libs.testing.testdata.jul
import no.nav.sykepenger.libs.testing.testdata.sep
import org.junit.jupiter.api.Test

class SelvstendigBetalerForsikringenEtterVurderingE2ETest :
    AbstractE2ETest(
        yrkesaktivitetstype = "SELVSTENDIG",
        skjæringstidspunkt = 1 sep 2026,
    ) {
    @Test
    fun `forsikringen blir betalt mellom to endringssjekker, og saksbehandler fatter vedtak på den nye vurderingen`() {
        val virkningsdato = 1 jul 2026
        val forsfomSeq =
            brukerenHarEnUbetaltForsikringIInfotrygd(
                virkningsdato = virkningsdato,
                infotrygdType = '3',
                premiegrunnlag = 12345,
            )
        utbetalingsstatistikkenForIÅrErTom()

        flexSjekkerOmDetErNoeVitsIÅSøkeIVentetiden(forventetSvar = true)

        spleisSenderBehovForForsikringsvurdering()
        val førsteForsikringsvurderingId = detBlirPublisertEnForsikringsvurderingLøsning()

        // Første vedtaksperiode beregnes, men forsikringen er ikke betalt og gir derfor ingen dekning
        spleisSenderBehovForForsikringsvurderingResultat(førsteVedtaksperiode, førsteForsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : null,
              "forsikringsvurderingId" : "$førsteForsikringsvurderingId",
              "harForsikring" : false,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : false,
              "opphørsdato" : null,
              "villeHattForsikringOmDenVarBetalt" : true
            }
            """.trimIndent(),
        )

        saksbehandlerSjekkerForsikringsvurderingISpeil(
            forsikringsvurderingId = førsteForsikringsvurderingId,
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
                      "forklaring" : "Forsikringen er innvilget, men ikke betalt ennå"
                    },
                    "lagtTilGrunn" : false,
                    "navn" : "Selvstendig næringsdrivende 100 % fra 1. dag",
                    "opphørsdato" : null,
                    "virkningsdato" : "2026-07-01"
                  } ],
                  "kollektivForsikring" : null,
                  "samletDekning" : null,
                  "sistHentet": null
                }
                """.trimIndent(),
        )

        // Saksbehandleren venter med å fatte vedtak, og sjekker om noe har endret seg i Infotrygd. Det har det ikke.
        saksbehandlerGjørEndringssjekkISpeil(
            saksbehandlerIdent = "Z999999",
            forventetVurderingErEndret = false,
        )

        // Brukeren betaler forsikringen, og da gir den plutselig dekning
        brukerenBetalerForsikringenSinIInfotrygd(forsfomSeq = forsfomSeq, virkningsdato = virkningsdato)

        saksbehandlerGjørEndringssjekkISpeil(
            saksbehandlerIdent = "Z999999",
            forventetVurderingErEndret = true,
        )

        val andreForsikringsvurderingId = detBlirPublisertEnEndretForsikringsvurderingMelding()
        detBlirPublisertEnSubsumsjonsmeldingForSykefraværstilfellet(
            referansedel =
                """
                "lovverksversjon" : "2019-10-01",
                "paragraf" : "8-36",
                "ledd" : 1,
                "bokstav" : "c"
                """.trimIndent(),
            forsikringsvurderingId = andreForsikringsvurderingId,
        )

        // Spleis beregner vedtaksperioden på nytt, nå med den nye forsikringsvurderingen
        spleisSenderBehovForForsikringsvurderingResultat(førsteVedtaksperiode, andreForsikringsvurderingId)
        detBlirProdusertEnForsikringsvurderingResultatLøsning(
            """
            {
              "dekning" : {
                "grad" : 100,
                "iVentetid" : true
              },
              "forsikringsvurderingId" : "$andreForsikringsvurderingId",
              "harForsikring" : true,
              "harForsikringSomIkkePasserMedSøknadstype" : false,
              "harIndividuellForsikring" : true,
              "opphørsdato" : null,
              "villeHattForsikringOmDenVarBetalt" : false
            }
            """.trimIndent(),
        )

        saksbehandlerSjekkerForsikringsvurderingISpeil(
            forsikringsvurderingId = andreForsikringsvurderingId,
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
                  },
                  "sistHentet": null
                }
                """.trimIndent(),
        )

        // Første vedtaksperiode utbetales
        spleisSenderSelvstendigUtbetaltEtterVentetid(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = andreForsikringsvurderingId,
        )

        spesialistSenderVedtakFattet(
            vedtaksperiode = førsteVedtaksperiode,
            forsikringsvurderingId = andreForsikringsvurderingId,
            dekningsgradEtterVentetid = 100,
            sykepengegrunnlag = 12345,
            utbetalingIVentetid = true,
            dagsbeløp = 3151,
        )

        // Sykepengegrunnlaget er likt premiegrunnlaget, så det er ingenting å varsle om
        detBlirIkkeOpprettetFlereGosysoppgaver(antallOppgaverTotalt = 0)

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
    }
}
