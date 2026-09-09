# ADR-097 — A relevance label is keyed by the document's path and the seed set, not by the occurrence

- **Date**: 2026-09-09
- **Status**: accepted
- **Amends**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) — its "one `relevance_label` row per labelled occurrence, **keyed by the occurrence and the seed set**" becomes keyed by the document's path and the seed set. Its "a label is a fact about a document, and no run owns it" section is unchanged in intent; this is what makes it true.

## Context

ADR-088 states the strongest claim in the system about a table: `relevance_label` is **the one table whose rows a re-run never rewrites**, because "a second copy of a human's answer is not a second observation; it is a duplicate". Its headline consequence follows from that — a re-score under a new embedder identity re-reads the existing labels and recomputes the bands **without new labels being supplied**, because the two hours that produced those rows cannot be produced again by a machine.

Building [#112](https://github.com/algernon28/vespera/issues/112) showed that consequence does not hold across invocations, and cannot.

**Occurrence ids are per-walk, deliberately.** [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md)'s minting rule reuses only an *unfinished* walk: "a deliberate re-walk after a code change gets a new id automatically, because by the time an operator re-invokes for that reason, the prior walk has already finished." So every ordinary `vespera run` over a corpus mints a new walk and a new `file_occurrence` row per file. A label keyed by `occurrence_id` points at the walk that asked the question, and joins to nothing the next run scores. The answers survive in the table and stop being findable — which is worse than losing them, because nothing reports their absence.

**The label file is already keyed by path.** `RelevanceLabelFile` writes the document's path, and `LabelIngestion` resolves path → occurrence inside the walk the file names. The identity that survives a re-walk is therefore already in the loop, one layer above the table; only the row disagrees.

**The blast radius is wider than labels, and this decision deliberately does not widen with it.** `Ledger#survivors` anti-joins `verdict` on `occurrence_id` within one walk's occurrences, so no verdict carries across invocations either: a re-invocation re-derives the whole cascade from scratch. For a *measurement* that is [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) working as intended — a second computation is a second observation. A label is the one thing in the system that is not a measurement, which is why the same mechanism reads as correct everywhere else and as a defect here.

## Decision

### The key is the path and the seed set

**`(path, seed_set)`**, where `path` is the document's path relative to the corpus root — the identity [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md) already fixes, and the one the label file already travels on. The row **stops referencing `file_occurrence`**: a label is a fact about a document in a corpus, not about one walk's sighting of it.

`run_id`, `score_shown` and `embedder_identity` stay exactly where they are, as the **context the judgement was given in** rather than as part of its identity — which is what ADR-088 already says they are. The walk that asked the question is still recoverable through `run_id`.

**Reads resolve the path into the current walk when they need to join scores.** That resolution belongs to whoever is doing the join, and it is the only place an occurrence id is needed at all.

### A renamed document is asked about again, and that is the failure direction chosen

Path is not a perfect identity. A document that is moved or renamed between runs loses its answer and appears in a later sample as a question nobody has answered.

**The alternative was the content hash, and it fails worse.** Keying by content would survive a rename, at the cost of discarding an answer whenever the bytes change — a re-scan of the same paper, a re-export of the same report, a PDF rewritten by a tool that touched only its metadata. Those all produce a document a person would answer the same way, and throwing the answer out because the bytes moved is the more expensive mistake. A rename costs a re-question; an edit-invalidation costs a judgement that was still true.

### No migration, because nothing has been labelled

`embedding` schema **6 → 7**, and no migration path. As of this decision no corpus has been labelled outside tests — the labelling loop landed in [#111](https://github.com/algernon28/vespera/issues/111) and the floor in #112, both on the same day as this record — so there is no answer anywhere to carry across. This is written down rather than left implicit, so that a later reader can tell it was checked rather than skipped.

### Occurrence continuity across walks was weighed, and is not decided here

The general fix is a resolution from an occurrence in one walk to the same document in the next, which would carry verdicts across invocations as well as labels. It is the more interesting decision and it is a **ledger-wide** one: it changes what a walk is for, what `survivors` means across runs, and whether re-derivation is still the default. Reaching it through a labelling ticket is how a system acquires an architecture nobody chose. It is named here so that the next person to want it knows this decision did not overlook it.

## Consequences

**ADR-088's headline consequence becomes executable.** A re-score under a new embedder identity re-reads the answers already given and re-bands them against the new scores, across invocations, with nobody asked anything twice. #112 could only pin that at the unit level; the ticket that implements this can pin it at the invocation level, which is where the claim was always about.

**Both halves of the key are now paths.** `seed_set` is already the canonical seed-folder path, so a label row becomes "this document, judged against this seed set" in two spellings of the same kind of identity — which is easier to reason about than a surrogate key paired with a path.

**A label can outlive the corpus it was about.** A row whose path no longer exists is not an error and is not cleaned up: it is an answer about a document that was there. Nothing reads it, and it costs a row.

**The verdict cascade still re-derives from scratch on every invocation.** That is unchanged by this decision and remains the open question named above.
