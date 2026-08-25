-- "Nav-kjøpt forsikring" har byttet navn til "individuell forsikring".
-- Tabeller, kolonner, constraints og lagrede verdier døpes om tilsvarende.

ALTER TABLE forsikringsvurdering_navkjøpt_forsikring
    RENAME TO forsikringsvurdering_individuell_forsikring;

-- Constraintene på tabellen fikk autogenererte navn av Postgres, og navnene er trunkert til 63 byte.
-- Vi slår dem derfor opp i katalogen framfor å skrive dem inn manuelt.
DO
$$
    DECLARE
        c RECORD;
    BEGIN
        FOR c IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'forsikringsvurdering_individuell_forsikring'::regclass
              AND conname LIKE '%navkjøpt%'
            LOOP
                EXECUTE format(
                        'ALTER TABLE forsikringsvurdering_individuell_forsikring RENAME CONSTRAINT %I TO %I',
                        c.conname,
                        replace(c.conname, 'navkjøpt', 'individuell')
                        );
            END LOOP;
    END
$$;

ALTER TABLE utbetaling_per_forsikringstype
    RENAME COLUMN navkjøpt_forsikring_type TO individuell_forsikring_type;

UPDATE forsikringsvurdering
SET forsikringskategori = 'INDIVIDUELL'
WHERE forsikringskategori = 'NAVKJØPT';
