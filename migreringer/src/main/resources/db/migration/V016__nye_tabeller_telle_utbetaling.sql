DROP table if exists tell_utbetaling;

CREATE TABLE vedtak_fattet_melding
(
    id                      UUID PRIMARY KEY,
    forsikringsvurdering_id UUID NULL REFERENCES forsikringsvurdering (id),
    identitetsnummer        TEXT      NOT NULL,
    behandling_id           UUID      NOT NULL,
    vedtak_fattet_tidspunkt TIMESTAMP NOT NULL,
    json                    JSONB     NOT NULL
);

CREATE TABLE utbetaling_per_forsikringstype
(
    id                        UUID PRIMARY KEY,
    utbetalt_i_ventetid       INT       NOT NULL,
    utbetalt_utenom_ventetid  INT       NOT NULL,
    vedtak_fattet_melding_id  UUID      NOT NULL REFERENCES vedtak_fattet_melding (id),
    kollektiv_forsikring_type TEXT NULL,
    navkjøpt_forsikring_type  TEXT NULL,
    CONSTRAINT utbetaling_per_forsikringstype_exactly_one_notnull_type CHECK (num_nonnulls(kollektiv_forsikring_type, navkjøpt_forsikring_type) = 1),
    CONSTRAINT utbetaling_per_forsikringstype_unik_type_per_melding UNIQUE NULLS NOT DISTINCT (vedtak_fattet_melding_id, kollektiv_forsikring_type, navkjøpt_forsikring_type)
);