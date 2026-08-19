DO
$$
    BEGIN
        IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sp-forsikring-opprydding-dev')
        THEN
            GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "sp-forsikring-opprydding-dev";
            GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "sp-forsikring-opprydding-dev";
        END IF;
    END
$$;
DO
$$
    BEGIN
        IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sp-forsikring-opprydding-dev')
        THEN
            ALTER DEFAULT PRIVILEGES FOR USER "sp-forsikring" IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "sp-forsikring-opprydding-dev";
            ALTER DEFAULT PRIVILEGES FOR USER "sp-forsikring" IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "sp-forsikring-opprydding-dev";
        END IF;
    END
$$;
