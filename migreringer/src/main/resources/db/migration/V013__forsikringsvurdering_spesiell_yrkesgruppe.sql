-- Spesielle yrkesgrupper er et sett i domenemodellen, og lagres derfor som egne rader
-- i stedet for å måtte tolkes ut av behov-JSON-en ved lesing.

CREATE TABLE forsikringsvurdering_spesiell_yrkesgruppe
(
    forsikringsvurdering_id UUID NOT NULL REFERENCES forsikringsvurdering (id),
    spesiell_yrkesgruppe    TEXT NOT NULL,
    PRIMARY KEY (forsikringsvurdering_id, spesiell_yrkesgruppe)
);

INSERT INTO forsikringsvurdering_spesiell_yrkesgruppe (forsikringsvurdering_id, spesiell_yrkesgruppe)
SELECT f.id, spesiell_yrkesgruppe
FROM forsikringsvurdering f,
     LATERAL jsonb_array_elements_text(
             COALESCE(f.behov -> 'Forsikringsvurdering' -> 'spesielleYrkesgrupper', '[]'::jsonb)
     ) AS spesiell_yrkesgruppe
ON CONFLICT DO NOTHING;
