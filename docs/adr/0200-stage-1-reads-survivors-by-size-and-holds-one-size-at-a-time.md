# ADR-200 — Stage 1 reads its survivors by size and holds one size at a time

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) §1 and §2, in what they say of the drain and nothing else. §1's *"with their behaviour unchanged"* stands for every rule and no longer for how either pass reads its survivors: the second pass is changed here, and the first in how it is fed. §1's fifth thing moved, *"`drain`, which both passes use"*, and §2's package-private `SurvivorDrain`, *"which holds `drain` for both passes"*, are deleted: `corpus` has no such type. §1's *"Both passes still drain the survivor reader into a `List`, which departs from ADR-060. Fixing that is #405's job"* is what this record does.
- **Amends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4 and §7, in two phrases. §4's row for `Stage 1 (byte-level reduction, sizes read)` gives its total as *"the survivors the pass drained"*; it is now the survivors the pass will read, taken from `Ledger.survivorCount`. §7's *"`SurvivorDrain.drain` (both of stage 1's passes)"* leaves the list of drains that record left to #411: stage 1 has none. §5's contract is unchanged: `toSize(N)` once, `sized()` per survivor, then `toHash`, `hashed` and `supersededRecorded` (§3).
- **Amends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §6, §7, §9 and its Tests, for stage 1 and nothing else (§7 here). §6's two rows for `SurvivorDrain.drain`, *"the survivors to check"* and *"the survivors to size"*, are struck, and no row replaces them: stage 1 has no timed statement. §7's `CorpusStatement`, with `SURVIVORS_TO_CHECK` and `SURVIVORS_TO_SIZE`, is not written, `corpus` gets no statement interface, and `corpus.HashingProgress` and `CheckingProgress` extend none. §9's part (b) no longer names `corpus`, and so moves the run ids of stages 2 to 6b where it said 1 to 6b. The claim its Tests owed in `StageOneReportsItsLoopsInvocationTest`, and the contract test owed in `corpus`, are no longer owed. Every other row, enum and part of ADR-193 stands.
- **Rests on**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md), whose consumer contract is an item reader and *"never a materialized `List` of what could be a million-plus ids"*; [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), whose runs in scope the new reader reads through; [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md) and [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md), which hash only inside a size shared by two or more; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md); [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md); [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md); [ADR-187](0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md).
- **Settles** [#405](https://github.com/algernon28/vespera/issues/405).

## Context

ADR-060 says `survivors` is an item reader and never a `List` of the corpus. Stage 1 broke it twice, in `corpus` since ADR-188 moved the code from `ByteLevelReductionTasklet`:

1. `SurvivorDrain.drain` read the whole reader into a `List<OccurrenceId>`, and its two callers each kept the result: `BrokenOrOutOfScope.verdictSurvivors` for the first pass, which needs nothing but one id at a time, and `ContentIdentityResolution.resolve` for the second.
2. `ContentIdentityResolution.resolve` then read each id's size into a `Map<Long, List<OccurrenceId>>`, which holds every survivor id again, and held both until the last size was hashed.

The drain's own comment said both passes need the whole set. The first never did. The second needs each size whole and no size but the one in hand. Hashing already streams, so this is ids, not file contents: about as many `Long`s as there are survivors, and at stage 1 the survivors are every occurrence there is (ADR-060).

Three accepted records name the drain: ADR-188, which moved it; ADR-192 §7, which left it to #411 as a drain to be announced; and ADR-193 §6, which gave each of its two calls a timed line. #411's part (b) is not built, and would set out to time a call this record deletes. So this record says what becomes of those rows (§7).

## Decision

### 1. A second reader in `ledger`: the same survivors, in size order

`Ledger.survivorsBySize(RunId)` returns an `ItemStreamReader<SizedOccurrence>` over exactly the set `Ledger.survivors` reads, the same anti-join over the same runs in scope (ADR-156), ordered by `size_bytes` and then `id`. Every survivor of one size therefore arrives together, the lower id first, which is the order within a size the pass always had. The SQL stays in `ledger` (ADR-041, ADR-060). It pages by keyset, one statement a page that gives its connection back, and it is hand-written, not a `JdbcPagingItemReader`: the provider Spring Batch ships writes the next-page condition as an `OR` of two comparisons, and a row-value comparison `(size_bytes, id) > (?, ?)` with `size_bytes >= ?` beside it is what SQLite answers as one range of an index. `SurvivorsBySizeTest` asks SQLite for the plan of the statement the reader issues, taken as the reader issues it and not from a copy, and pins that the plan searches the new index and names no temp B-tree.

### 2. Content identity reads it twice: once counting, once holding one size at a time

`ContentIdentityResolution.resolve` reads the survivors by size twice, because ADR-192's contract needs the hash pass's total before its first hash and every `sized()` before that total. **The first read holds no survivor**: it reports `sized()` as each survivor is read, and keeps only the size in hand and how many share it, adding that number to the hash total when the size ends and it is two or more. Then `toHash` is called. **The second read holds one size at a time**: it hands each size to a visitor once its last member has been read, the visitor resolves a size of two or more, and the size is let go. A survivor whose size is its own is read twice and never hashed (ADR-057).

**The bound is the largest set of survivors sharing one size, plus one**, and not a constant: the pass cannot learn which files are the same without holding the files of one size at once, and the hash map within a size is what it always held. It is no longer the corpus. That bound is the second read's, the one that hashes; the first read is under it by holding none, and a test holds each read to its own (Tests). Zero-byte files, the likeliest very large size, are removed by the first pass (`BrokenCheck`: *"the file is empty"*) before this one reads. Whether any real corpus has a size so common that the bound still hurts is not measured here.

### 3. The progress contract stands, and the total is one count

`toSize(N)` is called once, first, with `Ledger.survivorCount(runId)`: the same anti-join as a `COUNT(*)`, and the same statement `ByteLevelReductionTasklet` already runs for the first pass's total. It is not a new kind of statement, and it is not measured on the whole-archive database on `H:`: this record does not claim it cheap there, only that stage 1 already pays it once and now pays it twice. ADR-193 §6 left `Ledger.survivorCount` to [#429](https://github.com/algernon28/vespera/issues/429) and named the places it is called; this adds one, in `ContentIdentityResolution.resolve`, inside `corpus` where `pipeline` cannot put two lines around it, and #429 is where that goes. `ContentIdentityResolutionReportsItsLoopsTest` passes with every claim as it was, one phrase of its javadoc reworded; `StageOneHoldsBoundedMemoryTest` pins the whole sequence over a fixture with several sizes.

### 4. The first pass streams

`BrokenOrOutOfScope.verdictSurvivors` opens the `survivors` reader and checks each id as it is read. It writes a verdict for each survivor it has already read, which removes nothing still to come: the reader pages by `id`, after the last one read. `SurvivorDrain` has no caller left and is deleted.

### 5. One index, and no schema version moves

`schema.sql` gains `file_occurrence_by_walk_and_size ON file_occurrence (walk_id, size_bytes)`. Without it every page of §1 sorts the whole walk's survivors, and a million-row walk would pay that a thousand times. It is an addition to a `ledger` table and changes no column, so it is not what ADR-059's version is for: a database that lacks it is not stale. **An existing ledger builds the index at start-up**, under `CREATE INDEX IF NOT EXISTS`, the first time it is opened by this build. ADR-187 §3 says so before and after, and since ADR-193's part (a) the build is a counted one: it writes `Start-up (building index file_occurrence_by_walk_and_size): about X% of N rows` between the two lines, over `MAX(rowid)` of `file_occurrence`, at ten steps a row for its two columns. `CorpusSchema.VERSION` and `LedgerSchema`'s stay. How long the build takes on the whole-archive database is not measured here. ADR-193's own *"No table, column or index changes"* described that record's change and is not amended.

### 6. What does not stay byte-identical, and what does

**The verdicts, the content hashes, the `superseded_by` rows and every reason are the same set.** The size groups are independent, and inside one the code is unchanged. **The order in which rows of different sizes are written changes**: they were written in the order each size was first met by id, and they are now written by ascending size. Nothing reads `verdict`, `content_hash` or `superseded_by` by insertion order: the survivors reader orders by `id` of the occurrence, and every report orders by path. Keeping the old order would mean knowing, before a size's rows are written, which size was met first, which is a second pass of its own and a structure as large as the number of sizes. That is the cost of the bound, and the record takes the change in order and not the cost.

**The change in order is accepted as it is.** The operator left the call to the session that gated this change, which accepted it on 2026-10-05 on the gate's evidence: no `ORDER BY` and no tiebreak is added anywhere to restore or to fix an insertion order, because nothing reads one. **And no test pins the new insertion order**, for the same reason: a test that held the rows to ascending size would promise an order no reader has, and would fail a later change that kept every row and wrote them otherwise. What is pinned is the set, and the order the pass works in, which is the reader's order and the progress contract's (Tests).

### 7. Stage 1 has no drain left to time, so ADR-193's two rows are struck

ADR-193 §6 timed `SurvivorDrain.drain` in each pass: one line before, one after with the duration, labelled *"the survivors to check"* and *"the survivors to size"*. **Both rows are struck and nothing replaces them.** Timing `Ledger.survivors` in `verdictSurvivors` and the two `survivorsBySize` reads in their place was the other way, and it is not taken, by ADR-193's own rule and not by a new one:

- **What ADR-193 timed was a wait, and the wait is gone.** Its row says of the total *"none: `Ledger.survivors` is a paged reader, many statements"*: the thing timed was never a statement but the call that ran every page before the pass did anything, in §1's words *"a capability call that is one or more statements and nothing else — no loop of its own, no file, no call to a model"*. No such call is left. Each read is now spread through a loop, a page at a time, between the items the loop works on.
- **Two lines around a read would time the pass and not the read.** In the first pass the read ends when the last survivor has been checked for damage, so the after-line would state how long the broken check took, files opened and all, which stage 1's own start and end lines already bracket. The same holds for the second read of the second pass, which ends when the last file is hashed.
- **Each of those loops already reports, under ADR-192, and better than a timed line would.** The first pass ticks `Stage 1 (byte-level reduction, broken check)` for every survivor read, over a total. The first read by size ticks `Stage 1 (byte-level reduction, sizes read)` for every survivor read, over a total: a counter where ADR-193 gave a duration. The second read by size ticks `Stage 1 (byte-level reduction, content hash)` for every file hashed.
- **What one page costs, which is not nothing and is not new.** A page returns at most a thousand survivors, and to find them it goes through every row of its range of the index, the ruled-out ones included, asking the anti-join of each. So a page's cost grows with the ruled-out rows in its range and has no fixed ceiling: the first page of each read by size passes over every zero-byte file the first pass removed (§2) before it returns a row. `Ledger.survivors` has the same property, in every chunk step of every later stage, over whatever earlier stages ruled out, and no record has timed a page of it. This record does not rest on a page being cheap; the three points above carry the decision.

**So `corpus` has no statement of ADR-193's**, and that record's §7 asks for an enum and an interface only *"for each module that has such a statement"*: `CorpusStatement` and its interface are not written, and part (b) does not touch `corpus`. The parked test `docs/adr/0193/tests/b/…/StatementStepsPerRowAreTheDeclaredOnesTest.java` no longer names `CorpusStatement`, in this change.

**One stretch is left with no line, and it is stated, not settled.** In the second read by size, a survivor whose size is its own is read and passed over, and nothing ticks until the next shared size is hashed. What that stretch costs is pages of the same read the `sizes read` counter has just reported from first survivor to last, so the operator has seen how long the whole of it takes before it starts. Whether it should tick a counter of its own is ADR-192's question, about a loop, and is not decided here (What this does not decide).

## Consequences

**Run identity moves for stages 1 to 6b** at the next commit, by ADR-058, because `corpus` changes: stage 1's implementation version is `corpus`'s alone, and every later run names its upstream. `ledger` changes too and is in no stage's implementation version, so it moves none on its own. No `config_consumed` text changes, so `RunIdentityGoldenTest` is unedited.

**An existing ledger builds `file_occurrence_by_walk_and_size` at start-up**, once, announced as §5 says. A new one has it from `schema.sql`.

**Two reads of the survivors, not one.** The cost is a second walk of an indexed range, and the saving is that neither holds the set.

**#411's part (b) is smaller by `corpus`** (§7): two timed statements, one enum, one interface and two owed tests fewer, and stage 1's run id is not moved by it.

**Other stages that drain survivors into a `Set` are not touched.** `SeedCorpusComparison` and `ConfidenceDistribution` do, each for a stated reason of its own, and this record settles stage 1 only, which is what #405 names. Their rows in ADR-193 §6 stand.

## Tests

| Test | Pins |
|---|---|
| `ledger.SurvivorsBySizeTest` | the survivors by ascending size and then id across three pages, the ruled-out ones absent; a verdict written for each survivor as it arrives losing none still to come; the plan of the statement the reader issues, captured from the reader, searching `file_occurrence_by_walk_and_size` on every page with the size bounded below (`size_bytes>`), which the `OR` spelling's plan lacks, and with no temp B-tree |
| `ledger.SurvivorsTest`, one new test | what §4 relies on: `Ledger.survivors` read over three pages with a blocking verdict written for each survivor as it arrives hands out every one, once, in id order |
| `corpus.StageOneHoldsBoundedMemoryTest` | the representatives, the `superseded-by` verdicts, the hashes and the `superseded_by` rows over four sizes, as sets; the whole progress sequence, size by size; while sizing, at every `sized()`, no survivor handed out and not yet reported; at the first hash, no more handed out than the first sizes and one; the first pass handed no survivor it is not yet at |
| `corpus.ContentIdentityResolutionReportsItsLoopsTest` | every claim as it was, and green; one phrase of its javadoc reworded |
| `corpus.ContentIdentityResolutionTest` | unchanged, and green |

**Not pinned, and why:** the order in which rows of different sizes are inserted (§6); the cost of the count, of the index's build and of the second read on the whole-archive database, which no test can see.

## What this does not decide

- Whether the other drains of the survivor set should be streamed too.
- The measured cost of the new index's build, and of the count, on the whole-archive database.
- Whether the second read by size should tick a counter for the survivors it passes over (§7).
- How `Ledger.survivorCount` is announced where it is called from inside `corpus` (§3): [#429](https://github.com/algernon28/vespera/issues/429)'s.
