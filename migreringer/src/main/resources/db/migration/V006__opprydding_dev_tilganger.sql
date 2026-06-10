DO
$$BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'sp-forsikring-opprydding-dev') THEN
        GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "sp-forsikring-opprydding-dev";
        GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "sp-forsikring-opprydding-dev";
END IF;
END$$;
