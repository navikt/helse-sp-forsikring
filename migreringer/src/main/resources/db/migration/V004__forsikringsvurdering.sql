CREATE TABLE forsikringsvurdering
(
    id         UUID PRIMARY KEY,
    oppslag_id UUID  NOT NULL REFERENCES oppslag (id),
    behov      JSONB NOT NULL,
    løsning    JSONB NOT NULL
)
