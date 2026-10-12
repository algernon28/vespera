# ADR-242 — A page moved over its place is moved again a few times where the move is refused as access denied

> **Partly amended — see [ADR-244](0244-the-floors-answers-are-those-under-the-runs-embedder-identity-a-move-refused-as-a-sharing-violation-is-attempted-again-and-a-document-kept-for-another-is-checked-for-no-container.md).** Of §3 and the first item of *What this does not decide*: a move refused with a `FileSystemException` of no subclass, which is how Java gives a sharing or lock violation on Windows, is attempted again as one refused as access denied is, read by its class and never by its reason. A failure Java names by a subclass is still thrown at once.

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11, on the call of the session that worked the review register's deferred and partly done findings (*"fix the 4 deferred findings and the partly done"*). No question was put to the operator. The change had tests written first that failed on the code before it, and pass after it.
- **Amends**: [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3, *"moved over `arrangement.html` by one atomic move that replaces"*, and §6's step 7, the two moves of `index.md.part` and `documents.csv.part`: each is the one move of §1 here, attempted again where it is refused as access denied. ADR-223 carries a note at its head. And [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md), whose `ledger` owns identity and verdicts: `ledger` also holds the shared helpers every capability may name that hold no rule and name no table, `Placeholders` and `ReplacingMove` (§1). ADR-041 carries a note at its head.
- **Keeps**: ADR-223 §3 and §6: each page written whole beside its place and moved over it in one atomic move that replaces, so that a reader finds the old page or the new; a `.part` file a failure leaves is written over by the next invocation; the index is the last page of the tree to appear. ADR-223 §1: `ArrangementTasklet` names no capability type it did not, and no class file is added to `pipeline` (§1 here). [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7: the review list's failure is said in one error line and not thrown.
- **Settles**: review register finding R18: an atomic move with no second attempt, in `Deliverable`, in `ArrangementTasklet` and in `ReviewListListener`.
- **Rests on**: the code at `94b43f2`, and at `480dee1` once ADR-240 was on the branch; the tests below. Nothing was run on Windows for this record. No archive, working directory, database, log, report or deliverable of the operator's was opened for it ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

Three pages are written beside their place and moved over it by `Files.move(part, target, ATOMIC_MOVE, REPLACE_EXISTING)`: `arrangement.html` by `ArrangementTasklet` (ADR-223 §3), `index.md` and `documents.csv` by `Deliverable` (ADR-223 §6), and the review list by `ReviewListListener`, which its javadoc says is moved *"as `arrangement.html` is"*. On Windows that move is `MoveFileEx` with `MOVEFILE_REPLACE_EXISTING`, and a program that has the page or its place open without letting it be deleted, a virus scanner looking at a file just written and closed, an indexer, or a folder kept in step with a cloud, makes it fail with `ERROR_ACCESS_DENIED` for as long as it holds the file. Java gives that as `AccessDeniedException`. Each site tried once:

- At 6b the tree is written inside the step's transaction, after the walk over the clusters (ADR-223 §6). A failed move of the index throws out of the step, and the rollback takes every synthesis doc that invocation wrote, the most expensive calls the system makes.
- At 6a it fails the arrangement step.
- At stage 2 the review list says one error line, and the list before it stands (ADR-175 §7).

**What ADR-223 §1 bars.** A reviewer read §1 as barring `Deliverable` and `ArrangementTasklet` from sharing one move. §1 says that `ArrangementTasklet` names no capability type it did not name when that record was built, and that the record added no class file to `pipeline`; `PipelineHoldsOnlyTheRulesOnRecordTest` holds the first for the types of `corpus`, `extraction`, `similarity`, `embedding`, `synthesis` and `profile`, and does not list `ledger`'s, which every module may name (ADR-040). So a move of `synthesis`'s would be barred to `ArrangementTasklet`, and one of `pipeline`'s to `Deliverable`, which may not name `pipeline`; one of `ledger`'s is barred to neither.

## Decision

### 1. One move, in `ledger`

`ledger.ReplacingMove` moves a page over its place, and every one of the three sites goes through it: `ReplacingMove.ATOMIC.intoPlace(part, target)`. It lives in `ledger` for the reason `Placeholders` does: the one module every capability may name, so that `synthesis`'s tree and `pipeline`'s pages share one move without either naming the other, and no class file is added to `pipeline`. It holds no rule: what is written, and where, stays the caller's, so no stage's version comes to name anything it did not. That `ledger` holds such a helper is recorded here, as an amendment of ADR-041, which scoped `ledger` to identity and verdicts: a class may live in `ledger` that holds no rule, names no table and is a mechanism more than one module needs, as `Placeholders` already was without a record.

The move it makes is the platform's, `Files.move` with `ATOMIC_MOVE` and `REPLACE_EXISTING`, behind `ReplacingMove.Move`; the wait between attempts is behind `ReplacingMove.Wait`. A test builds one with a move refused as Windows refuses it and waits it counts; `Deliverable.writeTo` and `ReviewListListener` each take one through a form their tests call, and the shipped forms hand them `ATOMIC`.

### 2. Five attempts, the waits doubling from a tenth of a second

A move refused with `AccessDeniedException` is attempted again after a wait, up to **five attempts** in all, the waits **100, 200, 400 and 800 ms**, 1.5 seconds together. After the fifth refusal the last refusal is thrown as it came, the page still beside its place and the place as it was, and each site does what it did on a failed move: 6b's and 6a's steps fail, the review list says its error line.

The numbers are a judgement and not a measurement: a scanner's look at a page of this size is usually over well inside a second, and a refusal that does not lift is said within two seconds, so a page refused for good costs an invocation 1.5 seconds.

A thread interrupted while it waits stops waiting, keeps its interruption, and throws `InterruptedIOException` with the refusal beside it.

### 3. Only a refusal as access denied is attempted again

Any other failure of the move is thrown at once, as before: a full disk, a missing directory, a path too long. Those are not lifted by a program letting go of a file. Windows can also refuse a move with `ERROR_SHARING_VIOLATION`, which Java gives as a plain `FileSystemException`; this record does not attempt that one again (*What this does not decide*).

### 4. Which run ids move

The change touches `ledger` (`ReplacingMove`, new), `pipeline` (`ArrangementTasklet`, `ReviewListListener`) and `synthesis` (`Deliverable`). No stage's version names `ledger` or `pipeline` (ADR-222); `synthesis` is named by 6a and 6b, so on its own this change moves 6a and 6b. [ADR-240](0240-the-tree-asks-what-it-lists-of-each-survivor-a-page-of-survivors-at-a-time.md)'s change and ADR-237's, on the same branch, already move stages 2 to 6b, and [ADR-241](0241-the-manifest-asks-a-pages-places-together-a-clusters-synthesis-doc-is-read-once-and-a-survivors-pictures-are-looked-up-by-its-listed-key.md)'s 6a and 6b, so shipped with them it moves nothing more. No DDL, no page, no line and no verdict changes where no move is refused.

## Consequences

- **A page refused for a moment is put in its place**, and 6b's synthesis docs and 6a's rows are not rolled back for it.
- **A page refused for good is said 1.5 seconds later than it was**, with the same failure.
- **On Linux and macOS a refusal as access denied is a permission**, which no wait lifts, and costs the same 1.5 seconds before the same failure.
- **Every page moved over its place goes through one class**, and `EveryPageIsMovedIntoPlaceByTheMoveThatTriesAgainTest` fails a class that moves one itself.

## Tests

| Class | What it holds |
| --- | --- |
| `EveryPageIsMovedIntoPlaceByTheMoveThatTriesAgainTest`, new | that no shipped class but `ReplacingMove` names `ATOMIC_MOVE`. Failed first on `ArrangementTasklet` and `ReviewListListener` as they stood at `94b43f2` |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest`, new, three tests | that a move refused twice is made on the third attempt after two waits, the second longer than the first and neither over a second; that one refused every time gives up after five attempts and four waits, together no more than two seconds, throwing the refusal and leaving the page beside its place; and that any other failure is thrown after one attempt and no wait. The first two failed on the move as it stood, made once |
| `synthesis.TreeWhoseIndexIsRefusedForAMomentIsStillPutInPlaceTest`, new | that a tree whose index's move is refused twice is finished, its index and manifest the bytes a write with no refusal gives, and no `.part` file left. Failed first, the refusal thrown |
| `pipeline.ReviewListRefusedForAMomentIsStillPutInPlaceTest`, new | that a review list whose move is refused twice is put in its place whole, with the line that says where it is and no error line. Failed first, on the error line |

The site tests were run first against the move as it stood, made once behind `ReplacingMove`, so that each failed on the behaviour and not on a missing type. **Run against the change**, under Java 26 on Linux, with ADR-241's: 1,745 tests, none failing, 13 skipped by assumption as at `480dee1`, where 1,738 ran. **What no test here holds**: Windows. CI's NTFS job runs the suite on NTFS, where each move is made once and none is refused; what a scanner, an indexer or a folder kept in step with a cloud does to a move is known from how `MoveFileEx` reports a file held open, not from a run.

## What this does not decide

- A move refused as a sharing violation, `ERROR_SHARING_VIOLATION`, which Java gives as a plain `FileSystemException` and this record does not attempt again. Telling it from other `FileSystemException`s means reading its reason text, which Windows words in the user's language.
- A page refused for longer than 1.5 seconds: whether to wait longer, or to tell the operator which program holds it.
- The cluster files and picture files, which are written in place and not moved (ADR-223 §6).
