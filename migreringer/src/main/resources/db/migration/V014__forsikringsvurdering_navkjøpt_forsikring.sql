-- Lagrer hele den vurderte nav-kjøpte forsikringen (VurdertNavKjøptForsikring), ikke bare
-- ekskluderingene. Da slipper vi å tolke råkopiraden på nytt ved lesing, og den historiske
-- vurderingen bevares i sin helhet.
--
-- Eksisterende data bygges opp fra råkopiradene, med konklusjon hentet fra
-- forsikringsvurdering_ekskludering_navkjopt_forsikring (GYLDIG der det ikke finnes en ekskludering).
-- Ekskluderingstabellen erstattes av den nye tabellen og fjernes til slutt.

CREATE TABLE forsikringsvurdering_navkjøpt_forsikring
(
    forsikringsvurdering_id  UUID    NOT NULL REFERENCES forsikringsvurdering (id),
    råkopi_IF_VEDFRIVT_10_id UUID    NOT NULL REFERENCES råkopi_IF_VEDFRIVT_10 (id),
    PRIMARY KEY (forsikringsvurdering_id, råkopi_IF_VEDFRIVT_10_id),

    type                     TEXT    NOT NULL,
    virkningsdato            DATE    NOT NULL,
    opphører                 BOOLEAN NOT NULL,
    opphørsdato              DATE,
    premiegrunnlag           INTEGER NOT NULL,
    er_betalt_noen_gang      BOOLEAN NOT NULL,
    konklusjon               TEXT    NOT NULL
);

INSERT INTO forsikringsvurdering_navkjøpt_forsikring (forsikringsvurdering_id,
                                                      råkopi_IF_VEDFRIVT_10_id,
                                                      type,
                                                      virkningsdato,
                                                      opphører,
                                                      opphørsdato,
                                                      premiegrunnlag,
                                                      er_betalt_noen_gang,
                                                      konklusjon)
SELECT f.id,
       v.id,
       CASE v.IF10_TYPE
           WHEN '1' THEN 'SELVSTENDIG_80_PROSENT_FRA_DAG_1'
           WHEN '2' THEN 'SELVSTENDIG_100_PROSENT_FRA_DAG_17'
           WHEN '3' THEN 'SELVSTENDIG_100_PROSENT_FRA_DAG_1'
           WHEN '4' THEN 'SELVSTENDIG_JORDBRUKER_100_PROSENT_FRA_DAG_1'
           WHEN '5' THEN 'FRILANSER_100_PROSENT_FRA_DAG_1'
           END,
       to_date(lpad(v.IF10_VIRKDATO::text, 8, '0'), 'YYYYMMDD'),
       NULLIF(v.IF10_FORSTOM, 0) IS NOT NULL OR btrim(v.IF10_OPPHGR) <> '',
       to_date(lpad(NULLIF(v.IF10_FORSTOM, 0)::text, 8, '0'), 'YYYYMMDD'),
       v.IF10_PREMGRL::int,
       EXISTS (SELECT 1
               FROM råkopi_IF_FKONTO_12 k
               WHERE k.råkopi_IF_VEDFRIVT_10_id = v.id
                 AND NULLIF(k.IF12_BETDATO, 0) IS NOT NULL),
       COALESCE(e.ekskluderingsaarsak, 'GYLDIG')
FROM forsikringsvurdering f
         JOIN råkopi_IF_VEDFRIVT_10 v ON v.råkopi_id = f.råkopi_id
         LEFT JOIN forsikringsvurdering_ekskludering_navkjopt_forsikring e
                   ON e.forsikringsvurdering_id = f.id AND e.råkopi_IF_VEDFRIVT_10_id = v.id;

DROP TABLE forsikringsvurdering_ekskludering_navkjopt_forsikring;
