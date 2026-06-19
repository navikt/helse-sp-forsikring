ALTER TABLE forsikringsvurdering
    ADD COLUMN har_forsikring     BOOLEAN,
    ADD COLUMN dekning_i_ventetid BOOLEAN,
    ADD COLUMN dekning_grad       INTEGER;

UPDATE forsikringsvurdering
SET har_forsikring     = (løsning ->> 'harForsikring')::boolean,
    dekning_i_ventetid = CASE
                             WHEN løsning -> 'dekning' IS NOT NULL
                                 THEN (løsning -> 'dekning' ->> 'fraDag')::int = 1
                             ELSE NULL
        END,
    dekning_grad       = CASE
                             WHEN løsning -> 'dekning' IS NOT NULL
                                 THEN (løsning -> 'dekning' ->> 'grad')::int
                             ELSE NULL
        END;

ALTER TABLE forsikringsvurdering
    ALTER COLUMN har_forsikring SET NOT NULL,
    ALTER COLUMN løsning DROP NOT NULL;
