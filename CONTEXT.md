# SP Forsikring

Vurderer om en selvstendig næringsdrivende eller frilanser har gyldig Nav-kjøpt eller kollektiv sykepengeforsikring på et gitt skjæringstidspunkt.

## Language

### Forsikring

**NavKjøptForsikring**:
En individuell forsikring kjøpt av Nav på vegne av en person, hentet fra IF_VEDFRIVT_10 i Infotrygd-replikabasen.
_Unngå_: forsikringsavtale, polise

**KollektivForsikring**:
En kollektiv forsikringsdekning som gjelder automatisk for bestemte spesielle yrkesgrupper (Fisker Blad B, Jordbruker, Reindrifter) — uten at den er registrert i IF_VEDFRIVT_10.
_Unngå_: gruppepolise

**RåForsikring**:
En domenekartlagt representasjon av en NavKjøptForsikring direkte fra replikabasen, med rene Kotlin-feltnavn og uten tilknytning til et Oppslag. Brukes som input til ForsikringsvurderingKalkulator.
_Unngå_: IF_VEDFRIVT_10_Rad (som input til domenelogikk)

**Betaling**:
En betalingsperiode fra IF_FKONTO_12, med `fom`, `tom` og `betdato`. En RåForsikring er betalt for en periode dersom tilhørende Betaling har en ikke-null `betdato`.
_Unngå_: konto, fkonto-rad

### Vurdering

**Forsikringsvurdering**:
Det lagrede resultatet av en fullstendig forsikringsvurdering — inkludert oppslagId, behovJson, løsning og ekskluderinger. Produseres av BehovRiveren og lagres persistent.
_Unngå_: forsikringsresultat

**KalkulatorResultat**:
Det ikke-lagrede resultatet fra ForsikringsvurderingKalkulator: de filtrerte RåForsikringene som passerte basisfiltrene, samt beregnet dekning.
_Unngå_: vurderingsresultat, resultat

**Dekning**:
Forsikringsdekning uttrykt som grad (prosent) og fraDag (hvilken dag dekningen starter).
_Unngå_: dekningsgrad, forsikringsomfang

**Skjæringstidspunkt**:
Datoen forsikringen vurderes mot — dvs. datoen sykefraværet starter.

**erBetaltNoenGang**:
Boolsk verdi som angir om en NavKjøptForsikring noen gang har hatt en betaling registrert (minst én Betaling med ikke-null betdato). Brukes som basisfilter i ForsikringsvurderingKalkulator.

**erBetaltForSkjæringstidspunkt**:
Boolsk verdi som angir om en NavKjøptForsikring har en betalt periode som dekker skjæringstidspunktet (Betaling med betdato satt og fom ≤ skjæringstidspunkt ≤ tom). Returneres kun i API-responsen — er ikke et filter.
_Unngå_: erBetaltNoenGang (disse er forskjellige konsepter)

**Ekskludering**:
En begrunnelse for hvorfor en NavKjøptForsikring ble utelatt fra vurderingen (f.eks. ALDRI_BETALT, OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT).

### Infrastruktur og flyt

**Oppslag**:
En lagret kopi av rådata fra Infotrygd-replikabasen for én person på ett tidspunkt, med tilhørende NavKjøptForsikringer. Opprettes og lagres av BehovRiveren — aldri av API-en.
_Unngå_: snapshot, cache

**ForsikringsvurderingKalkulator**:
En tilstandsløs komponent som tar `List<RåForsikring>` og `skjæringstidspunkt`, anvender de fire basisfiltrene og beregner dekning. Har ingen sideeffekter og lagrer ingenting.
_Unngå_: ForsikringsvurderingService (som er en annen komponent med sideeffekter)

**BehovRiver**:
Rapids & Rivers-lytteren som mottar `Forsikringsvurdering`-behov fra Spleis, utfører en fullstendig vurdering (inkl. typevalidering og kollektive forsikringer), lagrer Oppslag og Forsikringsvurdering, og publiserer løsning.

**SpesiellYrkesgruppe**:
En yrkesgruppe med særskilt forsikringsdekning: Fisker (Blad B), Jordbruker eller Reindrifter.

**Yrkesaktivitetstype**:
Typen næringsaktivitet personen driver: SELVSTENDIG eller FRILANS. Brukes av BehovRiveren til å validere at NavKjøptForsikringens type samsvarer.

---

## Example dialogue

> **Dev**: Når Speil spør API-et om noen har forsikring, lagrer vi da et Oppslag?
>
> **Domeneekspert**: Nei — API-et bruker ForsikringsvurderingKalkulator direkte på RåForsikring fra replikabasen. Ingen Oppslag opprettes.
>
> **Dev**: Men hva med `erBetaltNoenGang`-filteret? Det brukes da også?
>
> **Domeneekspert**: Ja, det er et av de fire basisfiltrene i Kalkulatoren. Men `erBetaltForSkjæringstidspunkt` er noe annet — det er kun informasjon i API-responsen, ikke et filter.
>
> **Dev**: Og kollektive forsikringer for Jordbruker?
>
> **Domeneekspert**: De håndteres bare i BehovRiveren, ikke i API-et. API-et vet ikke om yrkesgrupper — det ser bare på NavKjøptForsikringer fra replikabasen.
