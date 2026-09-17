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

Types, fields, methods, constants, enum values, table and column names, javadoc, log lines, exception messages, test identifiers, commit messages, issue text, the records under `docs/`, and `CONTEXT.md`'s own entries. Everything in that list is read by someone who has `CONTEXT.md` open, or who is one file away from it. For all of them the entry's term is the only word, and a rejected synonym is a defect.

**That enumeration is the rule's single statement, and the other two places state where it lives rather than what it says.** `AGENTS.md` and `CONTEXT.md`'s **Cluster** entry point here instead of restating it. Until now they did not: all three carried a list of their own, no two of the three agreed on what was on it, and between them they dropped methods, constants, enum values, table names, test identifiers, issue text and `CONTEXT.md`'s own entries. A rule written three times in a form that drifts is the failure this record cites below as its reason to refuse an allowlist, and it does not become acceptable when the thing drifting is the rule itself.

**A rejected synonym is a defect where it names the thing the entry defines, and nowhere else.** Plain-English grouping that names no cluster is untouched: `Collectors.groupingBy`, SQL's `GROUP BY`, `Matcher.group`, `resolveGroupSharingASize`, and every "content-identity group" in `corpus` stay exactly as they read. This qualifier sits in the Decision and not only in the enforcement argument below, where it first appeared, because without it the rule reads as a ban on five letters and anyone applying it mechanically would break working code.

**A test identifier is a name like any other.** A method name, a constant, a fixture helper — none of them reaches the Allure report, because `@DisplayName` and the three labels are what the report shows. ADR-052's argument defends the text a reader sees and says nothing whatever about the identifiers beneath it, which is why `GROUP_NAMES`, `oneGroupPerDocument` and `carriesOnPastAGroupWhoseAnswerWasTurnedDown` had no defence and never did.

### They do not bind prose this project renders for a reader outside it

Four audiences, and one rule covering all four: the operator, reading `README.md`, the HTML pages, and **the deliverable** — the Markdown tree stage 6b writes, its headings, its prose and its `index.md` alike ([ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md), [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md)); the generation model, reading a prompt; the reader of a test report, reading display names, labels and claims; and anyone else handed an artifact rather than the repository — the deliverable being the artifact this project exists to hand over ([ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md)). The deliverable is named here rather than left to the fourth clause because it is the one outside-facing surface that is also an output of a stage, and a surface nobody listed is a surface somebody argues about later. None of these readers can reach `CONTEXT.md`, and none of them should have to.

For such a reader a term is **rendered**: replaced by the plain-English word that carries the meaning without the glossary. Rendering is required where the project's term would be read wrongly, and harmless where it would not.

### An outside-facing surface is unbound, and a missing rendering is not a prohibition

Prose written for any of those four readers is free of the `_Avoid_` lists outright. A `_Renders as_` line says which word to prefer **where a rendering exists**; its absence says nothing at all, and in particular does not fall back to binding the surface to this project's own term.

So `README.md` and the operator's pages go on saying *document* for a file occurrence, no rewrite of any outside-facing surface follows from this record, and the only thing a `_Renders as_` line ever does is replace each author's choice with one settled word.

**The other reading is available, and it is refused here rather than left open.** On that reading an outside-facing surface would be bound *to the rendering*, and therefore, wherever no `_Renders as_` line exists, bound to the project's term after all — which would make this record's own enumeration of four audiences the trigger for rewriting every page the operator reads, in the name of a glossary they cannot open. That is the outcome the Context section rejects in so many words. The lists bind our names. They do not reach prose written for someone who cannot see them, with a rendering or without one.

### A manifest header is a column name, not prose

**`documents.csv` keeps `cluster`** ([ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md), [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md)). A header row names columns. It is the same kind of name as a table's, which the binding list already covers, and it is read by a program before it is read by a person — the file exists so that whatever comes after the hand-off can be built without parsing Markdown or opening the ledger.

The manifest sits inside the deliverable, which the clause above makes an outside-facing surface, so the question is real rather than pedantic. Three facts settle it:

- **[ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md) fixes the strings verbatim** — "`documents.csv` gains `partition_order` and `cluster_order`" — and a rendering applied to the manifest would silently contradict a record this one does not amend. **ADR-112 needs no amendment**: this record simply does not reach that surface.
- **Every other column in the same row is already unrendered.** `occurrence_id`, `winning_seed`, `seed_partition` and `relevance_score` are all this project's own terms, carried into the header as they stand. Rendering one column out of seven produces a header half glossary and half plain English, which teaches a reader nothing and costs a consumer a special case.
- **`group` is a reserved word in SQL**, and loading this file into a table is the first thing the consumer [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) names would do with it. A column that cannot be selected without quoting is a cost paid for no reader's benefit.

The amendment recorded under Consequences enumerates the surfaces that do render — `arrangement.html`, `cluster-sizes.html`, `README.md` and the generation prompt. `documents.csv` is not among them, and this clause says so out loud, so that the omission cannot be read as an oversight by whoever writes the manifest.

### A term's rendering is written beside it, so it is one word and not each author's choice

The rendering goes on the entry in `CONTEXT.md`, as a `_Renders as_` line beneath the `_Avoid_` line that would otherwise forbid the word — beneath rather than inside it, so that the prohibition and the exception are never read as one sentence. An entry with no rendering stated has none, and its own term is used everywhere we name things ourselves.

**Cluster renders as `group`.** That is the only rendering this record fixes, because it is the only one that had two words in use at once.

**The two words beside it on those pages are not the same mechanism, and an earlier draft of this record said they were.** They are three different situations, and the difference is worth a sentence each:

- *Exemplar* is a rendering of nothing. The word does not appear in `CONTEXT.md` at all, and the **Winning seed** entry rejects only "best match" and "nearest seed", so no rule was ever in its way and none needs lifting.
- *Document* is rejected — under **File occurrence** and again under **Content identity** — and it stands in `README.md` and on the two pages anyway, because outside-facing prose is unbound. That is the clause above doing its work, not an exception to it, and saying so is the point: the word is permitted there because of the rule, not because anyone forgot it was on a list.
- *Cluster* is the only one of the three where two words were in use at once for one reader, which is what a rendering is for.

Neither *exemplar* nor *document* gains a `_Renders as_` line here: nothing about either is in dispute, and a record that swept them in would be deciding three things while examining one.

### Why not let the outside reader see "cluster" and look it up

They cannot look it up — that is ADR-052's whole premise, restated. And the failure is silent rather than loud: "cluster" does not read as an unfamiliar term a reader stops on, it reads as a familiar one, and the familiar sense — a set of things that are much the same — is precisely the sense `CONTEXT.md` spends a sentence refusing. A term that fails by being understood incorrectly is worse than one that fails by being opaque.

## Consequences

**Three test classes are renamed, and their report text is not.** `GenerationInvocationTest`, `GenerationFaultInvocationTest` and `GenerationBreakerInvocationTest` keep `@Feature("Writing over the groups")`, every `@Story`, every `@DisplayName` and every claim exactly as they read; their identifiers and javadoc move to *cluster*. All three at once, because they sit under one feature in the report tree and a rename of one would put siblings on two words. Nothing any of them asserts changes.

**`ArrangementReport.Group` is a violation of this record, and it is production code this record does not touch.** The nested record `ArrangementReport.Group`, the `groups` component of `ArrangementReport.Partition`, the `bySeed` map in `ArrangementTasklet` and the exception message in `ClusterSynthesis.docFor` — *"the group \"…\" has no document that fits"* — are names and a message of ours, and they say *group*. The rendered prose in the same two files is correct and must survive the rename untouched, which is exactly why the change needs a reader rather than a replace-all. Owed to `spec-implementer`, tracked as a follow-up to #213.

**Those four sites are not the whole of it, and the follow-up carries the count rather than this record.** Javadoc across `synthesis`, two log lines in `pipeline`, and test method names, constants and fixture helpers across several classes say *group* too — roughly ten times what the paragraph above enumerates. The inventory belongs on the ticket because a list of line numbers goes stale and a rule does not, but two things travel with it: the exclusion qualifier stated in the Decision, without which the work breaks `Collectors.groupingBy` and `Matcher.group`, and the instruction that rendered prose sitting in the same file must survive.

**A fixture's label values stay as they read, and this records why rather than leaving it to be re-derived.** `"Group one"` through `"Group nine"` in `GenerationBreakerInvocationTest`, and the three label strings beside them in `GenerationInvocationTest` and `GenerationFaultInvocationTest`, are values this project writes that reach no reader at all — they go to the scripted model and into a `cluster` row, never into a claim — and they stand in for a `cluster.label`, which *is* rendered to the operator, so the fixture reads as the thing it fakes. The constants naming them say cluster, because those are identifiers; an unrecorded convention in exactly these classes is how #213 came to exist.

**`CONTEXT.md`'s **Cluster** entry loses the word from its own first sentence.** The entry is ours, so the rule binds it.

**Nothing enforces this mechanically, and that is chosen rather than deferred.** `docs/check-claims.mjs` is the only claim-checking program here, and its remit — stated in its own header — is the claims `AGENTS.md` and `README.md` make about the tree; it never opens `CONTEXT.md`, and its header warns in terms against a green that is read as covering more than it does. Three further reasons, any one of which would be enough:

- *The lists are not checkable as a whole.* They hold `file`, `document`, `run`, `job`, `model`, `state`, `flag`, `error`, `index`, `score` and `step` — words every Java program uses for something else. A checker over them reports noise, and a noisy checker is one people learn to read past.
- *Narrowed to `group`, it stops enforcing the rule and starts pinning one word.* It would also need an allowlist before it were green on a clean tree, holding everything the Decision's exclusion qualifier already covers: `Collectors.groupingBy`, SQL's `GROUP BY`, `Matcher.group`, `resolveGroupSharingASize`, "a content-identity group" — plain-English grouping that names no cluster and breaks nothing. An allowlist maintained by hand is the rule written twice, in a form that drifts.
- *The distinction is an audience judgement, which no pattern holds.* The same five letters are correct inside `ArrangementReport.render` and wrong in `ArrangementReport.Group`; correct in a `@DisplayName` and wrong in the method beneath it. A check that could tell those apart would have to know who reads each string, which is the thing a person decides.

So this is a documented rule, **stated once here and pointed at from the two places it is read** — `AGENTS.md`, beside the sentence that scopes the lists, and the `CONTEXT.md` entry that carries the rendering — and pinned for this one word by the three classes renamed here. Neither of those two restates the enumerations, because that is what drifted.

**The working rule #186 is being built against is amended, in one direction only.** Its first clause stands: report-visible test text may render `cluster` as `group`. Its second clause — *production identifiers and operator-facing strings may not* — is split. Production **identifiers** may not, which is stricter than #186 needs and costs it nothing. Operator-facing **strings** may, and already do; anything #186 writes into `arrangement.html`, `cluster-sizes.html`, `README.md` or a generation prompt says *group*.

**The next collision has a place to be settled.** **Cluster label**, **Cluster title** and **Cluster fault** are three terms an outside reader meets in the deliverable and in a report, and none of them has a rendering stated. When one is needed it goes on its entry, and nothing has to be re-argued.
