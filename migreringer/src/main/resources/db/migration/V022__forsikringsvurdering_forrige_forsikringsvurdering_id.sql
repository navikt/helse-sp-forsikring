ALTER TABLE forsikringsvurdering
    ADD COLUMN forrige_forsikringsvurdering_id UUID REFERENCES forsikringsvurdering (id) ON DELETE RESTRICT;
