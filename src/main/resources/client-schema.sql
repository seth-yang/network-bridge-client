CREATE TABLE t_proxy (
    id              TEXT                PRIMARY KEY,
    name            TEXT,
    service_port    INTEGER,
    peer            TEXT,
    peer_port       INTEGER,
    status          TEXT
);