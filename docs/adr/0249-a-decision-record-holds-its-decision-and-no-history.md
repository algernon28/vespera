# ADR-249 — A decision record holds its decision, and no history

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13.
- **Amends**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1 and §12, in what they say of runs made, and its Status line (§3). It carries a note at its head.
- **Applies to**: every record, and the index in `docs/adr/README.md`; ADR-237 to ADR-248 are rewritten to it (§4).

## Context

A record that says whether the operator has used `vespera run` states a fact of one working directory on one day. The repository cannot check it, and it is false from the operator's next run on, while the record still reads as true. ADR-216 §1 lets a change move a module's run ids only for a reason of its own or beside a change that already moves them, and whether those ids are already moving depends on whether the operator has used `vespera run` since; ADR-216 and ADR-243 wrote down an answer to that, and ADR-245 to ADR-248 cited it.

Records ADR-237 to ADR-248 also told how they were made: what found the defect, who worked it and in which pull request, which commit it was written against, what the operator was asked, which test failed before the fix, and what a build printed. None of it is a decision or a reason for one, and all of it is kept where it belongs: in the pull request and the commit message.

## Decision

### 1. A record holds a decision, its context and its reasons

A record states what is decided, the problem in the code or the design it answers, the reasons, the measurements the reasons rest on (as AGENTS.md's *measure rather than argue* asks), the consequences, what it leaves undecided, and the tests that hold it. Its Status gives the date it was accepted. Its head lines say which records it amends, keeps or applies.

### 2. What stays out of a record

- **The state of a working directory or of the operator's machine**: whether the operator has used `vespera run`, what a working directory holds, what is pending at the next run.
- **The history of the record's making**: what found the defect, who worked it, a pull request, the commit it was written against, a question put and the answer given, a test's first failure or a mutation that failed it, and a build's output.

A record that moves run ids says which stages move (ADR-058). Whether that costs a pass of its own is settled when the change is merged, against what the operator says then, and said in the pull request.

### 3. ADR-216's check reads the run state when a change is merged

ADR-216 §1 stands: a change moves a module's run ids for a reason of its own, or beside a change that already moves them and has not yet been run. Whether one has been run is asked of the operator for each such change and answered in its pull request, not in a record. ADR-216's Status and §12 lose the answer they recorded.

### 4. ADR-237 to ADR-248 are rewritten to it

Each keeps its decisions, context, reasons, measurements, consequences, scope and tests; each loses what §2 names. No decision changes, and no record is renumbered. The index lines of ADR-237 to ADR-248 lose the same.

## Consequences

- A record reads the same whatever has been run since it was written.
- A pull request that moves run ids says whether the operator has used `vespera run` since the last change that moved them.
- No code changes and no run id moves.

## Tests

| Class | What it holds |
| --- | --- |
| `ADecisionRecordHoldsItsDecisionAndNoHistoryTest.noRecordSaysWhetherARunHasBeenMade` | no record and no line of the index says whether a run was made |
| `ADecisionRecordHoldsItsDecisionAndNoHistoryTest.aRecordTellsNothingOfItsMaking` | from ADR-237 on, a record's Status is the date it was accepted, and no line names what found the defect, who worked it, a pull request, a commit, an answer it was given, a test's first failure or a build's output |

## What this does not decide

- Records before ADR-237, whose Status lines and contexts name the tickets and answers they came from; each is held to §2 only for the run state, and is otherwise left as written.
