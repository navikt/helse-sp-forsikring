# Skille vurderingslogikk fra persistens med en tilstandsløs Kalkulator

API-et og BehovRiveren trenger begge å vurdere forsikring, men med ulike krav: API-et skal ikke lagre noe, mens BehovRiveren skal lagre Oppslag og Forsikringsvurdering og inkludere typevalidering og kollektive forsikringer. Vi introduserer `ForsikringsvurderingKalkulator` som en tilstandsløs komponent som håndterer de fire basisfiltrene og dekning-beregningen for NavKjøptForsikringer, og som brukes av begge pather. BehovRiveren utvider Kalkulator-resultatet med typevalidering, kollektive forsikringer og persistens. API-et bruker kun Kalkulator-resultatet direkte.

## Considered Options

Vi vurderte å la API-et kalle `ForsikringsvurderingService` direkte med et "ikke-lagrings-flagg", men det ville spre persistensansvaret inn i servicen og gjøre den vanskeligere å teste og resonnere om. Vi vurderte også én felles service med valgfrie parametere for yrkesaktivitetstype/spesielleYrkesgrupper, men dette ville blande API-spesifikk og river-spesifikk logikk i samme komponent.

## Consequences

`RåForsikring` (domenekartlagt representasjon av IF_VEDFRIVT_10 + IF_FKONTO_12) er input til Kalkulatoren — både API-et og BehovRiveren mapper fra rådata til `RåForsikring` før de kaller Kalkulatoren. BehovRiveren beholder ansvar for Oppslag-lagring, typevalidering (`validerType`), kollektive forsikringer og Forsikringsvurdering-lagring.
