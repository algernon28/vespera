# ADR-253 — Every decision record holds its decision, and no history

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13.
- **Amends**: [ADR-249](0249-a-decision-record-holds-its-decision-and-no-history.md) §4 and its *What this does not decide*, which left the records before ADR-237 as written but for what they said of runs made. It carries a note at its head.
- **Keeps**: ADR-249 §1 to §3.

## Context

ADR-249 held ADR-237 onward to its rule and left the records before them as written. Those records tell their making as the later ones did: whose answer settled a point, which questions were put and how they were answered, which pull request or commit carried a change, which test failed before the fix, what a build printed, and a Status line that recounts how the record was accepted. A reader of an older record has to tell the decision from the story of it, and the two kinds of record read differently for no reason the decisions give.

## Decision

### 1. ADR-249's rule holds for every record

Every record from ADR-001 on holds its decision, the context it answers, its reasons and the measurements they rest on, the alternatives refused, its consequences, what it leaves undecided and the tests that hold it, and nothing of its making. Its Status says it is accepted, with the date where it gives one. ADR-001 to ADR-252 are rewritten to it; no decision, number, name or identifier changes, no record is renumbered, and no numbered section or numbered point loses its number.

### 2. A point an answer settled is stated as a decision

A section that put questions and recorded the answers keeps its number and states each point as decided, under the number it had. A reference elsewhere to a question of that section names the point by the same number.

### 3. A reconstituted record keeps its verbatim summary

ADR-001 to ADR-049 keep the note that marks them as reconstituted and the summary transcribed verbatim from the digest; only the notes added at their heads since are held to §1.

### 4. The index follows the records

Each line of `docs/adr/README.md` says what its record decides, amends, keeps or moves, and nothing of its making.

## Consequences

- A record of any age reads as its decision alone.
- No code changes and no run id moves.

## Tests

| Class | What it holds |
| --- | --- |
| `ADecisionRecordHoldsItsDecisionAndNoHistoryTest.noRecordTellsItsMaking` | every record's Status says it is accepted, with its date where it has one, and nothing else; no record and no line of the index carries a pull request link, a commit's hash, an answer of the operator's, a first failure, the agent that worked it or a code inspection of its making |
| `ADecisionRecordHoldsItsDecisionAndNoHistoryTest.aRecordTellsNothingOfItsMaking`, unchanged | ADR-237 onward, and their lines of the index, are held to ADR-249's wider set of words |
| `ADecisionRecordHoldsItsDecisionAndNoHistoryTest.noRecordSaysWhetherARunHasBeenMade`, unchanged | no record and no line of the index says whether a run was made |

## What this does not decide

- `docs/decision-ledger.md` and `docs/architecture.md`, which condense the records and are not records themselves.
