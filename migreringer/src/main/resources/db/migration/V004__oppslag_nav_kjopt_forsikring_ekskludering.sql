CREATE TABLE oppslag_nav_kjopt_forsikring_ekskludering
(
    oppslag_id          UUID        NOT NULL,
    IF01_KODE           CHAR(1)     NOT NULL,
    IF01_AGNR_FNR       NUMERIC(11) NOT NULL,
    IF10_FORSFOM_SEQ    NUMERIC(8)  NOT NULL,
    PRIMARY KEY (oppslag_id, IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ),
    FOREIGN KEY (oppslag_id, IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ)
        REFERENCES oppslag_IF_VEDFRIVT_10 (oppslag_id, IF01_KODE, IF01_AGNR_FNR, IF10_FORSFOM_SEQ),

    ekskluderingsaarsak TEXT        NOT NULL
)
