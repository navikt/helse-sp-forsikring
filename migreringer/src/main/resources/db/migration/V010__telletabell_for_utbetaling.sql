CREATE TABLE tell_utbetaling
(
    id                     UUID PRIMARY KEY,
    fødselsnummer          TEXT      NOT NULL,
    vedtaksperiodeId       UUID      NOT NULL,
    vedtakFattetTidspunkt  TIMESTAMP NOT NULL,
    dekningsgrad           INT       NOT NULL,
    harDekningIVentetid    BOOLEAN   NOT NULL,
    utbetaltIVentetid      INT       NOT NULL,
    utbetaltUtenomVentetid INT       NOT NULL,
    json                   JSONB     NOT NULL
);
