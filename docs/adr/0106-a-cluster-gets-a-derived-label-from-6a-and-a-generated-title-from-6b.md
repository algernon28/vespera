# ADR-106 — A cluster gets a derived label from 6a and a generated title from 6b

- **Date**: 2026-09-12
- **Status**: accepted
- **Builds on**: [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md) — which gave a cluster a row to be named in, and left what the name is derived from open.

## Context

[ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md) gates generation on a human reading the arrangement. A modularity community ([ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)) arrives with an ordinal and nothing else, and *"cluster 3, 47 documents"* cannot be reviewed by anyone. So a cluster needs a name **before** 6b runs, which is the constraint the whole question turns on.

Three ways were live, and each was uncomfortable in the same way — all three asked one string to serve both the gate and the finished document.

### What the corpus can actually yield, mechanically

- **Shingles cannot name anything.** `shingle.shingle_hash` is an `INTEGER`; the text was never stored. Naming from "the cluster's most distinctive shingles" would mean a fresh pass over the corpus that retains strings, for a phrase assembled out of overlapping n-grams.
- **Document titles can.** Docling labels items `title` and `section_header`, and `HybridChunker` makes a heading start and lead its chunk ([ADR-029](0029-chunking-structure-first-with-a-measured-llm-fallback.md)). Both the full response (`extraction_cache`) and the chunk text (`chunk_cache`) are cached, so a document's own title is recoverable with no Docling call.
- **Nothing can supply a language.** TF-IDF or keyword extraction needs stopwords, and the corpus's language mix is unknown by design — [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md)'s mismatch measurement exists precisely because nothing here may assume one.

## Decision

**A cluster has two names. Stage 6a derives a *label*; stage 6b generates a *title*.**

They are different words for different things, and `CONTEXT.md` now carries both so they are not used interchangeably.

### The label: derived, by 6a, into the ledger

**The title of the cluster's highest-scoring document, plus the cluster's document count.** The gate reads *seed `Safety`, cluster 3 — "2019 Site Safety Audit" and 46 others*, which is a thing a person can judge.

It costs one query over already-cached data, assumes no language, and is auditable: the label points at a document the reviewer can open and disagree with.

**The fallback chain is the common path, not an error case**: the Docling-labelled `title` → the filename stem from the occurrence's root-relative path ([ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md)) → the ordinal alone. Plain `.txt` files and scans that OCR'd into undifferentiated body text have no title item at all.

**Labels are not unique.** ADR-105 keys the cluster row by run, winning seed and ordinal, so identity never depends on the label. Two clusters led by identically-titled documents keep both labels: that is a fact about the corpus the gate should see, not a collision to paper over by appending a number.

### The title: generated, by 6b, into the deliverable

A synthesis doc's heading is written by the model that read the whole cluster — the only thing in the system that has. It arrives with the prose it belongs to, and it does not exist until 6b runs.

**A cluster file's heading is the title, falling back to the label where generation failed** for that cluster. `index.md` carries both, so the tree stays navigable where a synthesis doc is missing, and a reader can see what the mechanical derivation said before the model retitled it.

### The operator does not rename a cluster at the gate

Arrangement rows are per-run and never edited in place ([ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md), ADR-105). The one human fact in this system, the relevance label, deliberately lives keyed by the document's path so it outlives the run and the model that prompted it ([ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md)).

A hand-edited name inside a derived row set is exactly the drift both records avoid: a re-run would either destroy the edit or refuse to overwrite it, and neither is a behaviour worth having. The deliverable is Markdown ([ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)) — after hand-off the operator renames whatever they like, in the artifact that is theirs.

## Consequences

**No model runs before the human gate**, which is what keeps ADR-022's split from quietly collapsing. The arrangement is reviewable with nothing served, and a corpus can be re-arranged and re-reviewed without an Ollama instance in the room.

**The gate's quality depends on document titles, which the corpus supplies unevenly.** An archive of untitled scans yields labels that are filename stems, and a reviewer reads those. That is a real limitation of judging a cluster before anything has read it, and the alternative — generating labels first — costs the split above.

**Two terms enter `CONTEXT.md`** — **cluster label** and **cluster title** — with "cluster name" pushed to `_Avoid_` on both, since the ambiguous word is the whole problem this record solves.

**`CONTEXT.md`'s publication-ready artifact entry stops deferring.** It said what the deliverable carries was undecided and named it the slice's first question; ADR-103 and [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) answered that, so the entry now says what it is and that the originals are referenced rather than carried.

**6b's context budget grows an obligation.** Whatever is sent to the model has to be enough to title the cluster, not only to connect it — a decision that belongs to the ticket settling what the generator reads.
