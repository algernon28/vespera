# ADR-125 — An absent title and a blank one are told apart by what each left on disk, and ADR-124's owed guarantee is spent

- **Date**: 2026-09-19
- **Status**: accepted
- **Amends**: [ADR-124](0124-both-fields-of-a-parsed-answer-are-required-and-missing-writing-is-a-schema-violation-rather-than-an-uncited-one.md) — in two places, and in neither does its rule move. Its `title` half is built, so the bullet it records as **owed** is spent and is recorded as spent here rather than struck there. And its justification for `title`'s two details being distinct is restated on a ground that survives this branch: the one it gave is stated in terms of what the two states cost on `main` today, which is exactly what this record removes.
- **Rests on**: [ADR-123](0123-an-answer-carrying-nothing-is-a-schema-violation-and-only-guaranteed-response-fields-are-dereferenced.md) (the response-level half of the same rule, and the instruction that whichever record-level ticket lands second adds a branch to `parseAnswer`), [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (failures under one kind are told apart by their detail), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a rejected answer faults its cluster, the run keeps going, and a re-run skips a cluster already carrying a `synthesis_doc` row), [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) (the four checks, and each at the earliest point its own evidence is whole), [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) (the first refusal of a fifth fault kind), [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md) (what the vocabulary binds, and what it leaves alone).
- **Charted as [#216](https://github.com/algernon28/vespera/issues/216)**, under the stage 6a/6b map [#175](https://github.com/algernon28/vespera/issues/175). Surfaced by an architect re-gate of #186's branch, bounded by ADR-124, which states the scope line once: **#216 owns `title` entire, #224 owns `prose` entire.**

## Context

### What is left, and what is not

ADR-124 decided the **rule** for both required fields of a parsed `Answer`: presence is checked on the record, a missing one is a `SCHEMA_VIOLATION`, `ClusterFaultKind` stays at four. It built the `prose` half only, and said in terms that `title` is not covered — that a `null` `title` still reaches `synthesis_doc.title`, which is `TEXT NOT NULL`, and still raises a data-integrity exception out of `GenerationTasklet`, costing the step and every `cluster_fault` row in it.

So nothing about the kind, the placement or the shape of the guard is open. Two things are.

### One: #216 wants two details where ADR-124 took one

For `prose`, ADR-124 folded absent and blank into a single detail. For `title`, #216's acceptance criteria require the two to be **distinguishable by their recorded detail**, and ADR-124 blesses that as a counter-example rather than an inconsistency, on this ground:

> Its two title cases are different events with different histories: an absent title raises a data-integrity exception against a `TEXT NOT NULL` column and costs the step, while a blank one is stored, renders as a hole forever, and is counted as written.

That reasoning is about the two states **as they behave on `main` today**. This record is what stops them behaving that way: after this branch, an absent title and a blank one both fault their cluster, write nothing, and leave the run to carry on. Their consequences become identical, and the justification, taken literally, expires at the moment it is acted on. It needs restating before it is relied on, or a later reader finds a record defending a distinction on grounds the same record removed.

### Two: a bullet in ADR-124's rule section is owed to this ticket

ADR-124 states the record-level rule and then says the guarantee arrives in two pieces, `prose` today and `title` owed, and that the owed bullet

> is spent when #216 lands, and #216's own record is what records it as spent: decisions here are append-only, so the rule holds whole by a later record amending this one, never by an eraser taken to it.

This is that record.

### Measured: the readers #216's last two criteria name are not built yet

#216 asks that the cluster *"renders as a hole in the listing and is counted as one by the closing line"*, and writes about `Deliverable` and `NextAction` as though both were in the tree. Neither reader is. There is no `Deliverable` type — nothing writes `index.md`, and nothing under `src/main` mentions either name — and `NextAction` counts no `synthesis_doc` rows and names no cluster. What the ticket describes is the deliverable writer stage 6b has not been given yet, and its `isBlank()` is a property that writer will have rather than one anything has today.

**This record says so once and then argues from the row rather than from the reader**, because the reader is the part that is owed and the row is the part that is there. Everything below that needs a fact about a blank title takes it from `synthesis_doc.title`, which accepts one, and from ADR-111, which skips a cluster already carrying a row.

What the criterion is *about* is testable, and is tested: both readers, when they exist, read one thing — whether the cluster has a `synthesis_doc` row. A faulted cluster has none. The two readings of a hole are made to agree by making the divergent state unreachable, which is #216's own sentence, and the divergent state is *a row on disk whose title is blank*.

## Decision

### The two title details stay distinct, on the ground that one of the two states could leave a row behind and the other could not

ADR-124's test for when distinctness is owed is its own, and it is kept: *distinctness of detail is owed where the cases are different events, not as a tax on every branch.* What is replaced is the evidence that `title`'s two cases are different events.

**The durable difference is what each state was able to write to disk.**

- A **blank** title **is storable**, and before this branch it passed every check there was and was stored. `synthesis_doc.title` is `TEXT NOT NULL` and a blank string satisfies that, so the row was written; ADR-111 has a re-run skip every cluster already carrying one, so such a row can never be reached again by any repair pass. Nothing in a later build undoes a row an earlier one wrote — which is why the tense matters here, and why it is the storability rather than the storing that this ground rests on.
- An **absent** title could never be stored at all. It met the same `TEXT NOT NULL` from the other side and took the step down instead. No `synthesis_doc` row has ever been produced by it, anywhere.

So the two states leave different things behind them, and they go on differing after this branch, because what is on disk is not undone by a guard added later. An operator whose run reports *a blank heading came back* has a reason to go and look at whatever an earlier build wrote for that corpus, and possibly to remove a row ADR-111 will otherwise skip forever; an operator told *no heading came back* has no such work, because that state never produced a row to find.

**And it is exactly why `prose` folds and `title` does not.** ADR-124's ground for folding was that *neither case leaves anything behind and neither is repairable differently* — true of `prose`, where an absent one threw and a blank one was turned down as an uncited answer, and neither could write a row. It is not true here. The same test, applied to different facts, gives different answers; nothing about ADR-124's rule is amended, only the sentence that argued this case.

#### The limit on that argument, stated rather than left to be found

The story above is about rows that exist. **No build of this system has shipped**, so the population of blank-titled rows may well be empty, and nothing here has counted it. Taken strictly, what the previous section establishes is a difference between two *counterfactual* histories — what each state was able to leave behind — rather than a difference an operator is certain to meet. That is weaker than the plain reading of ADR-124's test, and it is recorded rather than glossed, because a later reader who notices it unaided will reasonably wonder what else was glossed.

**The distinction stands anyway, and on a second ground that needs no population at all.** #216's third acceptance criterion fixes distinctness independently of any argument about consequences, and ADR-124 blessed that criterion in terms — *"#216's scope is unambiguous afterwards: it owns `title`, including both of its details and both of its reader-level claims."* The second half of that quotation is the half this branch does not discharge, and it is quoted whole rather than cut at the convenient point: the reader-level claims are real, they are owed, and the bullet in Consequences names #186 as their owner. The argument above is what makes that criterion *reasonable* rather than what makes it binding. Where the two would come apart is a future in which the deliverable writer lands, no blank-titled row is ever found anywhere, and someone proposes folding the two details: that would be a decision to take then, on evidence nobody has yet, and against a ticket criterion that would have to be reopened rather than quietly dropped.

**The rival was folding the two now, for symmetry with `prose`.** It is refused. Symmetry between two fields is not a property anyone reads, and buying it costs the one distinction an operator can act on. It would also contradict a live acceptance criterion of an open ticket without settling that ticket, which ADR-124 refused to do for the guard itself and this record will not do for its wording.

### The two details

Under `SCHEMA_VIOLATION`, the class now records **six** reasons, and they stay mutually distinguishable by the `detail` alone:

| What came back | Detail |
|---|---|
| a call carrying no answer | `the call came back carrying no answer at all` |
| an answer whose text is null or blank | `the answer came back empty` |
| an answer nothing could read | `the answer did not read back into a heading and its writing: …` |
| a record with no writing in it | `the answer came back with no writing in it` |
| **a record with no title on it** | **`the answer came back with no heading on it`** |
| **a record whose title is blank** | **`the answer came back with a blank heading`** |

Six rows: the first phrased as *what the call did*, since at that point there is no answer to say anything about, and the other five as *what the answer did*, because that is what the operator is reading about. The two new ones say which of the two worlds this is, and neither names a number, a field name or a Java type.

**Neither says anything about the writing**, for ADR-124's reason applied the other way round: whether the writing arrived is not what this check established, and a detail claiming it is a claim nothing checked.

#### The names say *title* and the details say *heading*, and that split is deliberate

`CONTEXT.md`'s **Cluster title** entry is the entry for what the model returns, so under ADR-122 every name of ours takes its term — the constants, the test method names, this record's own title and filename. The `detail` strings are the other side of that rule: they are prose an operator reads, the entry carries no `_Renders as_` line, so nothing is imposed, and the reason sitting beside these two already reads *"did not read back into a heading and its writing"* on `main`. Changing them would churn text the vocabulary does not bind, so they stay. This is written down so the next reader does not take the mismatch for an oversight and reopen it.

### The title is checked first, in the order the schema names the fields

`parseAnswer` gains one branch, added to the method rather than rewriting it, which is ADR-123's instruction repeated by ADR-124. The order within the method becomes: the response-level empty guard, `readValue` with its catch, the title guard, the writing guard, then return.

**Reading in `ANSWER_SCHEMA`'s own order is the rule, and it is chosen rather than inherited.** `required` names `title` then `prose`; `SynthesisDoc` takes them in that order; so the first required field the schema names that did not arrive is the one reported. That needs no case analysis, and a third required field added later has one obvious place to go.

It decides one case that nothing decided before: an answer of `{}`, missing both, records the title reason rather than the writing one. Nothing in the tree pinned it — every fixture for a missing `prose` carries a title — so this is a free choice being made rather than a behaviour being changed, and it is pinned by a test of its own so that the order is a decision rather than the order the code happens to read in. That is what ADR-123 did for its own placement, for the same reason.

### `ClusterFaultKind` stays at four values

The fifth refusal, on the ground the four before it used: `SCHEMA_VIOLATION` already names *what came back could not be read into the shape the call imposed*, and an answer missing a field that shape requires is squarely that. A fifth kind would change no behaviour and no remedy — the cluster keeps its hole, and a repair pass asks again, identically.

### ADR-124's owed bullet is spent

ADR-124's rule section carries `title` as **owed**: `SynthesisDoc`'s title argument not covered, a `null` title still reaching a `TEXT NOT NULL` column, still costing the step. With this built, that bullet is spent, and the record-level rule

> **No check downstream of `parseAnswer` dereferences a field of the parsed answer that nothing established is there**

holds whole for both required fields. `SynthesisDoc`'s title argument is non-null and non-blank on the same ground its prose argument already was: `parseAnswer` established it, and only because of that.

The bullet in ADR-124 is left exactly as written. Decisions here are append-only, and a record that said something true when it was written goes on saying it — what changes is that a later record names it as answered. That is what this section is.

## Consequences

- An answer parsing to a record whose `title` is null, or blank, faults its cluster and the run continues. The `cluster_fault` rows written earlier in the step survive it, which is what the absent case cost on `main` and no longer does.
- **The live run-level defect on `main` is closed.** One malformed answer out of a serving engine no longer loses every fault the run had recorded — the outcome ADR-111 exists to prevent, reached through the third and last of the three holes ADR-123, ADR-124 and this record close between them.
- **A blank-titled `synthesis_doc` row becomes unwritable.** The two readings of a hole cannot disagree about it, because the state they would disagree in is no longer reachable. This stops new such rows and claims nothing about old ones: a row an earlier build wrote is still there and ADR-111 still skips it.
- **Nothing is changed in the two readers #216 rules out, and nothing could be.** #216 rules out changing `Deliverable` and `NextAction` — `isBlank()` staying as the floor under a row an earlier build already wrote, and neither reader being edited to make the two readings agree. This branch touches neither, and what makes that easy rather than a discipline is that neither exists yet: the constraint binds whoever builds them.
- **#216's fifth criterion is not discharged here, and is not ticked.** That a faulted cluster writes no `synthesis_doc` row is claimed by an invocation test, and that is the whole of what this branch can show. That the listing renders the cluster as a hole under its label and that the closing line counts it as one belong to **[#186](https://github.com/algernon28/vespera/issues/186)**, the deliverable tree — the index, the manifest and the closing line — whose own acceptance criteria already carry both halves: *a faulted cluster appears under its label as an entry with no link*, and *the invocation's last line names the path and the number of faults*. [#187](https://github.com/algernon28/vespera/issues/187) builds the cluster file beneath it. The criterion has an owner rather than a promise.
- **`ClusterFaultKind` still has four values**, for the fifth time and on the same ground.
- No `schema.sql` change and no `SynthesisSchema.VERSION` bump: the kinds and the row shape are untouched.
- **The tests are split across two levels, as ADR-124's were.** What the fault is, what each detail reads, that the six details stay distinct and that the title is named first are claimed against `docFor` directly, where that evidence lives; that the run carries on, that fault rows written earlier in the step survive, and that neither kind of answer leaves a row behind are claimed by invocation tests, because a step-level guarantee cannot be read off a single call.
- **Nothing is owed by this record.** ADR-124's target is met for both required fields, and its own edge stands as written: a field added to `Answer` later either joins the required set and is guarded in `parseAnswer`, or is optional and every reader of it guards itself.
