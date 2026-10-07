# ADR-205 — The relevance report counts the answers a model gave as it looks each up

- **Date**: 2026-10-06
- **Status**: accepted
- **Extends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4, whose table gains one row, and its Consequences, whose *"The relevance report writes its two counters on every invocation that reaches it"* now reads *three*. ADR-192's rule, its cadence and every other row stand.
- **Rests on**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §3 (a label a local model set has a provenance row, and the report reads those rows), [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md) §2 (the read of those rows is timed, as `the answers a model gave`) and §7 (which found the loop and left it to its own ticket), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move) and [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) §4 (a progress label is literal text).
- **Settles** [#444](https://github.com/algernon28/vespera/issues/444).

A *counter* is ADR-192's: one `StageProgress` over one loop.

## Context

`RelevanceReportTasklet` has two loops that look a recorded answer up in the ledger, one `Ledger.occurrenceId(walk, path)` statement an answer, to learn which occurrence of this walk the answer is about (ADR-097).

- `answersInThisWalk` goes through every answer recorded for the seed set. ADR-192 §4 gave it a counter, `Stage 5 (relevance report, answers matched)`.
- `modelAnswersInThisWalk` goes through the answers a local model gave for the seed set, read by `RelevanceLabels.modelAnswers`. It has no counter.

The second loop arrived with ADR-197, after ADR-192 had surveyed every loop at `f8f9a18`. ADR-204 found it while reading ADR-193 §6 again against the code, timed the read before it, and left the loop to #444, since a counter is ADR-192's rule and not a statement's.

Nothing here was measured. `vespera label --auto` has a local model answer the questions of the label file, which the report writes for a sample of up to sixty documents (ADR-088, ADR-197). Answers stay recorded from one run to the next, so the answers a model gave for a seed set can pass sixty; how many the archive's seed set has is not known, and neither is how long one lookup takes on its disk.

## Decision

### 1. The loop is counted

Its body reads the database, which is ADR-192 §1's first test. None of §1's four exclusions applies: it does not compute over values in memory; its count is set by how many answers a model has given and not by the code; it is a loop of the stage's own with one statement in each pass, not one statement or its row callback; and it is not inside another loop.

That it is short does not exclude it. ADR-192 §1 counts by what a loop's body does, and the operator's decision recorded there is that every stage's loops are counted whatever their duration. Its sibling is counted over the same kind of total.

### 2. The row ADR-192 §4 gains

| Stage | Label | One item | Total | Loop in | Part |
|---|---|---|---|---|---|
| report | `Stage 5 (relevance report, model answers matched)` | an answer a model gave looked up in this walk, matched or not | the answers a model gave for the seed set | `pipeline` | — |

- **The label** is the sibling's with one word before it, in §4's form: the stage as its own lines name it, a comma, the unit. Neither label begins with the other followed by a colon, so a reader of the log, and a filter on `<label>: `, tells the two apart.
- **"model" is ADR-204 §2's word** for whoever set these answers, in `the answers a model gave`, the line written just before this counter's. It names the local model of ADR-197, which CONTEXT.md's **Relevance label** entry calls by that word, and no instrument.
- **One item** is one pass of the loop: an answer whose path this walk does not hold is looked up and counted, as the sibling counts it (ADR-192 §2).
- **The total** is the size of what `RelevanceLabels.modelAnswers(seedSet)` returned: one for each provenance row of the seed set. This answers the ticket's second question with yes.
- **The two counters overlap, and that is right.** A model's answer is a recorded answer too, so the sibling goes through it as well, for the page's spread. This loop goes through it a second time for the label file's `labelledBy`. Each counter counts the lookups its own loop makes.

### 3. Where it is opened, and its cadence

**The counter is opened after the timed read of the answers a model gave has ended**, since its total is what that read returned, and before the loop's first lookup. It is ticked once at the end of each pass, after the lookup, on every path out of the pass but one that throws. So the report's lines in this part of the step are, in order:

```
Stage 5 (relevance report) is reading the answers a model gave
Stage 5 (relevance report) read the answers a model gave in <S> s
Stage 5 (relevance report, model answers matched): 1 of 3 (33%)
Stage 5 (relevance report, model answers matched): 2 of 3 (66%)
Stage 5 (relevance report, model answers matched): 3 of 3 (100%)
Stage 5 (relevance report) finished under scoring run <run>: …
```

The loop is the last thing the step does that writes a line of the report's before its finishing line: the label file and the profile are written after it. The cadence is ADR-192 §8's over a known total, unchanged.

### 4. Where there is nothing to count

| Case | What the report writes |
|---|---|
| No model has answered for the seed set | The timed read's two lines, and no line of the counter. The counter is opened over zero and writes nothing, as ADR-192 §4 says of a loop reached with no item. |
| The scoring run has no walk | Neither the read's lines nor the counter's: the method returns before the read, as `answersInThisWalk` does. |
| No seed set is named | Not reached. The step's gate is shut before it, and the label file's seed set is asked for before this method is called (ADR-169 §4). |

### 5. Text owed with the code

- `RelevanceReportTasklet.modelAnswersInThisWalk`'s Javadoc, which says what the method returns and nothing of its counter.

## Which run ids move

The change is in `pipeline`, which `StageModules` puts in the implementation version of stages 3 to 6b (ADR-058). **So it moves the run ids of stages 3 to 6b, and those of stages 0, 1 and 2 do not move for it.**

Part (b) of [#411](https://github.com/algernon28/vespera/issues/411) moves stages 2 to 6b (ADR-204 §1) and is not on `main` when this is written; this change is built on its branch. Landed with part (b), or after it and before the archive's next run, it moves no stage part (b) does not already move, and that run mints those ids once. **Landed after a run made on part (b), it costs a replay of stages 3 to 6b over that working directory**, which no record times.

No schema version moves (ADR-059).

## What was not taken

- **No counter, the loop being short.** ADR-192 does not count by length, and the operator decided against deferring any loop until it is measured.
- **One counter over both loops.** They are two loops over two reads with two totals, run at two points of the step with three other reads between them, four where the floor is a number (ADR-204 §5), and ADR-192 §1 gives each counted loop its own.
- **Folding this lookup into `answersInThisWalk`**, which already resolves every one of these paths. It would remove the loop and its statements. It is a change to what the step does, not to what it reports, and nothing has measured the lookups as a cost.
- **`Stage 5 (relevance report, answers a model gave matched)`**, the read's `<what>` word for word. Longer, and no clearer beside the sibling.
- **A note corrected in ADR-204 §7 in place of a record.** A label is a decision the log's readers and the tests hold, and `docs/adr/README.md` reopens a record only by a later one.

## Consequences

- **One file of production code changes**, `pipeline/RelevanceReportTasklet.java`.
- **Lines change by addition**: on every invocation that reaches the report with at least one answer a model gave for the seed set, at most one line an answer.
- **The relevance report has three counters**, where ADR-192's Consequences count two.
- **`StageProgressLabelsAreConstantsTest` holds the new label** with every other: it is literal text.
- **`AGENTS.md` changes its count and its range line only.** #444 is not a defect.

## Tests

| Test | Pins |
|---|---|
| `pipeline.TheRelevanceReportCountsTheAnswersAModelGaveInvocationTest`, `theReportCountsEachAnswerAModelGave` | over three answers a model gave, one about a path the walk does not hold, and one a person gave: the counter's label word for word, a line an answer, of three; the sibling's lines over all four; the counter's logger; and every line the report writes about itself in order, with the counter's lines after the read's closing line and before the finishing line |
| the same class, `theReportCountsNothingWhereNoModelAnswered` | over one answer of a person's and none of a model's: the read's two lines written, no line of the counter, and the finishing line next |

**Not pinned, and why:**

- **A scoring run with no walk.** No whole-job fixture reaches the report with one.
- **The counter's cadence over a large total.** `StageProgressVolumeTest` holds the cadence in `StageProgress`, which every counter is.

## What this does not decide

- **Whether the two lookups of a model's answer should be one** (above).
