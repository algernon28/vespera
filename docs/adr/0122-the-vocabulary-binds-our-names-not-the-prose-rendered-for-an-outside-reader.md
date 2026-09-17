# ADR-122 — The vocabulary binds our names, not the prose rendered for an outside reader

- **Date**: 2026-09-17
- **Status**: accepted
- **Amends**: [ADR-052](0052-the-test-report-is-written-for-a-reader-outside-the-project.md) — its "report-visible text stands on its own" rule said what such text may not contain and left open what it should say instead. That gap is what let two words for one thing stand side by side. The rule is generalised here from the test report to every reader outside this project, and the one word it was needed for is fixed. Nothing else ADR-052 decided moves: the claim, the labels, the links and the single-file report are untouched.
- **Rests on**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) (README.md is written for the operator and AGENTS.md for us — the division this record generalises), [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) and [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md) (the arrangement page the operator approves on, which is where the rendering already happens), [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (the gate that page carries).
- **Raised by the architect gate on [#184](https://github.com/algernon28/vespera/issues/184)**, settled as [#213](https://github.com/algernon28/vespera/issues/213) under the stage 6a/6b map [#151](https://github.com/algernon28/vespera/issues/151).

## Context

### Two rules in this repository pointed opposite ways

`AGENTS.md` scopes `CONTEXT.md`'s `_Avoid_` lists to "identifiers, tests and commit messages". ADR-052 requires report-visible text — `@DisplayName`, `@Epic`, `@Feature`, `@Story`, the claims, the category names — to carry "no phrase that needs `CONTEXT.md` to parse", because the person reading an Allure report has no access to this repository.

For most of the vocabulary the two never meet: *file occurrence*, *walk*, *verdict* and *survivor* are all readable cold. **Cluster** is the entry where they collide head-on, and the collision is not an accident of wording. The entry spends a sentence saying that the everyday reading of the word is the wrong one — "an arrangement of relevant documents, never a set of interchangeable ones (that is a redundancy set)" — and the **Redundancy set** entry avoids "cluster" from the other side for the same reason. So the one term whose meaning cannot be guessed is the one term ADR-052's reader is guaranteed to guess wrong about, and `CONTEXT.md` is where the correction lives.

`CONTEXT.md`'s own **Cluster** entry then opens *"A group of documents within one seed partition…"*, using the word its `_Avoid_` line rejects, in the entry that rejects it.

### Measured: the tree had already answered, in two places, consistently

Nobody recorded this, but the practice is uniform and it is not the practice `AGENTS.md` describes.

| Where | Reader | Word used |
|---|---|---|
| `arrangement.html` (`ArrangementReport.render`) | the operator | *group*, *exemplar* |
| `cluster-sizes.html` (`ClusterSizeReport`) | the operator | *group*, *exemplar* |
| `README.md` | the operator | *group*, *exemplar* — "one file per group" |
| The generation prompt (`ClusterSynthesis`) | the model | *group* |
| The Allure report (three generation test classes) | an outside reader | *group* |
| `GenerationTasklet`'s log lines | us | *cluster* |
| Types, fields, columns, javadoc | us | *cluster* |

The operator approves the arrangement on a page whose table header reads **Group**, whose counts read "N group(s)", and whose closing paragraph explains what "a group holding one document" means. `README.md` tells them the deliverable is "one file per group". Neither is drift: both are written for someone who has never read `CONTEXT.md`, and both render the word for exactly ADR-052's reason, one stage earlier than ADR-052 was written for.

Meanwhile every log line in `GenerationTasklet` says *cluster*, and so does every type, column and doc comment in `synthesis`.

### What that rules out

The candidate rule #213 proposed — *report-visible test text may render `cluster` as `group`; production identifiers and operator-facing strings may not* — is **contradicted by the tree in the place that matters most**. `arrangement.html` is the most operator-facing string this system produces; it is the page a gate stops on. Enforcing that rule means rewriting the two pages the operator actually reads into the word `CONTEXT.md` itself says they will misread, in order to protect a vocabulary they cannot see. The clause is refused.

Its first half is right, and for a reason wider than tests. The axis is not test against production, and not string against identifier. It is **who is reading**.

## Decision

### The `_Avoid_` lists bind every name this project gives itself

Types, fields, methods, constants, enum values, table and column names, javadoc, log lines, exception messages, commit messages, records under `docs/`, issue text, and `CONTEXT.md`'s own entries. Everything in that list is read by someone who has `CONTEXT.md` open, or who is one file away from it. For all of them the entry's term is the only word, and a rejected synonym is a defect.

**A test identifier is a name like any other.** A method name, a constant, a fixture helper — none of them reaches the Allure report, because `@DisplayName` and the three labels are what the report shows. ADR-052's argument defends the text a reader sees and says nothing whatever about the identifiers beneath it, which is why `GROUP_NAMES`, `oneGroupPerDocument` and `carriesOnPastAGroupWhoseAnswerWasTurnedDown` had no defence and never did.

### They do not bind prose this project renders for a reader outside it

Four audiences, and one rule covering all four: the operator, reading `README.md` and the HTML pages; the generation model, reading a prompt; the reader of a test report, reading display names, labels and claims; and anyone else handed an artifact rather than the repository. None of them can reach `CONTEXT.md`, and none of them should have to.

For such a reader a term is **rendered**: replaced by the plain-English word that carries the meaning without the glossary. Rendering is required where the project's term would be read wrongly, and harmless where it would not.

### A term's rendering is written beside it, so it is one word and not each author's choice

The rendering goes on the entry in `CONTEXT.md`, as a clause of the `_Avoid_` line that would otherwise forbid it. An entry with no rendering stated has none, and its own term is used everywhere.

**Cluster renders as `group`.** That is the only rendering this record fixes, because it is the only one that had two words in use at once. *Exemplar* for a winning seed and *document* for a file occurrence are the same mechanism already running in `README.md` and the two pages; they are left as they are found rather than blessed here, because nothing about them is in dispute and a record that swept them in would be deciding four things while examining one.

### Why not let the outside reader see "cluster" and look it up

They cannot look it up — that is ADR-052's whole premise, restated. And the failure is silent rather than loud: "cluster" does not read as an unfamiliar term a reader stops on, it reads as a familiar one, and the familiar sense — a set of things that are much the same — is precisely the sense `CONTEXT.md` spends a sentence refusing. A term that fails by being understood incorrectly is worse than one that fails by being opaque.

## Consequences

**Three test classes are renamed, and their report text is not.** `GenerationInvocationTest`, `GenerationFaultInvocationTest` and `GenerationBreakerInvocationTest` keep `@Feature("Writing over the groups")`, every `@Story`, every `@DisplayName` and every claim exactly as they read; their identifiers and javadoc move to *cluster*. All three at once, because they sit under one feature in the report tree and a rename of one would put siblings on two words. Nothing any of them asserts changes.

**`ArrangementReport.Group` is a violation of this record, and it is production code this record does not touch.** The nested record `ArrangementReport.Group`, the `groups` component of `ArrangementReport.Partition`, the `bySeed` map in `ArrangementTasklet` and the exception message in `ClusterSynthesis.docFor` — *"the group \"…\" has no document that fits"* — are names and a message of ours, and they say *group*. The rendered prose in the same two files is correct and must survive the rename untouched, which is exactly why the change needs a reader rather than a replace-all. Owed to `spec-implementer`, tracked as a follow-up to #213.

**`CONTEXT.md`'s **Cluster** entry loses the word from its own first sentence.** The entry is ours, so the rule binds it.

**Nothing enforces this mechanically, and that is chosen rather than deferred.** `docs/check-claims.mjs` is the only claim-checking program here, and its remit — stated in its own header — is the claims `AGENTS.md` and `README.md` make about the tree; it never opens `CONTEXT.md`, and its header warns in terms against a green that is read as covering more than it does. Three further reasons, any one of which would be enough:

- *The lists are not checkable as a whole.* They hold `file`, `document`, `run`, `job`, `model`, `state`, `flag`, `error`, `index`, `score` and `step` — words every Java program uses for something else. A checker over them reports noise, and a noisy checker is one people learn to read past.
- *Narrowed to `group`, it stops enforcing the rule and starts pinning one word.* It would also need an allowlist before it were green on a clean tree: `Collectors.groupingBy`, SQL's `GROUP BY`, `resolveGroupSharingASize`, "a content-identity group" — plain-English grouping that names no cluster and breaks nothing. An allowlist maintained by hand is the rule written twice, in a form that drifts.
- *The distinction is an audience judgement, which no pattern holds.* The same five letters are correct inside `ArrangementReport.render` and wrong in `ArrangementReport.Group`; correct in a `@DisplayName` and wrong in the method beneath it. A check that could tell those apart would have to know who reads each string, which is the thing a person decides.

So this is a documented rule, stated where each half is read — the rendering on the `CONTEXT.md` entry it governs, the audience rule in `AGENTS.md` beside the sentence that scopes the lists — and pinned for this one word by the three classes renamed here.

**The working rule #186 is being built against is amended, in one direction only.** Its first clause stands: report-visible test text may render `cluster` as `group`. Its second clause — *production identifiers and operator-facing strings may not* — is split. Production **identifiers** may not, which is stricter than #186 needs and costs it nothing. Operator-facing **strings** may, and already do; anything #186 writes into `arrangement.html`, `cluster-sizes.html`, `README.md` or a generation prompt says *group*.

**The next collision has a place to be settled.** **Cluster label**, **Cluster title** and **Cluster fault** are three terms an outside reader meets in the deliverable and in a report, and none of them has a rendering stated. When one is needed it goes on its entry, and nothing has to be re-argued.
