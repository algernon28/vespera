# ADR-219 — Stage 3's grouping names the index on the run, `INDEXED BY shingle_by_run_id`, and the clause ships with the next change to `similarity`

> **Partly amended — see [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md).** One sentence of Sequences no longer holds as written: "The index is one for the table, not one for a run or a walk". There is one index named `shingle_by_hash` in a database, and since ADR-221 it is over the rows of one stage-2 run, the run of the stage 4b that built it last. The sequences stand as ADR-222 leaves them, an index of that name being in the database wherever this record found one, and the clause of §1 stands. Measured with ADR-221's index in the database, the grouping without the clause is not drawn to it, as it was to the index over every run's rows. What this record keeps of ADR-218, "its 183 bytes a row", is of the build ADR-221 replaces.

- **Date**: 2026-10-09
- **Status**: accepted. The clause of §1 is decided and not built: it ships with the next change that moves `similarity` (§2).
- **Built**: the clause of §1 was built on 2026-10-10 in pull request [#478](https://github.com/algernon28/vespera/pull/478), commit `5ae070b` of its branch, the change to `similarity` that [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) records; the test-side edits §5 lists are in the commit after it, in the same pull request. This line is the one §5 asks for; the Tests notes below are corrected in place for the same change, as `docs/adr/README.md` has such a note corrected, and nothing else in this record is edited.
- **Amends**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) in three sentences, all about which index stage 3 reads through. In its Measurements, *"With both `shingle_by_run_id` and `shingle_by_hash` present, SQLite still answers stage 3's read and the per-run delete through `shingle_by_run_id`"*; in its Consequences, *"Stage 3 reads the run's shingles in the order they were written"* and *"Until then stage 3 reads through whichever index SQLite prefers, which with both present is `shingle_by_run_id`"*. Each was true of the plain read stage 3 made when ADR-182 was written. None holds of the grouping ADR-211 put in its place wherever `shingle_by_hash` is built, SQLite answering it through that index there (Measured), and each holds of it in every state when §1's clause ships. What that record says of the per-run delete and of containment retrieval is not touched. ADR-182 carries a banner at its head saying so; its Tests note on what `ShingleIndexesInTheSchemaTest` asserts is corrected in place, as `docs/adr/README.md` has such a note corrected, and is not an amendment.
- **Amends**: [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) in what it measured of stage 3's grouping, every figure of which is of the plan without `shingle_by_hash`: its Measured's plan, its times, and *"The one statement's sort takes about 22 to 23 bytes a row in temporary files"*; its §9's words for the line before the grouping, *"sorts them in temporary files in the working directory"*; and §9's *"it ends short of a hundred: at about 79% on the two ledgers measured, at 82% where every hash is carried once, at 42% where one hash is in every row, at 98% in the shape that takes the most"*. Where the grouping meets `shingle_by_hash`, and until §1's clause ships, it writes no temporary file, took 4.8 to 6.2 times as long, and its progress ends lower (Measured). The 45 steps a row ADR-211 declares hold in both plans. ADR-211 carries a second banner at its head saying so, and is otherwise not edited.
- **Amends**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) in two places. The item *"Whether stage 3's grouping should ever run with `shingle_by_hash` present"* of its *What this does not decide*, with its *"no ticket holds it"* and its *"whether the 45 steps a row ADR-211 declares for that statement's progress hold under that plan was not looked at"*: [#473](https://github.com/algernon28/vespera/issues/473) held it, this record decides it, and the steps were looked at. And its Measured's *"That reading is of `StageModules` and ADR-182, and no invocation was run to see it"*: the invocations were run (Sequences). ADR-218 carries a banner at its head saying so, and is otherwise not edited.
- **Keeps**: ADR-182's drop by stage 2 and build by stage 4b, as they stand; ADR-211's one statement, its exception to [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s bound, its 45 steps a row and every line it words; ADR-218's exceptions and its 183 bytes a row.
- **Rests on**: the operator's four answers of 2026-10-09, given through the coordinating session to the session that wrote this record: that the plan is pinned by `INDEXED BY shingle_by_run_id`; that the clause ships with the next change that moves `similarity` and not now; that `ShingleIndexesInTheSchemaTest` is corrected and the claim about what `DocumentFrequency` sends lands with the clause; and that this record is ADR-219. Three more, given the same way after the gate of this record: that `AGENTS.md` names stage 3's case among what is known and open (Consequences), that the test of §2 stays, and that the test of the plain read stays for the contrast it shows (Tests). [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids a change moves, §4). [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md)'s ride-along rule (§2). A throwaway probe over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#473](https://github.com/algernon28/vespera/issues/473), which stays open until the clause ships (§2).

**An *index* here is always a SQLite index on a table.** It is never the ledger. *Grouping* is SQL's `GROUP BY` and names no cluster.

## Context

Stage 3 counts, in one statement, how many occurrences carry each shingle hash of stage 2's run (`similarity/DocumentFrequency.java`, ADR-211 §3):

```
INSERT INTO shingle_document_frequency (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)
SELECT ?, shingle_parameter_identity, shingle_hash, COUNT(DISTINCT occurrence_id), COUNT(*)
FROM shingle WHERE run_id = ?
GROUP BY shingle_parameter_identity, shingle_hash
HAVING COUNT(DISTINCT occurrence_id) >= 2
```

`shingle` has two indexes a start always makes, `shingle_by_occurrence` and `shingle_by_run_id`, and a third, `shingle_by_hash` on `(run_id, shingle_parameter_identity, shingle_hash)`, which stage 2 drops before it writes and stage 4b builds before it reads (ADR-182).

ADR-182 knew that reading a run through `shingle_by_hash` is slow, each row being fetched from a different page of the table, and recorded that with both indexes present SQLite answers stage 3's read through `shingle_by_run_id`. Stage 3's read was then a plain `SELECT` of the rows, counted in Java. ADR-211 replaced it with the grouping above. `shingle_by_hash` holds the rows in the grouping's own order, so where it exists SQLite reads through it and sorts nothing for the `GROUP BY`. ADR-211 measured the grouping without that index. ADR-218 measured it with the index, decided nothing, and read from `StageModules` that a run can meet it, without running one.

`ShingleIndexesInTheSchemaTest` went on holding ADR-182's sentence against the plain read, described as *"as `DocumentFrequency` sends it"*, after `DocumentFrequency` had stopped sending it. It passed and held nothing that ships.

## Sequences

**Who drops the index and who builds it**, read in the code. Only stage 2 drops it, in `ExtractionJobConfiguration.extractionReader`, and only where its own step is not finished under the run the invocation arrives at. Only stage 4b builds it, in `RedundancyJobConfiguration`, where it is missing. The index is one for the table, not one for a run or a walk. So it stands from any stage 4b until a stage 2 next has work, and a stage 3 that runs in between, under a stage 2 with nothing to do, groups with it there. Stage 3 runs whenever its step is not finished under its run, and its run names `similarity`, `extraction` and `pipeline` where stage 2's names `extraction` and `similarity` (`StageModules`).

| Sequence | Does stage 3's grouping run with `shingle_by_hash` present? | How it is known |
| --- | --- | --- |
| An invocation reaches stage 4b; a build that moves `pipeline` alone is installed; the next invocation | **Yes** | Run: `StageThreeMeetsTheHashIndexInvocationTest`, first test |
| Stage 3 is stopped over corpus root B; corpus root A, in the same working directory, is then taken through stage 4b; B is invoked again | **Yes** | Run: the same class, third test |
| An invocation reaches stage 4b; a build that moves `similarity` is installed; the next invocation | No: stage 2 has a new run, and drops the index before stage 3 runs | Run: the same class, second test |
| Stage 3 is stopped under stage-2 run X; a value stage 2's run records is changed and that run is taken through stage 4b; the value is put back, which arrives at X again (ADR-156) | Yes, by the reasoning above | Reasoned, not run |
| The archive changes, so the next invocation walks again | No: a new walk has a new stage-2 run, which drops the index | Held before this record: `ShingleHashIndexInvocationTest` |

Three of the five were run for this record, one is reasoned, and one was already held. The first is the ordinary one: every upgrade to a build whose commits since the one before it touched `pipeline` and neither `similarity` nor `extraction`.

**How the tests see the index at the moment of the grouping.** A trigger the test adds to `shingle_document_frequency` records, for each row written there, whether `sqlite_master` holds `shingle_by_hash` at that moment, beside the run the row is written under. A build is moved by `SuccessiveBuildsBeans`, which moves one module's recorded version between two invocations of one context; a test cannot install a second jar. A stop is a trigger refusing the row that records stage 3 finished, which rolls the tasklet's transaction back, the stage-3 run's own row with it.

## Measured

**Method.** A throwaway probe outside the repository, on 2026-10-09, made as ADR-218's second was: Java 26.0.2.1 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), the shipped `schema.sql`, the shipped URL's parameters (`foreign_keys=on`, `busy_timeout=300000`, `journal_mode=WAL`, `synchronous=NORMAL`, `journal_size_limit=536870912`), and SQLite's directory for temporary files set once, on a connection to no database, before the database was opened. The grouping was run as stage 3 issues it, insert included, with SQLite's progress handler set to call back every 100,000 steps as `StatementSteps` sets it, and rolled back after each run. The sizes of the files in the temporary directory were summed every 5 ms and the greatest sum kept. Each plan is `EXPLAIN QUERY PLAN`'s on the same database.

**Ledgers.** ADR-218's second probe's, with the same seed: `shingle` rows alone, fifty to an occurrence, a tenth of them drawn from a pool of 100 hashes and the rest random, the granularity `word:5`, one stage-2 run. Three sizes: 1,500,000, 4,500,000 and 9,000,000 rows. The grouping writes 100 rows at each.

**Disk.** A solid-state NVMe disk, NTFS, Windows 11, the file cache warm. **Every figure in this record is from synthetic ledgers on that disk.**

### The grouping in the two states, and with the clause

Times are the least and the greatest of the runs made. The first, third and fourth blocks are of three runs at each size; the second and the fifth are of one run at each size.

| | 1,500,000 rows | 4,500,000 rows | 9,000,000 rows |
| --- | ---: | ---: | ---: |
| **`shingle_by_hash` absent, as shipped**: time | 1.07–1.08 s | 3.45–3.62 s | 6.67–6.83 s |
| temporary files at their largest, bytes | 33,541,656 | 103,499,404 | 207,619,562 |
| bytes a row | 22.4 | 23.0 | 23.1 |
| steps a row | 35.47 | 35.49 | 35.49 |
| share the progress line can state at the end | 78% | 78% | 78% |
| **Absent, with the clause**: time | 1.06 s | 3.40 s | 6.76 s |
| **`shingle_by_hash` present, as shipped**: time | 5.14–5.27 s | 19.33–19.67 s | 41.57–41.79 s |
| temporary files | none | none | none |
| steps a row | 28.47 | 28.49 | 28.49 |
| share the progress line can state at the end | 63% | 63% | 63% |
| **Present, with the clause**: time | 1.06–1.12 s | 3.31–3.44 s | 6.88–7.00 s |
| temporary files at their largest, bytes | 33,541,656 | 103,499,404 | 207,619,562 |
| steps a row | 35.47 | 35.49 | 35.49 |
| share the progress line can state at the end | 78% | 78% | 78% |
| **Present, `NOT INDEXED`**: time | 0.99 s | 3.16 s | 6.41 s |

- **The plans.** Absent, as shipped: `SEARCH shingle USING INDEX shingle_by_run_id (run_id=?)`, `USE TEMP B-TREE FOR GROUP BY`, `USE TEMP B-TREE FOR count(DISTINCT)`. Present, as shipped: `SEARCH shingle USING INDEX shingle_by_hash (run_id=?)`, `USE TEMP B-TREE FOR count(DISTINCT)`. With the clause, in either state: the first of those, word for word. `NOT INDEXED`: `SCAN shingle`, then the two temp B-trees.
- **With the index present the shipped statement took 4.8, 5.6 and 6.2 times as long**, by the middle run of the three at each size, and the ratio grows with the rows. ADR-218's probe took 20.63 s and 47.23 s at the two larger sizes, as #473's table gives them and ADR-218 rounds to 20.6 s and 47.2 s: 5% to 7% and 13% to 14% above the runs made here.
- **With the clause the statement is the one ADR-211 measured, in both states**: the same plan, the same steps a row to the hundredth, the same bytes of temporary files to the byte, and times that overlap the shipped statement's without the index at the smallest size; at 4,500,000 rows the four runs with the clause are below them, by 0.31 s at most; at 9,000,000 the one run without the index is among them and the three with it are above, by 0.33 s at most.
- **`NOT INDEXED` reads every row of the table**, not the run's. These ledgers hold one run, so its time here says nothing of a table that keeps several runs' rows, as a working directory's does (ADR-218, #468).
- **Callbacks keep coming in both plans.** The longest wait between two was 0.082 s in the plan through `shingle_by_run_id`, 0.051 s in the plan through `shingle_by_hash` and 0.073 s in `NOT INDEXED`'s scan, over every run of the three ledgers. The figures are of the plan and not of the state: the runs with the clause, made with the index present, waited up to 0.074 s.
- **With the index present the share is close to a share of the time.** At 9,000,000 rows it reached 10% at 6.6 s and 60% at 39.4 to 39.6 s of 41.6 to 41.8. Without the index it reached 20% at 3.6 to 3.8 s of 6.7 to 6.8, as ADR-211 §9 says of it.
- **The build and the drop**, once each at each size, the index freshly built: the build took 2.14, 6.57 and 13.08 s, and the drop 0.06, 0.18 and 0.38 s.

### Steps a row, shape by shape

The seven shapes `GroupingStepsPerRowTest` lays, each over 40,000 rows, and the second of them again over 80,000: eight measurements, counted every 100 steps, foreign keys on, each made without the index, with it, and with it and the clause.

| Shape of the rows | Absent | Present | Present, with the clause |
| --- | ---: | ---: | ---: |
| every row a different hash | 37.00 | 30.00 | 37.00 |
| every hash carried by two occurrences | 44.00 | 37.00 | 44.00 |
| two hashes in three carried by two occurrences, the rest by one | 42.60 | 35.60 | 42.60 |
| every hash carried by four occurrences | 33.00 | 26.00 | 33.00 |
| 1,000 hashes among the rows | 23.10 | 16.10 | 23.10 |
| one hash in every row | 19.06 | 12.06 | 19.06 |
| every hash under two granularities, under each carried by two occurrences | 44.00 | 37.00 | 44.00 |
| every hash carried by two occurrences, 80,000 rows | 44.00 | 37.00 | 44.00 |

- **Through `shingle_by_hash` the statement takes 7.00 steps a row fewer in each of the eight**, and in the three ledgers above. The first column is ADR-211's, figure for figure, in all eight: seven are in its table and the eighth is in the sentence beneath it.
- **So ADR-211's 45 is above the most measured in both plans**, 44.00 and 37.00, and a count of steps divided by it is never ahead of the rows in either.
- **The share ends lower with the index present**: between 26% and 82% over these eight, where it ends between 42% and 97% without it. ADR-211 writes the last as 98%; it is 44 of 45, which the line's whole percent states as 97.
- With the clause the third column equals the first in all eight.

## Decision

### 1. The grouping names the index it reads through

Stage 3's grouping gains one clause after the table's name, and nothing else in it changes:

```
… FROM shingle INDEXED BY shingle_by_run_id WHERE run_id = ? …
```

SQLite then reads the run's rows through `shingle_by_run_id` whether or not `shingle_by_hash` is built, in the order they were written, and sorts them itself for the `GROUP BY`. That is the plan ADR-211 measured and excepted from ADR-060's bound, the plan its line before the grouping describes, and what ADR-182 recorded of stage 3. `shingle_by_run_id` is created by `schema.sql`, which runs at every start (ADR-173 §3), so the index the clause names is always there; a statement naming an index that does not exist fails to prepare, and stage 3 would then fail as any statement that cannot be prepared fails it.

The other statements of `DocumentFrequency` name at most a page of occurrences and are not touched.

### 2. The clause ships with the next change that moves `similarity`

Not with this record, by the operator's choice. A change to `similarity` moves the run ids of stages 2 to 6b and has stage 2 do its work again (§4), and one clause is not reason enough for that. This is ADR-216's ride-along rule, applied to a change of behaviour: the clause goes when a change that re-mints `similarity` for a reason of its own is made, in that change's commit, and it is never the only reason for the re-mint.

**What keeps that change from leaving it behind.** ADR-216 carries its owed items by an open ticket and nothing else, and says they are *"pinned by the change that carries them"*. That is kept: #473 stays open until the clause ships. This record adds one test to it, `TheGroupingsPinShipsWithTheNextChangeToSimilarityTest`. It was the addition of the session that wrote this record and was not among the operator's first answers; the operator, asked about it after the gate, kept it. It passes while the sources of `similarity` are the ones this record was written against, or `DocumentFrequency` carries the clause, and fails otherwise, saying what is owed. It passes today. The change that ships the clause deletes it (§5).

### 3. Until then, stage 3 is slow wherever it meets the index, and one of its lines is untrue there

Stated plainly, because it is what shipping later costs:

- **In the two sequences run that meet the index, and the one reasoned, stage 3's grouping takes several times as long.** 4.8 to 6.2 times on a warm solid-state disk, growing with the rows (Measured).
- **On a slow disk it may be far worse, and that is arithmetic, not measurement.** Through `shingle_by_hash` each row is fetched from wherever in the table it was written. ADR-191 took 100 pages a second for the USB spinning disk in its own arithmetic. Were every row of the 42,833,917 on record one run's, and every fetch a read from that disk, that is 428,339 seconds, about five days, where reading one run's rows in the order they were written took half an hour on that disk (ADR-191). The file cache and the rows of other runs both make it less. Nothing here was run on such a disk.
- **The line before the grouping is untrue in that state.** It says the database *"sorts them in temporary files in the working directory"*, and with the index present it writes none. The line is `pipeline`'s and is not changed: it is true again when the clause ships. The comment above `shingle_by_run_id` in `schema.sql` says stage 3's read of a run's rows goes through that index, which is untrue in the same state and true again with the clause; it is not changed either.
- **The progress lines stay true, and stop lower.** *"At least X%"* holds in both plans. With the index present the last line is at 63% on the ledgers measured and between 26% and 82% over the shapes.
- **Stopping stage 3 then loses only the time spent**, as its line says: the statement writes nothing that outlives its transaction.

An operator who has to run a `pipeline`-only build against a large archive on a slow disk before the clause has shipped can ask for the clause to be shipped on its own. That is the alternative refused below, and its cost is §4's.

### 4. Which run ids move

**None now.** A stage's implementation version is the last commit touching `src/main/java/io/algernon/vespera/<module>` for a module `StageModules` names for it (ADR-058). This record changes nothing under `src/main`: it adds this file, one test class in `pipeline`'s tests and one in `similarity`'s, edits one test class, adds a constant to `Adr.java`, adds a row to the index and moves the range in its first line, puts a banner on ADR-182, ADR-211 and ADR-218 and corrects one Tests note of ADR-182 in place, and changes two sentences of `AGENTS.md`, its count of the records and its sentence on what is known and open (Consequences).

**When the clause ships: stages 2 to 6b, and stage 1 does not.** `DocumentFrequency` is in `similarity`, which `StageModules` names for stage 2, stage 3 and stage 4. Stage 5's two runs, 6a's and 6b's each record the id of the run upstream of them (`StageRuns`), so they move with stage 4's. Stage 1 names `corpus` alone.

**Stage 2 then does its work again**: under a new run it reads every survivor of stage 1, writes that run's shingle rows beside the rows `shingle` already keeps (ADR-218, #468), and drops `shingle_by_hash` first, which stage 4b then builds over the larger table. That is the cost the next change to `similarity` already has, and the reason the clause waits for it.

### 5. What the change that ships the clause owes

In its own commit, the `analyst` making the test-side edits first and `spec-implementer` the one line of `src/main`, as ADR-216 §13 splits its own:

- **P1, production.** `similarity/DocumentFrequency.java`: the clause of §1 in the grouping, and its class javadoc citing this record.
- **P2, test side: what turns.**
  - `DocumentFrequencyIsCountedInTheDatabaseTest`: the claim on the one grouping statement sent gains that it contains `FROM shingle INDEXED BY shingle_by_run_id WHERE run_id = ?`. This is the claim that is red without the clause. Its `kind` matcher asks for `FROM shingle` and `GROUP BY`, and still finds the statement.
  - `StageThreeMeetsTheHashIndexInvocationTest`: the last claim of its first test turns, from planned through `shingle_by_hash` with no sort for the `GROUP BY` to planned through `shingle_by_run_id` with one, and its `GROUPING` constant gains the clause. Every other claim of the class stands. Two passages of its class javadoc are rewritten, being about a time before the clause: the one opening *"A characterisation of what ships today"*, and the one headed *"What ADR-219's clause turns here, and what it leaves"*.
  - `GroupingStepsPerRowTest`: its `GROUPING` text gains the clause, so that it measures the statement shipped; the steps measured with the clause are the ones it holds today (Measured), and its claim on the plan stands. Two descriptions of that text name ADR-211 §3 as its source and gain this record: *"as ADR-211 section 3 gives it"* in the class javadoc, and *"ADR-211 section 3's grouping, as written there"* on the constant.
  - `ShingleIndexesInTheSchemaTest`: no assertion changes, and five texts do, each saying the clause is not shipped. In the class javadoc, the paragraph on what the class holds of stage 3, with its *"as it ships"* and *"The clause is not in `DocumentFrequency` yet"*. On `THE_GROUPING`, *"as `DocumentFrequency` sends it today"*: it becomes the grouping without the clause, which nothing sends. On `THE_GROUPING_PINNED`, *"which `DocumentFrequency` does not send yet"*. In the fourth test, the claim a report shows, *"is the count sent today with that one clause added"*. And on the fifth test, *"It passes today because `DocumentFrequency` sends this text"*.
  - `TheGroupingsPinShipsWithTheNextChangeToSimilarityTest` is deleted.
- **P2, test side: what owes nothing**, each read for this.
  - `EveryStatementThatSortsIsRecordedTest` counts four sorting statements in `DocumentFrequency`, in its map for a database as `schema.sql` leaves it and in its map with `shingle_by_hash` built, which is a copy of the first. The grouping is one of the four in both today, keeping a temp B-tree for `count(DISTINCT)` where the index is built. With the clause it keeps two in both states (Measured), so the count stays four in both. That class was not run against a `DocumentFrequency` carrying the clause, there being none.
  - `EachTableIsNamedOnlyByItsOwnerTest` reads a name as a table's where it follows `FROM`, `JOIN`, `INTO`, `UPDATE`, `TABLE` or `ON`. `shingle_by_run_id` follows `BY`, so it is not read as one, and `shingle` after `FROM` is `similarity`'s own.
  - `ContentCensusSaysHowManyShingleRowsItReadsInvocationTest` holds the words of the line before the grouping, which the clause makes true in both states and does not change.
  - No other class of `src/test` carries the grouping's text or a claim on its plan. The tree was searched for the `GROUP BY` of the two columns, for `FOR GROUP BY`, for `USING INDEX shingle_by`, for `INDEXED BY` and for `shingle_by_run_id`; the classes named in this section are the ones found that speak of stage 3's grouping.
- **P3, the record and what points at it.**
  - A line is added under this record's Status saying in which commit the clause was built, and #473 is closed.
  - `AGENTS.md`: stage 3's case is taken out of the sentence on what is known and open against what ships.
  - `Adr.java`: the javadoc of this record's constant says the statement *"is to name"* its index, that *"until then stage 3 is slow in that state"*, and *"No run id moves now"*. It is corrected to what then holds.
  - `docs/adr/README.md`: this record's row says *"until the clause ships stage 3 takes several times as long"* and that #473 *"stays open until then"*. It is corrected to say the clause has shipped, and in which change.
  - The three banners and the corrected Tests note of ADR-182 are left as they are (Consequences).

## Alternatives refused

- **Leave it, and record the time.** No code and no run id moves. Stage 3 stays several times slower in that state for good, its line before the grouping stays untrue there, and ADR-182's recorded consequence stays untrue of what ships.
- **Stage 3 drops `shingle_by_hash` before it reads.** A change to `pipeline` alone, `ShingleHashIndex.drop()` being public already: stages 3 to 6b move and stage 2 does no work. But each time stage 3 meets the index it pays a drop, and stage 4b then pays a build it would not have made, with 183 bytes a row free on the drive (ADR-218). ADR-187 records two hours for a drop on a 16 GB database, and ADR-193 39 minutes for a build on a USB spinning disk. It contradicts ADR-182 §1, *"present from stage 4b on"*. And the build that carried it would itself be a `pipeline`-only change, so the first run after it would pay both.
- **`NOT INDEXED`.** It reads every row `shingle` keeps, of every run, where the clause chosen reads the run's (Measured). It costs the run ids §4 names all the same.
- **Shipping the clause now, on its own.** Stage 3 would be right from the next run on. Every run id from stage 2 on would move for one clause, and stage 2 would do its work again over an archive that had no other reason to (§4). The operator set it aside for that.
- **Asking git, in a test, whether `similarity` has moved.** The build's own record of a module's version is a fact about the commits a checkout happens to hold, which is why `ImplementationVersions` has a seam for tests. The test of §2 reads the sources instead.

## Consequences

- **Nothing an operator sees changes with this record.** Stage 3 behaves as it did yesterday, slow in that state included (§3).
- **The next change to `similarity` fails a test until it carries the clause**, whatever that change is for, and a pull request open against `similarity` when this record merges fails it on its next merge of `main`. The failure names this record and what is owed.
- **When the clause ships, ADR-211's figures and words for the grouping, and ADR-182's three sentences, hold in every state of a working directory**, and the three banners this record adds describe a time that has passed. They are not removed: a later reader of those records still needs to know there was one.
- **#473 stays open**, as #352 does for ADR-216's owed items.
- **`AGENTS.md` names this among what is known and open against what ships**, by the operator's answer after the gate: beside the cases ADR-210 leaves open, stage 3's grouping where it meets `shingle_by_hash`, slower and with one line untrue there until the clause ships. The change that ships the clause takes it out again (§5).
- **ADR-060's bound and its exceptions are as ADR-218 left them.** With the index present the grouping writes no temporary file, which is under the bound, not a new exception to it.

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.StageThreeMeetsTheHashIndexInvocationTest`, new, three tests | the three sequences marked *Run* above, each by whole invocations: that after a build moving `pipeline` alone stage 2 keeps its run, converts nothing and says nothing of dropping the index, stage 3 keeps its run too and groups nothing, and so does stage 4 (until [#353](https://github.com/algernon28/vespera/issues/353) the test claimed that stage 3 then ran under a new run with `shingle_by_hash` in the database; ADR-222 took `pipeline` out of content census's version, and this note is corrected for it); that after a build moving `similarity` stage 2 has a new run and drops the index, and each row stage 3's grouping writes is written without it; and that a stage 3 stopped over one corpus root, invoked again once another corpus root has reached stage 4b, writes each row with the index there, and SQLite's plan for the grouping is then through `shingle_by_run_id` with a sort for the `GROUP BY` (the claim about the plan was the first test's until #353, and until the clause shipped, #473, it was that the plan went through `shingle_by_hash` with no sort; §5 turned it). Stage 3 meets the index in one sequence since #353, not two, and the class's javadoc says so |
| `similarity.ShingleIndexesInTheSchemaTest`, six tests where it had four | three as before: that `schema.sql` puts no index on the hash, that `shingle_by_run_id` is on the run alone, and that containment retrieval needs `shingle_by_hash`. One described anew, its assertions unchanged: that a plain read of a run's rows goes through `shingle_by_run_id` in both states, which is ADR-182's sentence about a statement nothing ships. It is kept, by the operator's answer after the gate, for what it shows beside the two new ones: the same rows of the same run, read with nothing grouped, are not drawn to `shingle_by_hash`, so it is the `GROUP BY` that draws the planner there. Context faults this test for holding nothing that ships while described as holding what `DocumentFrequency` sends; described as a contrast, it claims no more than it holds. Two new: that the grouping with §1's clause is planned through `shingle_by_run_id`, sorting for its `GROUP BY`, with the same plan in both states; and that the grouping without it is planned through `shingle_by_hash` once that is built, which is why the clause is needed and stays true of SQLite after it ships |
| `similarity.TheGroupingsPinShipsWithTheNextChangeToSimilarityTest`, new, one test | §2: that `similarity`'s sources are the ones this record was written against, or `DocumentFrequency` carries the clause. Deleted by the change that shipped the clause, as §5 has it (#473) |

All ten pass today (nine since the change that shipped the clause deleted the third class's one, #473). No test lands failing or disabled.

**What no test holds.**

- **That `DocumentFrequency` sends the clause.** It does not yet. §5's P2 is the claim, and it lands with the clause. It landed with it (#473): `DocumentFrequencyIsCountedInTheDatabaseTest` holds that the one grouping sent contains `FROM shingle INDEXED BY shingle_by_run_id WHERE run_id = ?`.
- **Any time, and any size of temporary files.** They are the probe's. A test of the suite cannot watch the process's temporary files (ADR-218, Tests), and the figures need millions of rows.
- **The steps a row with the index present.** `GroupingStepsPerRowTest` measures without it. The 45 is above both plans' figures in the probe; with the clause shipped the plan with the index is the one that test measures.
- **The plans over rows.** `ShingleIndexesInTheSchemaTest` plans against empty tables with no statistics; the invocation test plans over the rows of a corpus of 48 files. The probe's plans over millions of rows were the same.
- **The fourth sequence**, a value put back.
- **That the trigger sees what the statement sees.** It records what `sqlite_master` holds as each row is written, inside the grouping's statement; that the statement was planned while the index was there is the plan claim's, asked after the invocation.
- **A second jar.** `SuccessiveBuildsBeans` moves a recorded version in one context.
- **That the reminder fails for a change this record did not think of**, such as a file of `similarity` moved to another module. It reads one directory.
- **That the clause's index exists at run time.** `schema.sql` creates it; nothing here holds what stage 3 does where it is missing.

## What this record does not measure

- Anything on a spinning disk, on `H:`, on Linux, or with the file cache empty. §3's five days is arithmetic.
- Any size above 9,000,000 rows; the table on record has 42,833,917.
- A `shingle` table holding the rows of several stage-2 runs, as a working directory's does. It bears on `NOT INDEXED`, and may bear on the time through `shingle_by_hash`.
- An index grown row by row or built into pages a drop had freed, the state ADR-187's two hours are of. The probe's index was freshly built each time.
- The grouping through the application's own pool and `StatementSteps`: the probe used one plain connection and set the handler itself.
- A ledger whose grouping writes many rows, at size. The three ledgers write 100 rows each; the shapes that write up to half their rows are of 40,000 and 80,000 rows.
- Two of the nine shapes ADR-211's table lists: half the hashes carried by three occurrences and half by two, and every hash but two carried by three.
- Whether stage 2 under a new run converts anything again. `ShingleHashIndexInvocationTest` says conversions are cached outside the run (ADR-070); the second test here claims only that the invocation completed under a new stage-2 run and dropped the index.
- The suite's integration tests, which `./mvnw verify` runs.

## What this does not decide

- **Which change to `similarity` carries the clause.** The next one, whichever it is.
- **Whether the line before the grouping should say less about how the database does its work**, so that no plan can make it untrue again.
- **Whether `shingle` should keep the rows of earlier stage-2 runs**: [#468](https://github.com/algernon28/vespera/issues/468).
- **Whether the progress callbacks keep coming on a slow disk**, which ADR-211 and ADR-218 left open and this record does not take up.
