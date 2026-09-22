CREATE TABLE endringssjekk_logg
(
    id                            UUID PRIMARY KEY,
    forsikringsvurdering_id       UUID REFERENCES forsikringsvurdering (id) ON DELETE CASCADE,
    tidspunkt                     TIMESTAMP  NOT NULL,
    utfort_av_saksbehandler_ident VARCHAR(7) NOT NULL
);

CREATE INDEX endringssjekk_logg_forsikringsvurdering_id_tidspunkt_idx
    ON endringssjekk_logg (forsikringsvurdering_id, tidspunkt DESC);

