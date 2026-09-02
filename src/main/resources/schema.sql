-- The schema, replayed on every start. No migration tool yet (ADR-049), so every statement is
-- CREATE ... IF NOT EXISTS and this file is the whole schema rather than a step towards it.
--
-- One consequence worth knowing before it bites: IF NOT EXISTS never alters a table that already
-- exists, so a column added here does not appear in a database created before it. That is the case
-- ADR-059's schema_version catches, and its manual upgrade path is to delete the mismatched
-- module's tables and re-run census -- none of ledger, corpus or profile holds anything
-- irreplaceable yet.

-- One row per module, each checked and refused independently (ADR-059). Owned by ledger, because
-- every module depends on ledger and the check itself lives there.
CREATE TABLE IF NOT EXISTS schema_version (
    module TEXT PRIMARY KEY,
    version INTEGER NOT NULL
);

-- A walk owns file occurrences: they are filesystem observations, not derivations (ADR-048).
--
-- root, not corpus_root: the instrument generalises, and a seed folder is walked by the same
-- machinery under its own walk id (ADR-064).
--
-- The remaining four columns are one decision each. finished plus checkpoint_ordinals /
-- checkpoint_path make a walk resumable under its own id (ADR-055): an unfinished walk over a root
-- is continued, never discarded, and the checkpoint is what lets the resumed walk skip whole
-- completed subtrees instead of re-stat'ing them. entries_seen and directories_entered are the
-- cumulative counts the excludes-nothing reconciliation checks at finish (ADR-056); they are
-- cumulative across resume sessions, which is why they live on the row rather than in memory.
CREATE TABLE IF NOT EXISTS walk (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    root TEXT NOT NULL,
    finished INTEGER NOT NULL DEFAULT 0,
    checkpoint_ordinals TEXT,
    checkpoint_path TEXT,
    entries_seen INTEGER NOT NULL DEFAULT 0,
    directories_entered INTEGER NOT NULL DEFAULT 0
);

-- UNIQUE (walk_id, path) is ADR-051 as a constraint: within one walk a path identifies exactly one
-- file occurrence. It is also what makes a resumed walk safe to get wrong loudly -- re-recording an
-- entry a previous session already recorded fails here rather than silently doubling the corpus.
--
-- creation_time is ADR-069's: last_modified is a last-write time, unreliable for stage 1's
-- duplicate-resolution rule because it reflects copy-tool behaviour rather than content history.
CREATE TABLE IF NOT EXISTS file_occurrence (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    walk_id INTEGER NOT NULL REFERENCES walk (id),
    path TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    last_modified TEXT NOT NULL,
    creation_time TEXT NOT NULL,
    UNIQUE (walk_id, path)
);

-- A run owns verdict rows: they are derived under a configuration, where an occurrence is observed
-- (ADR-048). id is the hash of the four things that determine what the run would produce, so a run
-- that would produce identical output has an identical identity.
--
-- Nothing in the census slice writes here. The table exists because stage 1 is the next slice and
-- the pom carries what a recorded decision requires ahead of the code that uses it (ADR-046).
CREATE TABLE IF NOT EXISTS run (
    id TEXT PRIMARY KEY,
    stage TEXT NOT NULL,
    implementation_version TEXT NOT NULL,
    config_consumed TEXT NOT NULL,
    walk_id INTEGER NOT NULL REFERENCES walk (id)
);

-- A run's upstream runs, as rows rather than a delimited column so the chain stays queryable.
CREATE TABLE IF NOT EXISTS run_upstream (
    run_id TEXT NOT NULL REFERENCES run (id),
    upstream_run_id TEXT NOT NULL REFERENCES run (id),
    PRIMARY KEY (run_id, upstream_run_id)
);

-- The verdict row is generic regardless of kind (ADR-057): a kind from the closed vocabulary plus
-- free-text reason, mirroring walk_anomaly's detail. The occurrence reference a superseded-by
-- verdict needs belongs to corpus's own content_hash/superseded_by tables below (ADR-067, ADR-069);
-- the score a below-threshold verdict needs will belong to embedding's own table. Both join back by
-- occurrence and run (ADR-041) -- which is what keeps this shape unchanged when embedding arrives.
CREATE TABLE IF NOT EXISTS verdict (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    kind TEXT NOT NULL,
    reason TEXT
);

-- The survivors query is an anti-join over this column (ADR-060), and it is the one query in the
-- system that runs once per occurrence per stage.
CREATE INDEX IF NOT EXISTS verdict_by_occurrence ON verdict (occurrence_id, kind);

-- corpus's own table (ADR-041): a walk anomaly is not a verdict, so it is not in the ledger.
CREATE TABLE IF NOT EXISTS walk_anomaly (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    walk_id INTEGER NOT NULL REFERENCES walk (id),
    path_rendering TEXT NOT NULL,
    kind TEXT NOT NULL,
    detail TEXT
);

-- corpus's own table (ADR-067): the SHA-256 of an occurrence's content, computed only for
-- occurrences sharing a size with at least one other survivor of broken (grouping by size first is
-- a free filter -- different sizes can never be identical, so a lone size never pays for a hash).
-- One row per run, since a later run may recompute against a changed implementation.
CREATE TABLE IF NOT EXISTS content_hash (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    sha256 TEXT NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);

-- corpus's own table (ADR-069): which occurrence a superseded occurrence's content identity
-- resolved to -- the representative, chosen by earliest creation_time then lexicographically-
-- lowest path within a content_hash group. The representative itself has no row here.
CREATE TABLE IF NOT EXISTS superseded_by (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    representative_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    PRIMARY KEY (occurrence_id, run_id)
);
