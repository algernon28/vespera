# ADR-113 — No corpus-wide synthesis doc sits above the seed partitions; the deliverable's top level stays mechanical

- **Date**: 2026-09-13
- **Status**: accepted
- **Closes a deferral in**: [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) — *"Whether a **generated** overview sits above the partitions as well is not settled here."* It is settled here, and the answer is no. Nothing that record decided changes: `index.md` is mechanical, and it stays mechanical.
- **Reads, and does not amend**: [ADR-021](0021-synthesis-exists-to-make-the-survivor-set-coherent.md) — the section below says which reading of *"connective material about the collection"* was taken and why, because the other reading is the whole case for the opposite answer.
- **Rests on**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (a citation is an ordinal into one call's exemplars), [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) (one exemplar-first call per cluster), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a fault is keyed by a cluster), [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) (the run ends at the generated documents), ADR-026 (model-checking-model is refused).

## Context

ADR-103 fixed `index.md` as **mechanical, not generated** — every partition and cluster with counts and links, opening with the run id, the walk, the corpus root and the profile values consumed — and explicitly left one thing open: whether a *synthesis doc* sits above the partitions as well, over the whole corpus.

**It is not one more cluster.** ADR-108 budgets one call per cluster, exemplar-first, filling with each document's leading chunk until a word budget runs out. A corpus-wide doc has no cluster to draw exemplars from; its subject is the whole arrangement, and nothing has ever read that. The only corpus-wide text that fits a window is the labels and titles 6b itself generated.

### What the surviving record says, and how much weight it carries

`docs/architecture.md` §1.2 and §1.3 — the fuller record for the reconstituted ADRs — say **"one overview per cluster"** and *"6b generates connective overviews per cluster"*. So a corpus-wide doc would be new design rather than something recovered.

That is evidence and not binding, and the reason is worth stating: the same §1.2 row reads *"citations resolved to occurrence ids"*, which ADR-109 retired outright. A digest that is stale in one clause does not settle a question by another.

### The case for it, stated properly

ADR-021's verbatim summary is *"synthesis is connective material **about the collection**, not per-document summarisation."* A cluster's synthesis doc is connective material about a **cluster**. Read strictly, 6b as specified never produces anything about the collection, and **nothing in the deliverable connects one partition to another**. The operator chose their seeds knowing their domain and *not* knowing the archive, so "what turned out to actually be in here" is the question this tool exists to answer, and no artifact answers it in prose.

## Decision

**No corpus-wide synthesis doc. The deliverable's top level stays mechanical, and `index.md` remains the only thing above the seed partitions.**

### It could not be checked, and it would sit where checking matters most

ADR-109 made a citation an ordinal into the exemplars **one call** sent, and the whole check is `1 ≤ n ≤ k`. That design works because there is a `k` to count against: fabrication becomes arithmetic. A corpus-wide call sends no documents, so there is no `k`, no ordinal to mint, and nothing for a marker to resolve to.

The consequence is not that this one document is merely unchecked. ADR-109 also ruled that **uncited prose fails the cluster** — the one case where passing would mean nothing was checked. That rule would have to be waived, by construction and permanently, for the single document a reader opens first and trusts most. The deliverable's honesty story is that every prose claim traces to a file a human can open; a top-level doc that cannot participate in it is a hole in the one property this stage has been defending for five decisions running.

### It would be synthesis over synthesis

Its only possible inputs are the cluster labels and titles 6b generated — one remove further from the documents than anything else in this system. This is not ADR-026's model-checking-model, which is a narrower thing, but it is the same family: the model's own output standing in for the corpus. Every other generated artifact here reads documents.

### It narrates the operator's own structure back to them

`CONTEXT.md`'s **seed set** entry: *"The sole carrier of domain knowledge in the system: it defines relevance, names the top level of the arrangement, and shapes what sits beneath it."* The top level of the arrangement **is the operator's own taxonomy**. A generated document describing it tells them what they already told the tool, in the tool's words rather than theirs.

### The reading of ADR-021 this takes

**The collection ADR-021 means is the survivor set, and 6b makes it coherent cluster by cluster.** ADR-021's own title says so — *"Synthesis exists to make the survivor **set** coherent"* — and its contrast is with per-document summarisation, not with per-cluster synthesis.

This is stated rather than assumed because the other reading is available and someone will find it. **A future reader who takes it is re-opening a settled question, not discovering a gap**; the way back in is a record that answers the three objections above, not an appeal to ADR-021's wording.

### `index.md` needs no new record either way

The rival considered was a **mechanical** top-level statement instead of a generated one — partition and cluster counts, how they split, what faulted. It is not refused on its merits; it simply is not this decision. `index.md`'s content is ADR-103's, every number in it is derivable from what it already lists, and adding one needs no ADR at all.

### The ticket's other four questions are moot, not unanswered

[Whether a generated overview sits above the seed partitions](https://github.com/algernon28/vespera/issues/172) asked four further things — what it reads, where it lands, what it cites, whether it can fault. Each was conditional on this answer and none survives it. They are recorded here as closed by the root, so that reopening the root is what it takes to reopen them.

## Consequences

**Nothing in the deliverable connects one partition to another, and that is the accepted cost.** It is the real loss, and it is not mitigated. A reader who wants the corpus characterised as a whole has `index.md`, `documents.csv` and the cluster docs to do it from — a deliverable built to be read, which ADR-101 already decided is where this tool stops.

**"Every prose sentence in the deliverable traces to a document" is now an invariant of the whole tree**, not a property of most of it. That is worth more than the document this refuses, and it is only true because there is no exception at the top.

**`synthesis` has one generating shape, not two.** ADR-108's one-call-per-cluster stays the only call 6b makes, ADR-111's `cluster_fault` stays keyed by a cluster with nothing outside its key space, and ADR-110's two tables need no third. Each of those would have taken an exception.

**ADR-101's precedent extends one step.** That record left rendering and uploading to the operator; this leaves corpus-wide narration to them, on the same reasoning — the tool produces the material, and what is made of it is not the tool's.

**No vocabulary changes, because `CONTEXT.md` already excluded this.** Its **publication-ready artifact** entry is a closed list — *"the arrangement, the synthesis docs written over it, and a listing of the survivors it arranges"* — with no room for a corpus-wide document. The glossary was consistent with this answer before the question was asked.
