ALTER TABLE forsikringsvurdering
    ADD COLUMN oppslag_IF_VEDFRIVT_10_id UUID REFERENCES oppslag_IF_VEDFRIVT_10 (id),
    ADD COLUMN forsikringskategori       TEXT;