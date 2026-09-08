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

-- extraction's own table (ADR-010, ADR-012, ADR-070, ADR-071): a cached Docling response, keyed on
-- content hash plus full extractor identity, so re-running the same content under the same engine
-- never issues a second HTTP call and changing the configured engine mints a new row instead of
-- reusing another engine's output. content_hash here is not a foreign key into corpus's own
-- content_hash table: an occurrence stage 1 never hashed (no size-collision group) is hashed here
-- instead, and the two tables are keyed the same way by coincidence of algorithm, not by reference
-- (ADR-041 -- extraction owns this table, corpus owns its own). status/errors_json/confidence_json
-- are broken out from response_json so a verdict decision can read them without deserialising the
-- whole payload; response_json is the full response body verbatim, which is what makes the cache
-- usable by a later metrics/degeneracy/chunking pass without a second Docling call.
CREATE TABLE IF NOT EXISTS extraction_cache (
    content_hash TEXT NOT NULL,
    extractor_identity TEXT NOT NULL,
    status TEXT NOT NULL,
    errors_json TEXT NOT NULL,
    confidence_json TEXT,
    processing_time REAL NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY (content_hash, extractor_identity)
);

-- extraction's own table (ADR-070, ADR-073, #48): one row per response a document actually came
-- back for -- success, partial_success, or extraction-failed -- and never for a service-scope
-- failure, since no response exists there to measure. Counts are stored, never ratios (#45's user
-- story 29), so a corpus-wide aggregation later can re-derive whatever ratio it needs without
-- losing the denominator. error_summary is a comma-joined list of the response's errors[]
-- categories, free text like verdict.reason, not a re-parse of errors_json -- extraction_cache
-- already owns the response verbatim (ADR-070). page_count is nullable: confidence aggregation and
-- pagination are both properties of the paginated pipeline, absent for the simple one (.docx, .txt).
CREATE TABLE IF NOT EXISTS extraction_metric (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    status TEXT NOT NULL,
    error_summary TEXT,
    parse_score REAL,
    layout_score REAL,
    table_score REAL,
    ocr_score REAL,
    mean_score REAL,
    low_score REAL,
    mean_grade TEXT,
    low_grade TEXT,
    processing_time REAL NOT NULL,
    page_count INTEGER,
    character_count INTEGER NOT NULL,
    alphanumeric_char_count INTEGER NOT NULL,
    word_count INTEGER NOT NULL,
    word_character_length_total INTEGER NOT NULL,
    vowelless_word_count INTEGER NOT NULL,
    single_character_word_count INTEGER NOT NULL,
    primary_language TEXT,
    language_confidence REAL,
    PRIMARY KEY (occurrence_id, run_id)
);

-- extraction's own table (ADR-075): stage 3's corpus-wide distribution of extraction_metric's
-- mean_score, bucketed against QualityGrade's own cut-points (0.5/0.8/0.9) so a bucket boundary here
-- is one an operator already recognises from Docling's own grade. Keyed by stage 3's own run_id, not
-- stage 2's, since this is a measurement about a finished extraction run rather than a row extraction
-- itself wrote (ADR-075's "the module owning the columns it summarizes"). An occurrence whose
-- mean_score is NULL (the .docx/.txt case, where confidence is never computed) contributes to no
-- bucket at all -- there is no fifth "unspecified" row here, since that would be a count of
-- non-measurements rather than a bucket over the distribution. Each stage-3 run writes its own row
-- set under its own run_id and never rewrites an earlier run's (ADR-077, amending ADR-075): a run id
-- already folds in the stage-2 run measured over (ADR-048), so no row is ever stale and the earlier
-- measurements stay queryable -- the same shape shingle_document_frequency uses below. mean_score is
-- the only column distributed here: low_score is deliberately not, and no score_kind column exists to
-- say which score a row summarizes (ADR-078, amending ADR-070 and ADR-075). Tier 2 is a floor on the
-- mean alone, so a worst-page distribution would calibrate no threshold; low_score stays in
-- extraction_metric as re-analyzable data.
CREATE TABLE IF NOT EXISTS confidence_distribution (
    run_id TEXT NOT NULL REFERENCES run (id),
    grade TEXT NOT NULL,
    lower_bound REAL NOT NULL,
    upper_bound REAL NOT NULL,
    document_count INTEGER NOT NULL,
    PRIMARY KEY (run_id, grade)
);

-- extraction's own table (ADR-029, ADR-044, ADR-091): one row per chunk, keyed by content hash plus
-- chunker identity plus chunking-rule identity. ADR-044 required the key carry "tokenizer identity";
-- ADR-091 kept the slot and changed its occupant, because there is no tokenizer here -- the only
-- component that tokenizes is the one that embeds, and what the key has to carry is whatever
-- determines a boundary, which is the budgeting rule. So a re-chunk under a different budget mints
-- its own rows rather than overwriting the previous rule's, and word_count is a count of
-- whitespace-separated words, never of tokens. No chunk_count column exists anywhere (ADR-073): the
-- count is a query over this table, comparable only within one chunker plus rule identity.
CREATE TABLE IF NOT EXISTS chunk_cache (
    content_hash TEXT NOT NULL,
    chunker_identity TEXT NOT NULL,
    chunking_rule_identity TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    word_count INTEGER NOT NULL,
    PRIMARY KEY (content_hash, chunker_identity, chunking_rule_identity, ordinal)
);

-- similarity's own table (ADR-038, ADR-073): raw shingle hashes over extracted text, computed during
-- stage 2's pass (pipeline composes the call; extraction never calls into similarity, ADR-040) so that
-- stage 3's document-frequency boilerplate detection has data to GROUP BY without a second traversal.
-- No MinHash signature here -- that is stage 4's own decision (ADR-018), unblocked by this table rather
-- than pre-empted by it.
--
-- No primary key: a document's own shingle set legitimately repeats a hash (a repeated phrase), and a
-- uniqueness constraint here would silently throw away the repeat count the document-frequency pass
-- needs. shingle_parameter_identity is part of the addressing key precisely so a granularity change
-- (see similarity.ShingleParameters for today's provisional default) mints new rows under a new
-- identity instead of migrating or overwriting the ones already stored.
CREATE TABLE IF NOT EXISTS shingle (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    shingle_parameter_identity TEXT NOT NULL,
    shingle_hash INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS shingle_by_occurrence ON shingle (occurrence_id, run_id, shingle_parameter_identity);

-- Lookup by hash rather than by occurrence, which is what stage 4's containment retrieval needs: for
-- one document's 32 rarest shared shingles, which other documents hold them (ADR-081).
CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash);

-- similarity's own table (ADR-074): stage 3's per-hash document frequency, measured over the shingle
-- rows belonging to stage-2 survivors only (a footer's prevalence among excluded occurrences is not a
-- fact about the corpus stage 4 will actually deduplicate). document_count is
-- COUNT(DISTINCT occurrence_id); total_count is COUNT(*), so a phrase repeated many times inside one
-- document is distinguishable from the same phrase appearing once across many documents.
--
-- ONLY HASHES WITH document_count >= 2 GET A ROW HERE. This omission is itself the fact: an absent
-- hash means exactly one surviving document, never zero -- a reader expecting a complete distribution
-- must not misread a missing row as "never seen." Writing a row for every singleton hash would
-- roughly double the cost of the already-largest table in the database (see the shingle table's own
-- comment) to store a value that carries no corpus-wide signal a boilerplate floor could ever act on.
--
-- One row per stage-3 run: keying on run_id (rather than pointing back at the stage-2 run it was
-- measured over) is enough, because RunId.of already folds the upstream stage-2 run id into stage 3's
-- own identity (ADR-048), so two different stage-2 runs never collide under one stage-3 run id.
CREATE TABLE IF NOT EXISTS shingle_document_frequency (
    run_id TEXT NOT NULL REFERENCES run (id),
    shingle_parameter_identity TEXT NOT NULL,
    shingle_hash INTEGER NOT NULL,
    document_count INTEGER NOT NULL,
    total_count INTEGER NOT NULL,
    PRIMARY KEY (run_id, shingle_parameter_identity, shingle_hash)
);

-- similarity's own table (ADR-074): the denominator a future boilerplate-frequency proportion is
-- computed against -- how many stage-2-surviving occurrences carried at least one shingle row under a
-- given granularity, as of this stage-3 run. Kept apart from shingle_document_frequency rather than
-- folded into it as a sentinel row, since that table's grain is one row per hash, not per corpus.
CREATE TABLE IF NOT EXISTS shingle_corpus_size (
    run_id TEXT NOT NULL REFERENCES run (id),
    shingle_parameter_identity TEXT NOT NULL,
    shingled_document_count INTEGER NOT NULL,
    PRIMARY KEY (run_id, shingle_parameter_identity)
);

-- similarity's own table (ADR-081): one MinHash signature per stage-2 survivor that had any shingle
-- left after boilerplate was stripped (ADR-080). signature is 128 32-bit minima, 512 bytes, computed
-- over the stripped set -- so it stands for what is distinctive about a document rather than for its
-- whole text. A document whose every shingle was boilerplate gets no row at all: it is empty rather
-- than redundant, and comparing an empty set matches everything or nothing depending on how the
-- estimator is written (ADR-080).
--
-- signature_identity spells out what the signature means -- shingle parameter identity, permutation
-- count and seed, boilerplate floor. It is deliberately redundant with run_id, which already folds
-- all three in: a reader holding one of these rows can say what it is without first resolving the
-- run row it belongs to.
CREATE TABLE IF NOT EXISTS minhash_signature (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    signature_identity TEXT NOT NULL,
    signature BLOB NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);

-- similarity's own table (ADR-081): the LSH banding index -- 16 rows per signature, one per band of
-- 8 minima. This is a table rather than an in-memory map so candidate generation is a GROUP BY over
-- an index instead of a pass holding every signature in memory: two documents are near-duplicate
-- candidates when they share a (band_ordinal, band_hash), and the index below is what answers that.
-- Candidates are only candidates -- every pair is then scored exactly from the shingle sets, since
-- signatures retrieve and shingle sets judge (ADR-081).
CREATE TABLE IF NOT EXISTS signature_band (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    band_ordinal INTEGER NOT NULL,
    band_hash INTEGER NOT NULL,
    PRIMARY KEY (occurrence_id, run_id, band_ordinal)
);

CREATE INDEX IF NOT EXISTS signature_band_by_bucket ON signature_band (run_id, band_ordinal, band_hash);

-- similarity's own table (ADR-079, ADR-082): which surviving occurrence a redundant occurrence's
-- content is already published through -- the same shape corpus's superseded_by uses for stage 1
-- (ADR-069), and for the same reason: the occurrence reference a redundant-with verdict needs is a
-- typed column here, never a foreign key encoded into verdict.reason (ADR-041). The survivor itself
-- has no row.
--
-- relation is 'near-duplicate' or 'contained-in', and score is the exact Jaccard or containment that
-- produced the verdict, computed from the shingle sets rather than estimated from the signatures.
-- Storing it is what replaces the corpus-wide distribution report stage 4 deliberately does not ship
-- (ADR-082): an operator auditing a removal asks why this document went, which is one join.
--
-- The candidate pairs LSH generated are not stored anywhere. Most of a candidate list exists to be
-- rejected by exact scoring, it would be the largest table in the database, and it is not a
-- measurement (ADR-082).
CREATE TABLE IF NOT EXISTS redundant_with (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    redundant_with_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    relation TEXT NOT NULL,
    score REAL NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);

-- embedding's own table (ADR-083): a seed document that produced no text, recorded as data rather
-- than judged. The bar is stage 2's tier 1 exactly -- no alphanumeric content at all after
-- whitespace normalisation (ADR-070) -- and deliberately no stricter, since a confidence threshold
-- for seeds is one nobody has measured.
--
-- There is no verdict here and there never will be: every kind in the closed vocabulary (ADR-042)
-- exists to remove a document from publication, and a seed is never published. An unreadable seed is
-- an operator's problem to fix, not a document to filter.
--
-- run_id is the measurement run that found it, so a corrected seed folder -- which is a different
-- run, because the seed folder is part of what that run's identity is derived from (ADR-083) --
-- records its own row set rather than overwriting this one. That is what keeps scores taken against
-- a partial seed set from ever being mistaken for scores against a complete one, by identity rather
-- than by a check.
CREATE TABLE IF NOT EXISTS unusable_seed (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    reason TEXT NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);

-- embedding's own table (ADR-086, ADR-092): how far the seed set resembles the survivors it will be
-- scored against, keyed by the measurement run that computed it -- a fresh row set per run (ADR-077),
-- never an overwrite of an earlier one. Two shapes of row, one table, told apart by comparison: a
-- Proportion carries 'language' or 'provenance' there and the language code or born-digital/converted
-- in category; a Spread carries 'spread' there and the signal -- word-count, page-count,
-- vowelless-word-ratio, single-character-word-ratio -- in category. A Proportion row leaves the
-- *_lower_quartile/median/upper_quartile columns null; a Spread row leaves seed_share/corpus_share
-- null, because a middle figure and a share are not the same kind of number and a shared "value"
-- column would blur what a reader is looking at.
--
-- One side's three quartile columns are null together where that population reported the signal for no
-- document at all -- a born-digital folder has no page count, and no page count is not a page count of
-- zero (ADR-086). Null rather than 0.0 so a query cannot read the absence as a measured figure.
--
-- statement is the sentence ADR-086 requires, computed once here rather than re-derived by every
-- reader (the HTML report and any future one), so the table's own words and the report's can never
-- drift apart. seed_document_count and corpus_document_count on every row repeat the same two
-- populations (SeedCorpusComparison.Comparison carries them once); repeating them is cheap against a
-- few dozen rows and lets one row be read on its own without a second query for its denominator.
CREATE TABLE IF NOT EXISTS seed_corpus_comparison (
    run_id TEXT NOT NULL REFERENCES run (id),
    comparison TEXT NOT NULL,
    category TEXT NOT NULL,
    seed_documents INTEGER,
    corpus_documents INTEGER,
    seed_share REAL,
    corpus_share REAL,
    seed_lower_quartile REAL,
    seed_median REAL,
    seed_upper_quartile REAL,
    corpus_lower_quartile REAL,
    corpus_median REAL,
    corpus_upper_quartile REAL,
    seed_document_count INTEGER NOT NULL,
    corpus_document_count INTEGER NOT NULL,
    unmeasured_seed_document_count INTEGER NOT NULL,
    statement TEXT NOT NULL,
    PRIMARY KEY (run_id, comparison, category)
);
