CREATE TABLE INFOTRYGD_FORSIKRING_OPPSLAG
(
    oppslag_id       UUID PRIMARY KEY,
    løsning          JSONB NOT NULL,
    lagret_tidspunkt TIMESTAMP NOT NULL
)