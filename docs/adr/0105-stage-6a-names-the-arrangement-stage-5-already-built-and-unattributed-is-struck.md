# ADR-105 — Stage 6a names the arrangement stage 5 already built, and `unattributed` is struck

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md) — 6a does not build a tree, because stage 5 already built one; and its `unattributed` top-level node is struck, having no members it could ever hold. The 6a/6b split itself, and the human gate between them, are untouched.

## Context

[ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md) says 6a builds a page tree — a seed-named top level plus `unattributed`, clusters within — and gates 6b on a human reading it. It was written on 2026-08-20, before stage 5 existed, and both of its structural claims have since been overtaken by what stage 5 shipped.

### The structure is already in the ledger

`relevance_score` carries `winning_seed_occurrence_id` for every scored occurrence, which is the **seed partition**. `document_cluster` carries the cluster ordinal within that partition, keyed by occurrence and run. The `arrangement` `CONTEXT.md` defines — seed partitions at the top, clusters beneath — is therefore a fact about stage 5's rows, not something 6a has to construct.

What is genuinely missing is stated by `document_cluster`'s own schema comment: *a cluster has no row of its own — it is the set of rows carrying the same run, winning seed and ordinal*. There is nothing to name, nothing to order, and nothing for a human to point at and approve.

### `unattributed` has no members and cannot acquire any

Every survivor reaching 6a carries a winning seed, so the node ADR-022 places beside the seed-named top level is empty by construction. That rests on unscored survivors being impossible, which is checked rather than assumed:

- **Tier 1 of the degeneracy floor is unconditional** ([ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md), `DegeneracyFloor.evaluate`): `alphanumeric_char_count == 0` yields a blocking `degenerate-output` verdict, with empty, whitespace-only and punctuation-only text normalised to read the same way. Unlike tier 2 it does not wait for a configured floor, so a textless document is never a survivor.
- **`RelevanceScoring.scoreAndRecord` throws rather than scoring zero** when a survivor has no stored vectors, on the grounds that "a row here would misreport an assumption as a measurement" ([ADR-020](0020-relevance-scoring-function.md), *confirm, do not assume*).

## Decision

**Stage 6a writes one row per cluster, under a run of its own, and no verdicts. `unattributed` is struck.**

### One row per cluster, and membership is left alone

A row per cluster, keyed by the 6a run, the winning seed and the cluster ordinal, carrying the cluster's **name** and its **order** within the partition. That is the level that does not exist; the levels that do are read from stage 5.

Two alternatives were weighed and refused:

- **Restating occurrence → partition → cluster in a table of 6a's own** duplicates tens of thousands of rows stage 5 owns, and recreates exactly the membership drift `document_cluster`'s shape was chosen to make impossible.
- **Making the arrangement a pure query** over stage 5's rows leaves the human gate with nothing to approve. ADR-022's gate needs an artifact that still says the same thing when it is read a second time, and a query does not.

### It mints a run

A 6a run with its own stage, upstream-chained to stage 5's clustering run, looked up by stage and walk with two candidates stopping the run ([ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md)), and a re-run writing a fresh row set under a fresh id ([ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)).

This is also what makes [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s `deliverable/<run-id>/` behave correctly: a run id hashes its upstream runs ([ADR-048](0048-walk-and-run-identity.md)), so a re-arrangement changes the 6b run's id — and therefore the deliverable's directory — by construction rather than by a rule someone has to remember.

### No verdict rows, and that includes `passed`

6a removes nothing: `CONTEXT.md` is explicit that nothing is removed for the cluster it lands in, so no blocking kind applies.

It does not write `passed` either. [ADR-049](0049-verdict-rows-and-schema-versioning-without-a-migration-tool.md) says every stage writes a row per occurrence including non-blocking `passed`, and **no stage does**: `VerdictKind.PASSED` exists in the vocabulary and is written nowhere in `src/main`. That gap between the record and the code is real and deserves its own decision. It should not be closed as a side effect of the terminal stage, over the one stage in the cascade that judges nothing.

### The arrangement is total, and 6a asserts it

Every survivor has a score, a winning seed and a cluster ordinal. If 6a meets a survivor with no cluster row, it **stops**, in the same way and for the same reason `scoreAndRecord` refuses to score an unvectored survivor.

A defensive "miscellaneous" bucket is refused explicitly: it would turn a broken invariant into a silently rendered section of the deliverable, where stopping makes it a failure someone reads.

## Consequences

**ADR-022's description of 6a is superseded in substance while its split survives.** 6a and 6b remain two stages with a human gate between them; what changes is that 6a's job is naming and ordering rather than construction, because construction happened in stage 5 under [ADR-027](0027-clustering-moves-to-stage-5.md)'s move of clustering.

**`unattributed` leaves the design**, and the word is not recycled for the unscored-survivor case — that case is impossible, and 6a asserts it rather than housing it.

**Two adjacent gaps are recorded here rather than closed here.** ADR-049's per-occurrence `passed` row is unimplemented by every stage. And stage 5's scoring step is a plain tasklet with no skip policy — the skip and circuit-breaker machinery exists only in stage 2's extraction step — so one document's embedding failure ends the step rather than leaving an unscored survivor behind. Both belong to their own stages, not to this one.

**The cluster row is where a name will live**, which is what makes naming a decidable question rather than a rendering detail. What that name is derived from is not settled here.
