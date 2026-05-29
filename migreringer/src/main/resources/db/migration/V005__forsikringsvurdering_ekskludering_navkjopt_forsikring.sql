CREATE TABLE forsikringsvurdering_ekskludering_navkjopt_forsikring
(
    forsikringsvurdering_id   UUID NOT NULL REFERENCES forsikringsvurdering (id),
    oppslag_IF_VEDFRIVT_10_id UUID NOT NULL REFERENCES oppslag_IF_VEDFRIVT_10 (id),
    PRIMARY KEY (forsikringsvurdering_id, oppslag_IF_VEDFRIVT_10_id),

    ekskluderingsaarsak       TEXT NOT NULL
)
