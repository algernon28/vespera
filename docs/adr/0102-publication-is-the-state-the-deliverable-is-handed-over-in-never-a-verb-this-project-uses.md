# ADR-102 — Publication is the state the deliverable is handed over in, never a verb this project uses

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) — none of its substance. It named a correction to `CONTEXT.md` as a consequence and left the wording open; this record settles the wording, and settles it differently from the straight deletion the consequence implies.

## Context

[ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) struck publication from the design and listed four documents that carry the correction at their own altitude. `AGENTS.md`, `README.md` and `docs/architecture.md` were the easy three: each states project status, and a status sentence is rewritten by replacing it.

`CONTEXT.md` is not a status document. It is binding vocabulary, and going through it to remove the word turned up something the consequence list did not anticipate: **"published" was doing three separate jobs there**, and only one of them was ever about a destination.

| What it meant | Where | What that idea already is |
| --- | --- | --- |
| The document stays in the archive | **Representative occurrence**, **Redundancy set** | a **survivor** — the term exists, and the sentences were paraphrasing it |
| The document holds this text | **Containment** | *contains*; the word was loose rather than technical |
| The document has a place in what stage 6a builds | **Seed set**, **Seed partition**, **Cluster** | nothing — the idea is real and had no name |

The same borrowing had reached the code. Five classes in `src/main` — `UnusableSeed`, `UnusableSeeds`, `Ledger.occurrencesOf`, `SeedExtractionItemProcessor`, `SeedExtractionOutcome` — justify writing no verdict against a seed with the sentence "a seed is never published". That reasoning was always a step longer than it needed to be, and after ADR-101 its premise names something that does not exist. The true reason is shorter and survives the ADR untouched: a seed is not a candidate, so no kind in a vocabulary whose every member removes a candidate can apply to it.

## Decision

**"Publication" survives as an adjective describing the state the deliverable is handed over in. It is never a verb, and nothing in this project is ever said to publish anything.**

That rule keeps [ADR-025](0025-stage-7-is-an-adapter-not-a-stage.md)'s **publication-ready artifact**, which ADR-101 explicitly adopted as the terminus, while removing every use that implies an actor. Deleting the word outright would have cost the one phrase two records deliberately kept.

The three senses resolve as follows.

### Sense 1 folds into *survivor*, because it was never a second idea

"Chosen for publication" and "exactly one is published" describe a file occurrence carrying no blocking verdict. **Survivor** is that, and the glossary already warns against inventing synonyms for it. These entries now say *left standing* and *survives*.

### Sense 2 was not a term at all

**Containment** now says the container *carries* everything the contained document does. No vocabulary changes; a loose word is replaced by an exact one.

### Sense 3 gets a term: *arrangement*

The tree stage 6a builds is real, is named by three entries, and had been borrowing a destination's word for itself. It is now **arrangement**: the shape stage 6a gives the survivors, seed partitions at the top and clusters beneath. An order over survivors rather than a copy of them, which is the same distinction **survivor** draws against "the keep pile" and for the same reason — nothing in this system moves documents.

"Page tree" goes to `_Avoid_` with it. A *page* is a wiki's unit, and [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) makes the form of the deliverable the first open question of the 6a/6b slice — so a noun that quietly answers it is exactly the kind of inheritance that record exists to stop.

### The artifact entry stops answering a question that is open

**Publication-ready artifact** listed four parts: page tree, surviving originals, generated text, citations. ADR-101 says that composition is undecided and that what becomes of the surviving originals "now has no answer". The entry now names only what is decided — the arrangement, the synthesis docs written over it, self-contained and handed over as files — and says in its own words that the rest is the slice's first question.

A glossary asserting a settled answer is worse than one saying nothing, because it is read as inherited rather than as proposed.

## Consequences

**The 6a/6b slice starts with a word for its own output.** Charting it would otherwise have spent its first round inventing one, or worse, not noticing it had borrowed one.

**Five classes lose a premise that had stopped being true.** No behaviour changes and no test changes: the javadoc now gives the reason that actually holds, and the reason is shorter.

**`CONTEXT.md` gains one term and no entry is deleted.** Every rejected word is recorded under `_Avoid_` rather than dropped, so a reader who comes to the glossary with "page tree" or "published" in hand is told what to say instead.

**The rule is checkable by reading**, with one qualification this record got wrong when it was first written and corrects here: the rule binds *this project as the actor*. A sentence where something else publishes — NIST publishing a SHA-256 digest, Testcontainers publishing a property — is unaffected, and `ContentHashingTest` and `TestcontainersConfiguration` keep theirs. What is a defect is any sentence in which Vespera, a stage or the pipeline publishes something. None remain: the last of them was the withdrawn `vespera publish` subcommand, removed under ADR-101's own follow-up.
