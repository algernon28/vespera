# ADR-101 — The run ends at the generated documents; there is no publication stage and no publication target

- **Date**: 2026-09-11
- **Status**: accepted
- **Supersedes**: [ADR-002](0002-confluence-is-the-publication-target.md) — Confluence is not the publication target, because there is no publication target
- **Supersedes**: [ADR-023](0023-surviving-originals-stored-in-confluence-as-attachments.md) — nothing is stored in Confluence, as attachments or otherwise
- **Supersedes**: [ADR-024](0024-publication-is-terminal-and-one-shot.md) — there is no publication to be terminal or one-shot
- **Supersedes**: [ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md) — its rule that the pipeline never publishes survives in the only way it can: nothing publishes
- **Amends**: [ADR-025](0025-stage-7-is-an-adapter-not-a-stage.md) — stage 7 is not an adapter either; but its substance, that the pipeline terminates at a self-contained publication-ready artifact and that rendering it anywhere is a separate concern, is the whole of what this record keeps

## Context

The curation this tool performs ends when stage 6b has generated the connective material over the survivors. Everything after that — rendering the result into a wiki, uploading originals as attachments, deciding what a second run against an already-published space means — was recorded design and has never been code: `publication` has no package, `vespera publish` is a stub that refuses, and no wayfinder map has ever been charted for stage 7.

**The operator is content once the documents are produced.** That is the decision, and it is not a deferral. What was being deferred had a real ongoing cost even unbuilt: five decisions in the record describe a Confluence integration that shapes how every later decision is read, and the stage 6a/6b slice — the next real body of work — would have been charted against a destination nobody wants.

### What the record already said, and how little of it is lost

[ADR-025](0025-stage-7-is-an-adapter-not-a-stage.md) had already pushed publication out of the cascade: what the pipeline produces is a **self-contained, publication-ready artifact**, and getting it into Confluence "or elsewhere" is a separate adapter's concern. [ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md) drew the same line from the other side: the pipeline runs unattended through 6b and stops, and publishing is a distinct, always human-initiated invocation.

So the boundary this record acts on is one the design already drew. **What changes is only what sits on the far side of it: nothing.** The artifact is still the terminus, it is still self-contained, and it is still the thing a person does something with. There is simply no longer a piece of this project that does that something.

[ADR-021](0021-synthesis-exists-to-make-the-survivor-set-coherent.md)'s synthesis and [ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md)'s 6a/6b split are untouched. They are about making the survivor set coherent — a page tree named by seeds, cited overviews per cluster, a human gate between the two — and none of that was ever about Confluence.

### What is genuinely lost, stated rather than glossed

**The tool no longer has an end-to-end story.** "A folder in, a wiki out" was a claim anyone could evaluate; "a folder in, a directory of arranged and generated documents out" asks the operator to do the last step themselves, and that step is not nothing. This is a narrowing of what the tool promises, taken deliberately.

**ADR-023's reasoning about originals is discarded, not deferred.** It argued that surviving originals belong beside the synthesised pages as attachments, so the result is self-contained and searchable rather than a set of links into a directory nobody backs up. That argument was about Confluence's storage, and it does not survive the target's removal — but the underlying question it answered does, and it now has no answer: **what becomes of the surviving originals** is something the 6a/6b slice has to decide rather than inherit.

## Decision

**The pipeline's last stage is 6b. What it produces is the deliverable, and nothing in this project renders, uploads or transmits it anywhere.**

### Seven stages, not eight

Census (0), byte-level reduction (1), extraction (2), content census (3), content redundancy (4), relevance (5), arrangement (6a) and generation (6b). Stage 7 is struck from the record: not moved, not deferred, not left as an adapter — struck.

### `publication` is not a module

The recorded module list is **eight**, not nine: `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `profile`, `pipeline`. Seven exist as packages; `synthesis` is the one that is recorded design and no code, and the only one still owed.

### `vespera publish` is withdrawn

The CLI surface is one command, `vespera run`, plus `vespera label` for the operator's own labelling pass. The `publish` subcommand stub — which today exists only to refuse — is removed along with the README section documenting its refusal. **That removal is code and is not done by this record**; it is a follow-up, and until it lands `vespera publish` refuses for a different reason than it used to.

### What 6b writes becomes the first question of the 6a/6b slice, not a detail inside it

This is the obligation this record exists to hand forward, and it is sharper than it was.

While an adapter existed, "a self-contained publication-ready artifact" could stay abstract: the adapter's shape would have settled what the artifact concretely was, and the arrangement could be held in the ledger as rows describing a tree. **With nothing downstream, the artifact is the product**, and its form — a directory of Markdown mirroring the page tree, a single document, something else — is the first thing the slice has to settle, because every later decision in it is downstream of that answer.

Two questions come with it, neither answerable here:

- **What becomes of the surviving originals** — copied beside the generated documents, referenced by path, or left where they are. ADR-023 answered this for a Confluence space and its answer is gone.
- **Where the output lands.** The working directory holds derived state a re-run may delete ([ADR-066](0066-the-working-directory-is-configured-not-discovered.md)'s configured working directory); a deliverable is not derived state in that sense, and writing it there without deciding so would make it deletable by a stage re-run.

## Consequences

**Five recorded decisions stop describing this system**, and are marked superseded rather than deleted: the record keeps its history, and a reader who finds ADR-002 through the decision ledger is told in the first lines that Confluence is no longer the target. ADR-025 keeps its substance under an amendment, because its "publication-ready artifact" is exactly the terminus this record adopts.

**`docs/architecture.md` loses its stage 7 row, its publication module, and both mermaid nodes for them**, and its "publication is terminal, one-shot, separate" section becomes a statement that the pipeline terminates, full stop. `AGENTS.md`, `CONTEXT.md` and `README.md` carry the same correction at their own altitude. This is the cost of the decision being cheap in code and expensive in prose: nothing needs rewriting, but four documents describe a shape that has changed.

**`check-claims.mjs` needs one edit** — its module-list rule matches the literal phrase "of the nine", which is now "of the eight". The checker is the thing that keeps these documents honest, so a claim rule that no longer matches would silently stop checking rather than fail loudly.

**The `publication` module's absence stops being a gap.** `ModuleBoundariesTest` asserts that every package under the application root is one of the recorded modules, and that recorded set shrinks by one. Nothing in it was ever built, so no code moves.

**Nothing about stages 0 to 6b changes.** No verdict, no identity, no threshold, no schema. The curation this tool performs is exactly what it was; what changed is where it stops being this tool's problem.

**The cost is named once more, plainly**: an operator who wanted a wiki now has to make one. The tool tells them which documents survived, arranges them, and writes the connective material — and hands the result over as files.
