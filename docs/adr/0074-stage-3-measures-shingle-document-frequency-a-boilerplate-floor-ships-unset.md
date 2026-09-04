# ADR-074 — Stage 3 measures shingle document frequency; a boilerplate floor ships unset

- **Date**: 2026-09-05
- **Status**: accepted
- **Amends**: none (fills in the mechanism ADR-038 left unspecified — what makes a shingle "boilerplate," and where the result of measuring it lives)

## Context

ADR-038's reconstituted summary says stage 3 computes "corpus-wide document frequency" over `similarity`'s shingle table to "identify boilerplate before it corrupts stage-4 dedup or stage-5 relevance." Nothing in the record said what makes a shingle boilerplate as an actual number, where the result is stored, or whether stage 3 renders a judgement at all — a real tension, since ADR-038's own word "identifies" pulls toward a verdict-shaped answer while `CONTEXT.md`'s Census entry and this map's own Destination both hold stage 3 to writing no verdicts, the same shape as stage 0.

**What already exists, read off the code rather than assumed**: the `shingle` table (`schema.sql`) deliberately carries no primary key, because a document can legitimately repeat a shingle and a uniqueness constraint would silently discard the repeat count a document-frequency computation needs — so document frequency is `COUNT(DISTINCT occurrence_id)` per hash, and a same-document repeat count (`COUNT(*)`) is available for free alongside it. `RunId.of(...)` already folds sorted upstream run ids into a run's identity hash, and `run_upstream` records the chain queryably, so a document-frequency result keyed by stage 3's own `run_id` already distinguishes "measured over stage-2 run A" from "over run B" — no separate pointer to the source run is needed in the key. Shingle-parameter scoping (a document-frequency count is meaningless across different granularities) is already settled by ADR-073, and corpus-wide scope (not per-format, per-folder) is already settled by ADR-038's own wording — neither reopened here.

## Decision

**Stage 3 measures; it does not judge.** It writes document-frequency counts and nothing that designates a shingle as boilerplate — no flag column, no membership table, no verdict of any kind. This continues census's own posture ("measure before judging... thresholds left unset") rather than amending it: ADR-038's "identifies boilerplate" is read as "makes boilerplate identifiable," through the measurement this ADR specifies, not as stage 3 rendering the judgement itself.

### Where the eventual threshold lives

**A new `Profile` field, `boilerplateDocumentFrequencyFloor`, is named now and ships with no value** — the same shape ADR-070's tier-2 confidence floor and ADR-071's timeout/circuit-breaker counts used for a value nothing has measured yet. It is not applied by stage 3; stage 4 (out of scope for this map) is where a document-frequency count actually excludes a shingle from dedup. Naming the key now, rather than deferring it to stage 4's own map, means the stage-3 hand-off spec and its report (a separate ticket on this map) have a concrete profile key to serve as the calibration target for, and the observe-before-enforce obligation is recorded in the ADR that creates the need for it.

**The threshold is stated as a proportion, not an absolute document count.** A footer appearing in 400 of 500 documents and one appearing in 400 of 400,000 are different phenomena; a fixed count would silently change meaning between a small test corpus and a real one. This fixes only the *unit* a future threshold value is stated in — the values actually **stored** stay counts (ADR-073's "counters, never ratios"), so the proportion is computed at read time from the stored counts and the corpus size below, never persisted as a pre-divided ratio.

### Storage: two new `similarity`-owned tables

A column added to the existing `shingle` table is wrong twice over: document frequency is a property of a hash within a corpus, not of any one row, so it would mean writing one value copied across every row carrying that hash; and writing it would mutate rows owned by stage 2's run, which is exactly what ADR-048's run-owned-rows rule exists to prevent.

- **`shingle_document_frequency`**, keyed `(run_id, shingle_parameter_identity, shingle_hash)`: `document_count` (`COUNT(DISTINCT occurrence_id)`) and `total_count` (`COUNT(*)`), so a phrase repeated fifty times inside one document is distinguishable from one appearing once across fifty documents.
- **Only hashes with `document_count >= 2` get a row.** ADR-073 already flags the shingle table as "the largest table in the database by a wide margin"; on natural text, the count of distinct hashes appearing in exactly one document is close to the total shingle count, so writing a row for every singleton would roughly double that cost to store a value — "this shingle is unique to one document" — that carries no corpus-wide signal a boilerplate floor could ever act on. **The omission is itself the fact: an absent hash means exactly one document, never zero.** This must be stated as plainly in the schema comment as it is here, since a reader expecting a complete distribution would otherwise misread a missing row as "never seen."
- **`shingle_corpus_size`**, one row per `(run_id, shingle_parameter_identity)`, carrying `shingled_document_count` — the denominator the proportion above is computed against. This has to be stored somewhere, once, at the grain the proportion needs; folding it into `shingle_document_frequency` as a sentinel row would misstate that table's grain (one row per hash), and leaving it to the report ticket to derive would make stage 4's eventual threshold check depend on a report artifact rather than a measurement table.

### The denominator is stage-2 survivors, not every occurrence with a shingle

Document frequency is computed only over occurrences carrying no blocking verdict from stage 2 — a footer's prevalence among documents already excluded (`extraction-failed`, `degenerate-output`) is not a fact about the corpus stage 4 will actually deduplicate. A `partial_success` document is a survivor by design (ADR-070) and is therefore in scope for this count. This makes stage 3's read a join against `Ledger.survivors(runId)` rather than a bare `GROUP BY` over every shingle row stage 2 ever wrote.

### Module ownership: this half lives in `similarity` alone

Unlike stage 2's shingle-writing pass (which needed `pipeline` to compose `extraction` and `similarity` without either depending on the other), this computation only reads and writes `similarity`'s own tables — the shingle table it measures and the two tables above. No cross-module composition is needed for the boilerplate half specifically. `pipeline` defines the job step and supplies the run id; the computation itself belongs to `similarity`.

**This is not the whole of stage 3's module ownership** — the report half of ADR-019's "derived columns plus a report" (a separate ticket on this map) does read `extraction`'s `extraction_metric` columns and will need `pipeline`'s composition, the same shape stage 2 used. Stage 3 therefore has two owners depending on which half is running, and its `implementationVersion` spans both modules under ADR-073's reading of ADR-058, the same narrowing already applied to stage 2.

### Vocabulary

**Boilerplate** is added to `CONTEXT.md`: text repeated across enough of the corpus that its recurrence is a fact about document *production* (a template, a letterhead, a standard disclaimer) rather than about content — measured here as a shingle's document frequency, judged nowhere in this stage. `_Avoid_`: template text, chrome, junk, filler.

## Consequences

**Stage 3 stays verdict-free**, consistent with `docs/architecture.md`'s cascade table and this map's own Destination. Nothing about census's "measure, don't judge" posture is reopened; ADR-038's wording is read as compatible with it, the same kind of precise-reading-without-substance-change move ADR-068 and ADR-072 each made on an earlier decision's phrasing.

**A profile key exists with no consumer yet.** `boilerplateDocumentFrequencyFloor` ships unset and unread until stage 4's own map specifies how a document-frequency count actually excludes a shingle from dedup. This is the same shape ADR-070's tier-2 floor shipped in — a recorded gate pointing at the measurement that should inform it — and it means stage 4's future map inherits a named key rather than needing to invent one.

**The stage-3 report (a separate ticket on this map) has a concrete calibration target.** Whatever that ticket decides the report computes, it now has a named, unset profile value and the measurement table behind it to report on.

**`shingle_document_frequency` omits singleton hashes on purpose**, which any future reader — including whoever writes the report — must not mistake for "never measured." The schema comment and this ADR both say so, but a query written without reading either would get a silent false negative for "how many documents does this shingle appear in" on a hash that in fact appears in exactly one.

**Nothing here specifies call sites or literal DDL.** Column names above (`document_count`, `total_count`, `shingled_document_count`) are a reasonable default, not a re-decision — the same deferral ADR-070 through ADR-073 each made to their hand-off specs. The `Profile` field's YAML key name and its place in `profile.yaml` are likewise the hand-off spec's to write.
