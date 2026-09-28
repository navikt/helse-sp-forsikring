ALTER TABLE forsikringsvurdering
    ADD COLUMN vedtaksperiode_id UUID,
    ADD COLUMN behandling_id     UUID;

UPDATE forsikringsvurdering
SET vedtaksperiode_id = (behov ->> 'vedtaksperiodeId')::uuid,
    behandling_id     = (behov ->> 'behandlingId')::uuid;

ALTER TABLE forsikringsvurdering
    ALTER COLUMN vedtaksperiode_id SET NOT NULL,
    ALTER COLUMN behandling_id SET NOT NULL;
