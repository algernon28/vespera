# ADR-258 — The records' rules are checked in the claims job too, from one list of words, and every link between records resolves

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Settles**: where the rules of [ADR-249](0249-a-decision-record-holds-its-decision-and-no-history.md) and [ADR-253](0253-every-decision-record-holds-its-decision-and-no-history.md) are enforced, and that a link from one record to another opens a record.
- **Applies, and does not amend**: [ADR-249](0249-a-decision-record-holds-its-decision-and-no-history.md) and [ADR-253](0253-every-decision-record-holds-its-decision-and-no-history.md): what a record may hold is as they decide it.

## Context

The rule that a record holds its decision and no history of its making is held by one test, `ADecisionRecordHoldsItsDecisionAndNoHistoryTest`, which runs with the whole build. The claims job, `docs/check-claims.mjs`, runs on every pull request in seconds and reads the same Markdown, but did not read these rules; a record that broke one was found at the end of the full build.

A record also links to another by its file name. A record renamed, or a name mistyped, leaves a link that opens nothing, and no check read the links between records.

## Decision

### 1. One list of words, read by both checks

`docs/decision-record-markers.txt` holds the words the rules need, one per line: a name, a tab and a regular expression, with `#` starting a comment. The test and the claims job both read it, so a word changed or added is changed for both. A leading `(?i)` makes a word case-insensitive in both.

### 2. The claims job holds the rules

`docs/check-claims.mjs` holds, for every record and for the index, what the test holds: no line says whether a run was made; a Status is acceptance and, from the first record without history, the date of it; no record tells the history of its making, and from that record on the wider words are kept out of the record and of its line of the index. It fails when the list is missing or lacks a word.

### 3. A test holds the list

`ADecisionRecordHoldsItsDecisionAndNoHistoryTest` names the eight words the list holds and holds that each, but the number of the first record, compiles as a pattern, so that a word dropped from the list fails the build and not only the claims job.

### 4. A link between records resolves

The claims job fails for a link whose target is a file name of the form `NNNN-….md` (with or without an anchor) that is no record of `docs/adr`, and names the record that now has that number. A link to a file outside the form, such as the name in an example, is not a link between records.

### 5. Run ids

The change is to `docs`, `src/test` and the claims job. No class under `src/main` changes, so no stage's version moves.

## What this does not decide

- Which words the list holds: those of ADR-249 and ADR-253, as they stand.
- A link to a section of a record, or to a file that is not a record.
