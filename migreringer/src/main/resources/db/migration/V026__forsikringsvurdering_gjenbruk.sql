CREATE TABLE forsikringsvurdering_gjenbruk
(
    id                      UUID PRIMARY KEY,
    forsikringsvurdering_id UUID      NOT NULL REFERENCES forsikringsvurdering (id) ON DELETE CASCADE,
    behov                   JSONB     NOT NULL,
    opprettet               TIMESTAMP NOT NULL
);

CREATE INDEX forsikringsvurdering_rettighetskategori_id_skjaeringstidspunkt_idx
    ON forsikringsvurdering (rettighetskategori_id, skjæringstidspunkt);
