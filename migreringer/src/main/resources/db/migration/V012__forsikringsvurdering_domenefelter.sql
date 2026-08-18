-- Løfter feltene i domeneobjektet Forsikringsvurdering inn som egne kolonner.
-- Tidligere måtte identitetsnummer, yrkesaktivitetstype og skjæringstidspunkt hentes ut av
-- behov-JSON-en, og vurdertTidspunkt måtte lånes fra råkopiens lest_tidspunkt.
-- behov-kolonnen beholdes som rå kopi av meldingen som utløste vurderingen.

ALTER TABLE forsikringsvurdering
    ADD COLUMN identitetsnummer    TEXT,
    ADD COLUMN yrkesaktivitetstype TEXT,
    ADD COLUMN skjæringstidspunkt  DATE,
    ADD COLUMN vurdert_tidspunkt   TIMESTAMP,
    ADD COLUMN kollektiv_forsikring TEXT;

UPDATE forsikringsvurdering f
SET identitetsnummer     = f.behov ->> 'fødselsnummer',
    yrkesaktivitetstype  = f.behov ->> 'yrkesaktivitetstype',
    skjæringstidspunkt   = (f.behov -> 'Forsikringsvurdering' ->> 'skjæringstidspunkt')::date,
    vurdert_tidspunkt    = r.lest_tidspunkt,
    kollektiv_forsikring = CASE
                               WHEN f.behov -> 'Forsikringsvurdering' -> 'spesielleYrkesgrupper' @> '["FISKER_BLAD_B"]'
                                   THEN 'FISKER_BLAD_B'
                               WHEN f.behov -> 'Forsikringsvurdering' -> 'spesielleYrkesgrupper' @> '["JORDBRUKER"]'
                                   OR f.behov -> 'Forsikringsvurdering' -> 'spesielleYrkesgrupper' @> '["REINDRIFTER"]'
                                   THEN 'JORDBRUKER'
                               ELSE NULL
        END
FROM råkopi r
WHERE r.id = f.råkopi_id;

ALTER TABLE forsikringsvurdering
    ALTER COLUMN identitetsnummer SET NOT NULL,
    ALTER COLUMN yrkesaktivitetstype SET NOT NULL,
    ALTER COLUMN skjæringstidspunkt SET NOT NULL,
    ALTER COLUMN vurdert_tidspunkt SET NOT NULL;
