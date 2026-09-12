# ADR-103 — The deliverable is a Markdown tree in the working directory, one tree per run id

- **Date**: 2026-09-12
- **Status**: accepted
- **Answers**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) — the first of the questions it handed forward to the 6a/6b slice: what 6b writes, and where the output lands. The other, what becomes of the surviving originals, stays open.

## Context

[ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) struck publication from the design and said exactly why the form of the output could no longer be left abstract: while an adapter existed downstream, its shape would have settled what a "self-contained publication-ready artifact" concretely was. With nothing downstream, **the artifact is the product**, and every later decision in the slice is read off its answer.

That record named two things it could not answer — where the output lands, and what becomes of the surviving originals — and made them the slice's first questions rather than details inside it. The stage 6a/6b map opens on the first of them; this is its resolution.

### What the working directory actually holds

ADR-101 raised one concern about putting the deliverable in the working directory: that directory holds derived state a re-run may delete ([ADR-066](0066-the-command-line-names-the-root-configuration-is-the-fallback.md)), "and writing it there without deciding so would make it deletable by a stage re-run".

Read against the tree, that concern aims at the wrong risk.

- **Nothing in this codebase deletes anything in the working directory.** The five reports are written to fixed names and overwritten in place. [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)'s retuning is a `DELETE` of *ledger rows*, which is a statement about the database and not about the filesystem.
- **A non-derived, human-owned file already lives there**, and has since stage 5: `relevance-labels.yaml`, whose answers are a fact about the documents rather than about the run that asked, and outlive the model that prompted them ([ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md)). The working directory has not been purely derived state for some time.

So the hazard is **overwrite**, not deletion — a second run silently replacing the tree the operator already handed to someone. That is a real hazard, and it is answered by what the tree is named rather than by moving it somewhere else.

Against that stands `README.md`'s invariant, which is the thing an operator actually learns: *everything the tool writes goes in one working directory, never inside your archive*. A second output root would cost a fifth value on a path [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) worked to keep at four, for a separation the run id already provides.

## Decision

**What stage 6b writes is a directory tree of Markdown at `<working-dir>/deliverable/<run-id>/`, one tree per 6b run.**

```
deliverable/<run-id>/
  index.md                      the arrangement, whole, and what produced it
  <seed-partition>/
    <cluster>.md                one synthesis doc per cluster
```

### The form mirrors the arrangement, two levels deep

Seed partitions at the top, clusters beneath, exactly as `CONTEXT.md` defines the **arrangement**. One Markdown file per cluster, holding that cluster's synthesis doc.

**Markdown, not HTML.** The five reports are HTML because each is read once in a browser to choose a number; the deliverable is the thing carried somewhere else, and every wiki importer eats Markdown. ADR-101 leaves an operator who wants a wiki to make one — this is the format that makes that possible without this project knowing anything about wikis.

**`index.md` is mechanical, not generated.** It lists every partition and cluster with document counts and links, and opens with what produced the tree: the run id in full, the walk, the corpus root, and the profile values the run consumed. That is what lets the tree be read without the database beside it. Whether a *generated* overview sits above the partitions as well is not settled here.

### It lands in the working directory, and there is no new profile key

The README invariant holds, `--db-dir=` remains the only thing that moves it, and the operator sets nothing new.

### One tree per run, named by the run id

A run id is content-derived ([ADR-048](0048-walk-and-run-identity.md)): the SHA-256 of the implementation version, the configuration consumed, the walk, and the upstream runs. Naming the directory with it buys three properties, where a rule against overwriting would have bought one:

- **A re-run that changed nothing writes the same path**, because it mints the same id. Rewriting it is idempotent rather than destructive: the same inputs produced it.
- **A re-run under a changed configuration writes a new tree beside the old one.** What was already handed over is untouched, and the two are told apart by construction rather than by timestamp.
- **The deliverable carries its own provenance in its path.** The id in the directory name is the id in the ledger — no join, and no guessing which run wrote which tree.

### `deliverable/`, because the glossary has the word

[ADR-102](0102-publication-is-the-state-the-deliverable-is-handed-over-in-never-a-verb-this-project-uses.md) keeps "publication" as an adjective describing the state the artifact is handed over in, never a noun and never a verb. A directory named `publication/` would reassert precisely what that record removed, in the one string the operator reads every time.

### Self-contained has a test

**Every link inside the deliverable resolves with no database, no ledger and no network.**

What sits inside that boundary — the surviving originals above all — is the slice's next question and is deliberately not pre-empted here. It is, however, constrained: any answer that leaves a link in the deliverable pointing at something only the ledger can resolve fails this test.

## Consequences

**Nothing is ever cleaned up.** Deliverable trees accumulate under the working directory, and disposing of them is the operator's. This is the cost of the decision rather than an oversight: deleting a previous run's deliverable is the single thing it exists to prevent.

**A 64-character hex directory name is neither legible nor chronologically sortable.** Mitigated rather than denied — the invocation's last line names the path it just wrote, in the shape ADR-098 already requires, and `index.md` carries the provenance in readable form. Path length is not a refusal point for the tool: the JDK opts Windows paths into the long-path form ([ADR-065](0065-the-walk-algorithm-is-tested-on-an-in-memory-filesystem-identity-stays-on-ntfs.md)).

**No `latest` symlink.** Census is Windows-first, and this repository's symlink fixtures already abort by assumption where the privilege to create one is missing. A convenience that fails on the target platform is not one.

**The path is idempotent; the bytes are not claimed to be.** Research on Ollama's generation surface found no source claiming a fixed seed reproduces output, and `cache_prompt` is hardcoded on in 0.33.2. Whether a rewrite of the same directory is expected to produce identical text is a separate question this record does not answer.

**Two later decisions inherit a floor.** What becomes of the surviving originals, and what a citation is, are both now constrained by the self-containment test rather than free: an occurrence id a reader cannot follow without the database does not satisfy it.

**`README.md`'s "Where things live" tree gains the deliverable path**, and `docs/check-claims.mjs` checks that tree against what the code writes — so the document and the code move together, or the build says so.
