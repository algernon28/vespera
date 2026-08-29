-- The ledger's tables: what exists (ADR-040, ADR-041). No migration tool yet (ADR-049) --
-- this file is the whole schema, replayed on every start.

CREATE TABLE IF NOT EXISTS walk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    corpus_root TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS file_occurrence (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    walk_id INTEGER NOT NULL REFERENCES walk (id),
    path TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    last_modified TEXT NOT NULL
);

-- corpus's own table (ADR-041): a walk anomaly is not a verdict, so it is not in the ledger.
CREATE TABLE IF NOT EXISTS walk_anomaly (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    walk_id INTEGER NOT NULL REFERENCES walk (id),
    path_rendering TEXT NOT NULL,
    kind TEXT NOT NULL,
    detail TEXT
);
