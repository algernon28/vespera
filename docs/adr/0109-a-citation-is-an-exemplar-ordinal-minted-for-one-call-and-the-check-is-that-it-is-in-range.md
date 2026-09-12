# ADR-109 — A citation is an exemplar ordinal minted for one call, and the check is that it is in range

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-026](0026-generated-content-verified-mechanically-and-by-human-review.md) — its mechanical check is made concrete here, and its human review is relocated, because the consolidation gate that record named no longer exists.
- **Rests on**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) (the membership list a citation lands in) and [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) (the exemplars a citation may point at).

## Context

[ADR-026](0026-generated-content-verified-mechanically-and-by-human-review.md) is one line of reconstituted digest: a mechanical citation-existence check plus human review, with a model checking model output explicitly rejected. `docs/architecture.md` adds "citations resolved to occurrence ids". Between them, nothing says what a citation looks like in a generated document, what the check checks, or what happens to prose that fails it — and 6b cannot be written without all three.

### What has moved since ADR-026 was written

- **The consolidation gate it named is gone.** [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) struck the publication stage, and [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) placed this slice's only gate *before* 6b, over an arrangement nothing generated. There is no in-run moment left at which a human reviews generated text.
- **The deliverable has to resolve its own links** ([ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)): no database, no ledger, no network. A citation rendered as an occurrence id fails that test on its own.
- **Every cluster file already lists its whole membership, linked** ([ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md)), so the thing a citation should point at already exists in the file the citation is written in.
- **A call sends a subset of the cluster** ([ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)): the highest-scoring documents in relevance-score order, one leading chunk each, until a word budget runs out.

### The failure mode this has to defeat

A model asked to write `[occ:8f3a1c…]` inline will produce well-formed ones it was never given. A 64-character hex space costs nothing to generate plausible members of, and a fabricated id is indistinguishable from a real one until it is looked up — which is to say the prose *reads* as sourced whether or not it is.

## Decision

**A citation is an ordinal that Vespera minted for that one call, and the mechanical check is that the ordinal is one Vespera minted.**

### The numbering is Vespera's, and the reader sees the same numbering

The cluster file's membership list is **numbered in relevance-score order** — the same order ADR-108 draws exemplars in — so the exemplars sent are exactly entries `1..k` of the list the reader is looking at. One numbering serves three parties: the prompt, the check, and the reader.

- **In the prompt**, each exemplar arrives under its ordinal, `[1]` to `[k]`, and the instruction reserves square brackets for citations.
- **In the response**, citations appear as `[n]` markers **inline in the prose, and nowhere else**. There is no parallel `citations` array in the schema that [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) imposes and validates: two places to state a citation is two places for them to disagree, and the check has to read the thing that actually gets rendered.
- **In the deliverable**, each `[n]` is rewritten at write time into a link to entry `n` of the membership list below it. The occurrence id never appears in prose, and no raw `[n]` survives into the file — which also keeps a citation at the start of a line from being parsed as a Markdown reference-link definition.

### Why an ordinal rather than an id

A dense `1..k` space, with `k` typically tens, is one where **fabrication is arithmetic**: `[47]` from a call that sent 12 exemplars is wrong by inspection, with nothing to look up. There is no near-miss, and no plausible-looking member to invent. An occurrence id has the opposite property on both counts, and it is the ledger's handle anyway — the reader's handle is the root-relative path ([ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md)), which the membership entry already renders.

### The check, in full

**Every `[n]` parsed out of the returned prose satisfies `1 ≤ n ≤ k`.** That is all of it.

An in-range ordinal resolves to a surviving occurrence **by construction**, not by lookup: Vespera built the mapping in the same call, from rows it had just read out of the ledger. So ADR-026's "every cited id must exist and survive" is not a query here — it is an invariant, and this is the strongest and the cheapest form the check can take.

**"Cited a survivor that is not in this cluster" is retired rather than answered.** It was the open question the ticket asked, and the numbering removes it: the ordinal space covers only what this call sent, and what it sent is a subset of one cluster's members. There is no ordinal that names a document in another cluster.

### Uncited prose fails the cluster

**A synthesis doc with no citation at all is not written.** It is the one case where the check passing means nothing was checked: prose that reads as though it were written over named documents, with no claim in it traceable to one. Given that the only other check on generated content is a human reading it later, a doc offering that reader no thread back into the corpus is worth less than the visible hole it is replaced by.

**There is no per-sentence requirement and no coverage requirement.** A doc citing 5 of the 40 exemplars it read passes. Whether a cited document actually supports the sentence it is attached to, and whether the uncited sentences should have been attributed, are judgements — exactly the judgement ADR-026 refused to hand to a model, and no mechanical check reaches them.

### A failed check fails the cluster, and nothing is retried or repaired

It joins ADR-108's three verification failures on the same terms: **a recorded fault, not a stopped run**. The deliverable keeps a hole, and the hole carries the cluster's 6a label as its heading ([ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md)).

- **Not regenerated.** A second call made *because* the first one's output failed a check is a model checking model output at one remove, which ADR-026 refuses; it also turns a bounded one-call-per-cluster cost into an unbounded one.
- **Not stripped.** Deleting the offending marker leaves prose that reads as sourced, having quietly removed the source. Of the four options the ticket listed, this is the only one that makes the artifact *less* honest than failing.

### Human review is after the run, and outside the tool

ADR-026's second half survives as a practice, not as a stage. With no consolidation gate left, the human reading is the operator reading the deliverable once the run has ended — no verdict written, nothing in the pipeline waiting on it, nothing to approve.

What the tool owes that reader is the path: **a citation is one click from its membership entry, and the membership entry is one click from the original** in the archive ([ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md)). That chain resolves with no database and no network, which is ADR-103's self-containment test applied to the one thing a reviewer actually does.

## Consequences

**The check is on the rendered text, so a well-written doc can fail on one bad number.** A model that writes `[12]` after a call that sent nine exemplars loses the cluster, prose and all. Accepted: the alternative is a rule that repairs model output, and every such rule is a judgement about which part of the output to believe.

**A numeric bracket the model did not mean as a citation is treated as one.** Only digits inside brackets are read as citations, so `[see below]` is prose while `[3]` is a citation whatever it was meant to be. The prompt reserves the notation, and an ambiguity that fails loudly is the right side of this trade.

**The membership list gains an order and a set of anchors it did not have before.** ADR-104 fixed what it lists; this record fixes that it is numbered in relevance-score order, which is also what makes ADR-108's *"written from the 40 highest-scoring of 412"* checkable by a reader: entries 1 to 40 are the ones the prose could have been written from.

**`docs/architecture.md` now misstates this in two places** — §1's stage table ("citations resolved to occurrence ids") and §2's verification bullet ("every cited occurrence id must exist, survive, and be reachable in the tree", "human review at the consolidation gate"). Both are corrected when the slice's documentation lands with the hand-off spec, in the rhythm ADR-105 and ADR-107 already follow.

**`CONTEXT.md` gains *citation*.** The word now names something specific — an ordinal into one call's exemplars, rendered as a link into the membership list — rather than being a general-purpose word for a reference.

**Where a cluster fault is recorded is still open.** This record adds a fourth way for a cluster to fail, and a cluster is not a file occurrence, so no verdict row is written against one. Two records now lean on "a recorded fault" without saying where it lands; that is its own decision.
