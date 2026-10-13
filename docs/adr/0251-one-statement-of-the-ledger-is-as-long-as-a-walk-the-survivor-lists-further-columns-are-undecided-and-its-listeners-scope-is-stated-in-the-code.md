# ADR-251 — One statement of the ledger is as long as a walk, the survivor list's further columns are undecided, and its listener's step scope is stated in the code

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13.
- **Amends**: [ADR-250](0250-stage-2-writes-the-survivor-list-of-the-files-it-converted-and-did-not-remove.md), in three sentences and nothing else. It carries a note at its head. The three:
  - §3, *"it is the pager of the reads `ledger` pages, and `Verdicts.eachExtractionFailure`, one sorted statement, is the read it does not page"* (§1 here);
  - *Alternatives refused*, *"A status and a confidence grade beside the three columns. Refused for now under §6"* (§2 here);
  - §4, *"step-scoped as `ReviewListListener` is and for the same reason (ADR-175 §7)"* (§3 here).
- **Keeps**: everything else ADR-250 decides: who is on the page, the page and its three columns, how it is read, when it is written and what is said, its names, and §6's check.
- **Read with**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md), row 7 of its table, and [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md) §3, which keeps it excepted; [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7; [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §2.
- **Rests on**: a reading of every statement of `ledger`, classed in the Context; the tests below.

## Context

Three sentences of ADR-250 say something the code or the record itself does not.

**What `KeysetPages` does not page.** ADR-250 §3 names one read. `ledger` holds three that are not handed out by keyset, and they differ in what bounds them. Every statement of `ledger` that brings back rows is one of four kinds:

| Kind | The statements | Rows to a statement |
| --- | --- | --- |
| Handed out by keyset, through `KeysetPages` | `Verdicts`: the one statement of `survivors` and `eachPageOfSurvivors`, `eachPageOfSurvivorsByPath`, `survivorsBySize`. `Occurrences`: `recordedBy`, which `sameOccurrences` reads each walk through, and `occurrencesOf` | at most `KeysetPages.ROWS_IN_A_PAGE`, 1,000 |
| Asked by key, about the occurrences the caller hands over | `Occurrences.pathsOf`, `Verdicts.survivingAmong` | at most 1,000, in as many statements as what is handed over needs |
| One statement, sorted | `Verdicts.eachExtractionFailure`, `ORDER BY o.path` | the run's `extraction-failed` verdicts, as many as a walk's occurrences at most |
| Everything else | a count; one row found by its key, or the first of a `LIMIT 1`; the upstream runs of one run; `PRAGMA database_list` | one, or a handful |

The two reads of the second kind are as long as what they are given, and `pipeline` gives each a whole seed partition: `ClusteringTasklet` asks `survivingAmong` about the members of one, and `GenerationTasklet` asks `pathsOf` for the members of one in one of its two calls, and for a page of the scoring run's members in the other. `similarity.DocumentFrequency` asks `survivingAmong` about a page.

**The two further columns.** ADR-250 lists a column for the converter's status and one for the confidence grade twice: under *Alternatives refused*, as refused for now, and under *What this does not decide*, among the columns an existing read does not hand over. A thing is refused or it is left undecided, and the record says both.

**The listener's scope.** ADR-250 §4 cites ADR-175 §7 for `SurvivorListListener` being step-scoped. ADR-175 §7 states no scope, and no record before ADR-250 states the scope of either listener that writes a page at the end of stage 2.

## Decision

### 1. The one statement of `ledger` as long as a walk is the read of stage 2's failures by path

**The one statement of `ledger` whose rows can be as many as a walk's is the `ORDER BY o.path` of `Verdicts.eachExtractionFailure`.** It is the exception to ADR-060's bound that ADR-218 records as row 7 of its table and ADR-224 §3 keeps.

**`Occurrences.pathsOf` and `Verdicts.survivingAmong` are not handed out by keyset either.** Each asks by key, at most `KeysetPages.ROWS_IN_A_PAGE` occurrences to a statement, and is as long as what it is given. Neither is an exception to ADR-060's bound: no statement of theirs brings back more than a page.

ADR-250 §3's sentence is read so: `KeysetPages` is the pager of the reads `ledger` hands out by keyset; what it does not page is those three reads, and one of them is a single statement. `KeysetPages`' javadoc says the same of all three.

ADR-218's row 7 gives the statement a line of `Verdicts.java`. The row means the `ORDER BY o.path` statement of `Verdicts.eachExtractionFailure` wherever it stands in the file, which is how ADR-224 names it, by what it reads.

### 2. A column for the converter's status or the confidence grade is not decided

**Whether the survivor list gains a column for the converter's status, or one for the confidence grade, is not decided by ADR-250.** Its *What this does not decide* says so and stands. The entry of its *Alternatives refused* that refuses the two columns *"for now under §6"* is withdrawn: neither column is refused.

ADR-250 §2 stands: the page has three columns. ADR-250 §6 stands as what either column costs: a read added to `ExtractionMetrics` moves stage 2 and every stage after it.

### 3. The scope of the two listeners is stated in `ExtractionJobConfiguration`, and ADR-175 §7 states none

**`SurvivorListListener` is step-scoped by its bean method in `ExtractionJobConfiguration`. No record before ADR-250 states the scope of either listener that writes a page at the end of stage 2, and ADR-175 §7, which ADR-250 §4 cites for it, states none.** The javadocs of the bean methods state the scope and its reason by reference: `survivorListListener` is step-scoped as `reviewListListener` is *"and for the same reason"*, and `reviewListListener` as `extractionFaultRecorder` is, *"and for the same reason: it is built only when the step first calls it, and asks for the run only then"*. That is the reason ADR-250 §4 gives after its citation, *"built only when the step first calls it"*.

- **ADR-175 §7 states no scope, and its case is held by a guard in each listener, not by the scope.** Its case is the step that failed having read nothing, which writes no page and asks for no run. `ReviewListListener.afterStep` and `SurvivorListListener.afterStep` each return, where the step did not complete and its read count is 0, before the one line that asks `StageRuns` for the run. Neither constructor asks for it, and `StageRuns` is job-scoped.
- **ADR-139 §2 states the scope of the recorder alone**, *"one step-scoped listener"*, and no reason for it.

ADR-250 §4's *"(ADR-175 §7)"* is read so: it names the case the guard holds, and no scope and no reason for one.

## Consequences

- **No code changes and no run id moves.** The change touches `docs` and `src/test`.
- **A column for the converter's status or the confidence grade is a decision still to be made**, at the cost ADR-250 §6 gives.

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.SurvivorListListenerTest`, unchanged | §3: a step that ended in failure having read nothing writes no page and asks for no run |
| `ledger.ReadsOfTheLedgerHoldOnePageTest`, `ledger.SurvivorsByPathTest`, `ledger.SameOccurrencesTest`, unchanged | §1: the reads handed out by keyset bring back a page at most to a statement |
| `ledger.SurvivingAmongTest`, unchanged | §1: `survivingAmong` names at most a thousand occurrences in a statement, each a lookup by key |
| `EveryStatementThatSortsIsRecordedTest`, unchanged | §1: `ledger.Verdicts` holds one statement that sorts |

No test holds §1's first sentence as a whole, that no other statement of `ledger` brings back as many rows as a walk holds; it rests on the reading in the Context. No test names `Occurrences.pathsOf`.

## What this does not decide

- Whether the survivor list gains either column of §2, or any other column an existing read does not hand over.
- Whether `Verdicts.eachExtractionFailure` is bounded. ADR-224 §3 keeps it excepted.
- Whether `pipeline` hands `pathsOf` and `survivingAmong` a page and not a seed partition.
- The scope of either listener, or what a stage 2 without it does. §3 says where the scope and its reason are stated; no test holds the scope, and none was made of a step without it.
