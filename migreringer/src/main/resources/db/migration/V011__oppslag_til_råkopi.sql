-- Justerer skjemaet slik at det speiler domenemodellen: «oppslag» heter «råkopi» i koden.
-- Kun omdøping – ingen data flyttes eller endres.

ALTER TABLE oppslag
    RENAME TO råkopi;
ALTER TABLE råkopi
    RENAME COLUMN oppslag_tidspunkt TO lest_tidspunkt;
ALTER TABLE råkopi
    RENAME CONSTRAINT oppslag_pkey TO råkopi_pkey;

ALTER TABLE oppslag_IF_VEDFRIVT_10
    RENAME TO råkopi_IF_VEDFRIVT_10;
ALTER TABLE råkopi_IF_VEDFRIVT_10
    RENAME COLUMN oppslag_id TO råkopi_id;
ALTER TABLE råkopi_IF_VEDFRIVT_10
    RENAME CONSTRAINT oppslag_if_vedfrivt_10_pkey TO råkopi_if_vedfrivt_10_pkey;
ALTER TABLE råkopi_IF_VEDFRIVT_10
    RENAME CONSTRAINT oppslag_if_vedfrivt_10_oppslag_id_fkey TO råkopi_if_vedfrivt_10_råkopi_id_fkey;

ALTER TABLE oppslag_IF_FKONTO_12
    RENAME TO råkopi_IF_FKONTO_12;
ALTER TABLE råkopi_IF_FKONTO_12
    RENAME COLUMN oppslag_IF_VEDFRIVT_10_id TO råkopi_IF_VEDFRIVT_10_id;
ALTER TABLE råkopi_IF_FKONTO_12
    RENAME CONSTRAINT oppslag_if_fkonto_12_pkey TO råkopi_if_fkonto_12_pkey;
ALTER TABLE råkopi_IF_FKONTO_12
    RENAME CONSTRAINT oppslag_if_fkonto_12_oppslag_if_vedfrivt_10_id_fkey TO råkopi_if_fkonto_12_råkopi_if_vedfrivt_10_id_fkey;

ALTER TABLE forsikringsvurdering
    RENAME COLUMN oppslag_id TO råkopi_id;
ALTER TABLE forsikringsvurdering
    RENAME COLUMN oppslag_IF_VEDFRIVT_10_id TO råkopi_IF_VEDFRIVT_10_id;
ALTER TABLE forsikringsvurdering
    RENAME CONSTRAINT forsikringsvurdering_oppslag_id_fkey TO forsikringsvurdering_råkopi_id_fkey;
ALTER TABLE forsikringsvurdering
    RENAME CONSTRAINT forsikringsvurdering_oppslag_if_vedfrivt_10_id_fkey TO forsikringsvurdering_råkopi_if_vedfrivt_10_id_fkey;

ALTER TABLE forsikringsvurdering_ekskludering_navkjopt_forsikring
    RENAME COLUMN oppslag_IF_VEDFRIVT_10_id TO råkopi_IF_VEDFRIVT_10_id;
ALTER TABLE forsikringsvurdering_ekskludering_navkjopt_forsikring
    RENAME CONSTRAINT forsikringsvurdering_ekskluderin_oppslag_if_vedfrivt_10_id_fkey TO fv_ekskludering_råkopi_if_vedfrivt_10_id_fkey;
