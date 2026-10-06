-- Fra-og-med-dato for forsikringen (IF10_FORSFOM). Opptjeningstiden er [fom, virkningsdato).
-- Nullable siden IF10_FORSFOM kan være 0 (ingen dato) i Infotrygd.
ALTER TABLE forsikringsvurdering_individuell_forsikring
    ADD COLUMN fom DATE;

-- Eksisterende vurderinger fylles ut fra råkopiraden de ble vurdert ut fra.
UPDATE forsikringsvurdering_individuell_forsikring f
SET fom = to_date(lpad(NULLIF(v.IF10_FORSFOM, 0)::text, 8, '0'), 'YYYYMMDD')
FROM råkopi_IF_VEDFRIVT_10 v
WHERE v.id = f.råkopi_IF_VEDFRIVT_10_id;
