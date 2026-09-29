CREATE TABLE outbox
(
    id        bigserial primary key,
    key       text      not null,
    melding   jsonb     not null
);