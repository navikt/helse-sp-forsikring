CREATE TABLE oppslag
(
    id                UUID PRIMARY KEY,
    opprinnelig_behov JSONB     NOT NULL,
    oppslag_tidspunkt TIMESTAMP NOT NULL
)
