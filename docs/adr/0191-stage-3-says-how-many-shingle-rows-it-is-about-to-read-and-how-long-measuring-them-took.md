# ADR-191 — Stage 3 says how many shingle rows it is about to read, and how long measuring them took

- **Date**: 2026-10-04
- **Status**: accepted
- **Extends**: [ADR-187](0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md) §1 — **its rule, to a fourth statement, and nothing it decided.** §1 announces *"a single SQL statement that builds or removes an index over rows that already exist"*, and counts three. Stage 3's read of one run's shingle rows is neither a build nor a removal, and on 2026-10-04 it lasted half an hour. From this record it is announced too (§1 here). ADR-187's three statements, their lines, and its §2 and §3 stand as written.
- **Extends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md). One of stage 3's lines gains the time it took, and one line is added before it.
- **Adds a measurement to**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) and [ADR-187](0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md) §2 — **an effect of the drop that neither looked at, and no change to what either decided.** ADR-182's Consequences say stage 3 *"reads the run's shingles in the order they were written"*. That is the order of their rowids. In the probe here it is not the order of their pages in the file once stage 2 has written into pages a dropped index freed (Measurements). ADR-187 §2 weighed the drop against the minutes it saves stage 2 and the minutes stage 4b's rebuild costs. It did not look at where the drop leaves the next run's rows. This record measures where, on a solid-state disk. What that costs a read on a spinning disk is a prediction, not a measurement, and §2 is left as it is (What this does not decide).
- **Rests on**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) §2.1. `shingle_by_run_id` is the index this read goes through, and the one §2 here asks its two questions of.
- **Keeps**: [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md). `similarity` owns `shingle`, and the two statements that ask it for the bound are written in `similarity` (§3). No SQL in `pipeline` names that table.
- **Rests on**: [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md), for what this change costs in run ids. ADR-188 moves stage 1's run id and every later stage's with it, so a database upgraded across it replays stage 2 whether or not this record ships (§3, Consequences).
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No schema version moves: no table changes and no index is added, removed or renamed.
- **Settles** [#410](https://github.com/algernon28/vespera/issues/410).

**Two words here are SQLite's.** An *index* is always a SQLite index on a table, named where it matters. It is never the ledger. A *page* is one of the 4,096-byte blocks SQLite keeps its tables and indexes in.

## Context

### What happened

On 2026-10-04 an invocation over the whole-archive working directory on `H:` spent 31 minutes 40 seconds in stage 3 and wrote nothing while it did. Build `11691d0`, database 16.7 GB. Stage 2 had just finished under a new run with 5,001 survivors. `Stage 3 (content census) starting under run fcf6f0d2…` was written at 18:18:30.505, and the step ended at 18:50:06.838: `Step: [content-census] executed in 31m39s773ms`.

`jstack` at 18:36 and at 18:46 showed the `main` thread in `NativeDB.step` under `DocumentFrequency.measure`, in this statement:

```sql
SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?
```

At 18:36 the process read 4.1 MB in 10 seconds, wrote nothing and used 0.1 s of CPU. That is 100 pages a second, 10 ms a page. Memory was not short: the heap held 2.3 GB of a 15.4 GB limit.

It was the third wait of that shape on that day. The other two are ADR-187's: start-up's index build, and stage 2's drop of `shingle_by_hash`, which read 115 pages a second for 1 hour 58 minutes.

### What stage 3 said before this record

`ContentCensusTasklet` wrote `Stage 3 (content census) starting under run <id>`, called `DocumentFrequency.measure`, and wrote `Stage 3 (content census) measured shingle document frequency`. The read is the first thing `measure` does after it has read stage 2's survivors, so the half hour fell between those two lines. Neither said that a read was about to happen, over how many rows, or how long it took.

### What `H:` is

Windows' own inventory of the machine's disks (`Get-PhysicalDisk`, which asks nothing of the volume) lists two spinning disks, both `WD My Passport`, both on the USB bus, with no spindle speed reported. `H:` is a partition on one of them. ADR-180 and ADR-187 call it a local spinning disk. It is a portable one, behind USB.

## Measurements

**Where and how.** Two throwaway probes, kept outside the repository as AGENTS.md asks. Python 3.13.5, whose `sqlite3` module reports SQLite 3.49.1 (the application's `sqlite-jdbc` bundles 3.53), on Windows 11, on a solid-state NVMe disk. The connection settings are the shipped URL's: write-ahead log, `synchronous=NORMAL`, `journal_size_limit=536870912`, and SQLite's default 2 MB page cache. **Nothing here ran on `H:`, on a spinning disk, or on the archive's database**, which a whole-archive run was using. **So what is measured here is where a run's pages lie and how many are read. What that costs on a spinning disk is predicted from it, and nowhere measured.**

**The databases.** The `shingle` table as `schema.sql` declares it. One run is 1,000 file occurrences of 2,500 random-hash rows each, 2,500,000 rows, written in transactions of 16 occurrences (`CHUNK_SIZE`), in batches of 5,000 rows as `Shingler` sends them. Every run is written against the same 1,000 occurrence ids, as a new stage-2 run over a walk that is reused is.

- **A new file.** One run, written with `shingle_by_occurrence` and `shingle_by_run_id`, as today's build writes into a new working directory.
- **The archive's history.** Run *a* is written with `shingle_by_occurrence` and `shingle_by_hash`, as a build before ADR-182 wrote it. Then the probe does what the upgraded build did: `CREATE INDEX shingle_by_run_id`, `DROP INDEX shingle_by_hash`, and run *b* written without the by-hash index. Then, twice more, what every later stage 2 under a new run id does: `CREATE INDEX shingle_by_hash` over every row, as stage 4b builds it, `DROP INDEX shingle_by_hash`, and a run written: *c*, then *d*.

**What is measured about each run.** The plan SQLite chooses for stage 3's statement is `SEARCH shingle USING INDEX shingle_by_run_id (run_id=?)` in every case: it walks the run's part of the index, and for each entry fetches the row from the table. The probe reads the file's b-trees and lists, in the order that walk meets them, the leaf pages of the index and of the table that hold the run. It then counts the steps from one page to the next by where they land in the file. **Sixty-four pages, 256 KB, is the probe's own choice of what counts as far.** It stands in for whatever a disk reads ahead, and no disk was asked. The read itself is timed with the file cache emptied first, by ADR-187's method.

| The run read | Leaf pages: table, index | Steps not to the next page | Steps backwards | Steps backwards, or more than 64 pages forward | Steps more than 64 pages either way | The read, cache emptied | Read |
|---|---:|---:|---:|---:|---:|---:|---:|
| Written into a new file | 69,763 and 50,000 | 94,073 of 119,762 (78.5%) | 23,317 (19.5%) | 23,317 (19.5%) | 0 | 7,980 ms; 1,109 ms warm | 495.7 MB in 121,033 reads |
| *a*: written beside a row-grown by-hash index, `shingle_by_run_id` built afterwards | 69,763 and 44,633 | 114,394 of 114,395 (100.0%) | 44,633 (39.0%) | 89,265 (78.0%) | 89,265 (78.0%) | 7,841 ms; 1,116 ms warm | 472.8 MB in 115,440 reads |
| *b*: written into the pages a row-grown by-hash index freed | 71,177 and 50,001 | 108,495 of 121,177 (89.5%) | 41,764 (34.5%) | 65,827 (54.3%) | 48,775 (40.3%) | 8,391 ms; 1,237 ms warm | 501.6 MB in 122,469 reads |
| *c*: written into the pages a built by-hash index freed | 71,178 and 50,001 | 114,716 of 121,178 (94.7%) | 56,067 (46.3%) | 82,164 (67.8%) | 52,458 (43.3%) | 8,121 ms; 1,169 ms warm | 501.6 MB in 122,473 reads |
| *d*: the same, one run later | 71,178 and 50,659 | 121,480 of 121,836 (99.7%) | 67,232 (55.2%) | 103,701 (85.1%) | 73,323 (60.2%) | 9,233 ms; 1,166 ms warm | 504.5 MB in 123,161 reads |

What the table shows:

- **The read asks for one page at a time.** 121,033 reads for 495.7 MB is 4,096 bytes a read, and the count of reads is the count of leaf pages, to within the b-trees' interior pages. That is what a statement reading each leaf of the run's rows and of the run's part of the index once, and asking for nothing ahead, would give.
- **It costs 198 bytes and one page read for every 20.7 rows**: 48,400 page reads per million rows. Two fifths of them are index pages. An entry of `shingle_by_run_id` carries the 64-character run id, so the index's part of one run is 205 MB here beside 286 MB of table.
- **In a new file the read never steps more than 64 pages, and it still jumps.** Of the 69,762 steps from one table leaf to the next, 69,761 are not to the next page of the file, and of the 49,999 between index leaves, 49,998. That fits stage 2 writing table pages, pages of `shingle_by_occurrence` and pages of `shingle_by_run_id` in turn, which the probe did not check. Within the table and within the index every step is forward. The read, which goes between the two, steps backwards 23,317 times, 19.5% of its steps, fewer than one for every two index leaves. No step, either way, is longer than 256 KB.
- **After a drop, the run that stage 2 writes next lies largely out of the order it is read in.** Stage 2 takes its new pages from SQLite's free list before it extends the file: 78,317 freed pages were all used by run *b*, 138,999 by run *c*, and all but 13,913 of 208,497 by run *d*. Of the steps between one table leaf and the next, 21.3% go backwards in run *b*, 56.6% in *c* and 79.2% in *d*. Of the steps between one index leaf and the next, 40.2%, 71.3% and 99.5%. In the new file both figures are zero.
- **Dropping a built index did not leave the next run in better order than dropping a row-grown one. Runs *c* and *d* are further out of order than run *b* in each of the four columns that count steps.** The timing column does not follow them: run *c*'s cold read, 8,121 ms, was faster than run *b*'s, 8,391 ms. ADR-187 found that an index built whole into new pages is one unbroken stretch of the file, and is cheap to drop. That holds. The rows written into the pages that stretch frees are run *c* and run *d*. **The probe does not say why they are worse, because it changes two things at once**: *b* follows the drop of a row-grown index and *c* and *d* the drop of a built one, and *b*, *c* and *d* take 78,317, 138,999 and 194,584 pages from the free list, so the kind of index dropped is not separated from how much of the run lands in freed pages.
- **They are not the worst rows of the table in every column. Run *a* is the worst in two of the four columns that count steps**: not to the next page (100.0%) and more than 64 pages either way (78.0%). It is what the archive's runs written before ADR-182 are expected to be: rows written before `shingle_by_run_id` existed, and that index built afterwards, in one stretch (1.9% of the steps between its leaves are not to the next page). 100.0% of the read's steps are not to the next page and 78.0% are longer than 64 pages, two for each index leaf, which is what going to the table and back for every index leaf would give. Run *a* is above *c* in three columns and above *d* in two. Runs *c* and *d* are above it in steps backwards (46.3% and 55.2% against 39.0%), and *d* in steps backwards or far (85.1% against 78.0%).
- **The disk measured does not show what any of this costs.** All five cold reads took 7.8 to 9.2 s, and the new file's, at 7,980 ms, was slower than run *a*'s, at 7,841 ms. Order in the file made no difference that this disk could show. The cold read is seven to eight times the warm one in every row.

**What that predicts on a spinning disk, and what was seen on `H:`.** On a spinning disk a step backwards, or one too far forward for the disk's own read-ahead to cover, is expected to cost a seek or a turn of the platter, of the order of 10 ms. That is a general figure for such disks, not a measurement of this one. The one sample on `H:` is 10 ms a page. ADR-187's drop was 8 to 9 ms a page on the same disk. **Read at the sampled rate throughout, 31 minutes 40 seconds is 190,000 pages: 780 MB, or about 3.9 million rows on the probe's proportions.** How many rows the run held is not known (Not known). ADR-173 counted 24,910,924 rows in `shingle` on its copy of the archive's database, where the archive's stage 2 is 10,406 file occurrences: 2,394 a file occurrence, if those rows were one run's. If this run held that many for each of its 5,001 survivors, it held about 12 million rows in about 580,000 pages, and the read averaged 3.3 ms a page, which at 10 ms a seek would be a seek on one page in three.

**Neither reading is confirmed, and the first asks more of the layout than the probe found.** Ten milliseconds on every page is a seek on every step. After a drop the probe found 34% to 55% of steps backwards and 54% to 85% backwards or far, not all of them. The second reading, a seek on one page in three, is inside those figures. #410 lists as not known whether the read was slow because its pages were out of order or because `H:` is slow for any cold read of this size, and it stays not known: **the layout is the likely cause, and it is not shown to be the cause.** A read of 100 pages a second, 0.4 MB a second, is what seeking looks like on a spinning disk, as ADR-187 argued of the drop, and that one sample is the whole of the evidence from `H:`.

**The run read that evening was written straight after a drop**, as far as the two tickets say. On the same build and the same afternoon, stage 2 dropped `shingle_by_hash` by 15:53 (ADR-187), freeing at least the 3.44 GB that drop had read, and then wrote its run. So its rows are run *b*'s case if the index dropped was the row-grown one, and run *c*'s if stage 4b had built it since. ADR-187 could not tell which. Neither can this record. Both were out of read order in the probe.

**Other figures.** The fourth and fifth rows are from the probe above. The second is from the second probe. The first and third draw on both. The second probe builds the same database as far as run *b* and asks each statement three times, cache emptied each time.

| Statement | Measured, cache emptied |
|---|---:|
| `SELECT MIN(rowid) FROM shingle WHERE run_id = ?`, then the same with `MAX`, as two statements | 1.4 to 2.3 ms for the pair over both probes; 0.037 to 0.041 MB read, in 10 reads where the second probe counted them. 49 and 48 steps of SQLite's virtual machine, counted by its progress callback |
| `SELECT MIN(rowid), MAX(rowid) FROM shingle WHERE run_id = ?`, as one statement | Run *b*: 2,856 to 2,921 ms; 209.3 MB read in 51,092 reads. Run *a*: 210 to 238 ms; 186.4 MB in 45,497 reads. About 20,000,000 steps for either |
| `SELECT COUNT(*) FROM shingle WHERE run_id = ?` | Run *b*, in the second probe: 2,822 to 2,892 ms; 209.3 MB in 51,092 reads. Run *a*: 173 to 201 ms; 186.4 MB. About 7,500,000 steps for either. In the first probe: 2,636 to 3,163 ms and 209.3 to 212.1 MB for the four runs other than *a*, and 168 ms and 186.4 MB for run *a* |
| The whole database file read from end to end, then stage 3's read | 170 ms for 796 MB, 842 ms for 3,219 MB; the read then takes 1,155 to 1,359 ms |
| Stage 3's statement with `NOT INDEXED`, which reads every run's rows in rowid order and keeps one run's | 4,604 ms and 286.5 MB with one run in the table; 9,211 ms, 13,179 ms and 16,678 ms with two, three and four |

Three readings of it:

- **The run's first and last rowid cost ten page reads, and counting its rows costs the run's whole part of the index.** Asked as two statements, `MIN` and `MAX` are one descent of `shingle_by_run_id` each. Asked in one statement, SQLite walks the same range of the index as `COUNT(*)` does: the same plan, the same 209.3 MB, the same 51,092 reads, and about 20,000,000 steps against 49 and 48. On the solid-state disk that was 2.9 s for run *b*, and 0.2 s for run *a*, whose part of the index lies in one stretch. On a disk reading 100 pages a second, 51,092 pages would be eight and a half minutes, spent to announce a wait. That figure is arithmetic, not a measurement.
- **Reading the file from end to end first makes the read a warm one**, where the machine has memory to keep the file cached. On the solid-state disk that turned 8 to 9 s into 1.3 to 2.2 s, the pre-read included. On `H:` it is unmeasured.
- **Reading the whole table instead of going through the index reads fewer bytes for one run and more for every run kept.** It needs no index page, so one run costs 58% of the bytes. It grows by a run's worth with every stage-2 run the table holds, and nothing deletes a run's rows (ADR-077).

## Decision

### 1. Stage 3 says that it is about to read, over how many rows at most, and how long the measurement took

**Stage 3's read of one stage-2 run's shingle rows is announced under ADR-187 §1's rule**: one INFO line before it and one after it. The rule now covers a statement that reads every row a run holds in the largest table of the database, as well as one that builds or removes an index.

```
Stage 3 (content census) is reading up to <N> shingle rows of stage 2's run before it measures anything; SQLite reads them a page at a time, from wherever in the file stage 2 wrote them, which took half an hour on a USB spinning disk for one run in a 16.7 GB database, and stopping before it ends loses only the time spent
Stage 3 (content census) measured shingle document frequency in <S> s
```

- **The first line is new.** It is written after `Stage 3 (content census) starting under run <id>` and before the read, and only when stage 2's run holds at least one shingle row. A run with none has nothing to wait for, and stage 3 says nothing about reading it. A stage 3 already recorded under its run reads nothing and says nothing about reading: it writes `was already recorded under run <id>`, as before, and neither of these two lines.
- **The second line is the one stage 3 already wrote, with the time added.** It is written whenever stage 3 measures, whether or not the first was. `<S>` is the wall clock of `DocumentFrequency.measure`, in seconds with one decimal: the read of stage 2's survivors, the read of the shingle rows, the counting, and the writing of the frequency rows. `pipeline` times the call it makes. Timing the one statement apart from the rest would need `measure` to report it, and this record does not ask for that. Both samples on `H:` found the thread in the read.
- **The first line says the worst that has been measured and what a stop costs**, as ADR-187's does, because those are what the operator needs when deciding whether to wait. `measure` writes its first row only after the read has ended, so a process stopped during the read loses the read and none of stage 3's measurement, and the next invocation begins stage 3 again.
- **Nothing is written between the two lines.** ADR-187 §1 refused a line on a timer, because it reports the clock and would go on being written if SQLite had hung. Progress over `<N>` is a different thing, and it is possible for a read. It is not decided here (What this does not decide).

### 2. `<N>` is the span of the run's own rowids, asked as two statements

`<N>` is `MAX(rowid) - MIN(rowid) + 1` over the rows of stage 2's run:

```sql
SELECT MIN(rowid) FROM shingle WHERE run_id = ?;
SELECT MAX(rowid) FROM shingle WHERE run_id = ?;
```

- **Two statements, never one.** Each is a single descent of `shingle_by_run_id`: about 2 ms and 40 KB for the pair, cache emptied. One statement asking for both walks the run's whole part of the index, as `COUNT(*)` does: 2.9 s and 209 MB for a run of 2,500,000 rows written into freed pages, cache emptied, on a solid-state disk (Measurements).
- **Not the table's `MAX(rowid)`**, which ADR-182 §2.4 and ADR-187 state for a build and a removal. Those are over every run's rows. This read is over one run's, and a table that has seen two stage-2 runs holds both.
- **It is exact when the run's rows are one unbroken stretch of rowids**, which they are when one stage 2 wrote them with no other run writing in between. It is too high when a run was stopped, another run wrote, and a value put back resumed the first (ADR-156, ADR-181): the span then takes in the other run's rows. Nothing deletes a shingle row. So the line says "up to", as ADR-187's do.
- **A run with no shingle row answers `NULL` to both**, and that is the test for writing no line.

### 3. `pipeline` writes both lines, and `similarity` answers the bound

**`similarity` owns `shingle` (ADR-041), so the two statements of §2 are written there, and `pipeline` asks for their answer.** The method is on `DocumentFrequency`:

```java
public OptionalLong shingleRowsUpTo(RunId stage2RunId)
```

It answers the span of the rowids `stage2RunId` wrote in `shingle`, greatest less least plus one, and an empty value for a run with no shingle row. It runs §2's two statements, `MIN` and then `MAX`, and the comment beside them says why they are two (§2, Measurements).

- **On `DocumentFrequency`, because the bound is a bound on that class's own read.** `measure(stage3RunId, stage2RunId)` is the read being announced, and this is how many rows of the same run it will read at most. `ContentCensusTasklet` already holds a `DocumentFrequency`, so its constructor takes nothing new.
- **Not on `ShingleHashIndex`**, where `shingleRowsUpTo()` is. That class is about one index, `shingle_by_hash`, which this read does not use, and its method is over the whole table. The name is the same on purpose: both answer "shingle rows, up to", one of the table and one of a run.
- **It writes no line.** `ContentCensusTasklet` asks for the bound, writes the first line where a value comes back, times the call to `measure` it already makes, and adds the time to the line it already writes. `similarity` knows no stage and logs nothing here, as `ShingleHashIndex` logs nothing for ADR-187.

**No SQL in `pipeline`'s main tree names `similarity`'s table.** A rename of `shingle` or of `run_id` is a change inside `similarity`, beside the other statements that name them, and `pipeline` reaches the bound through a method the compiler checks.

**The first version of this record decided otherwise, and its reason fell away before it merged.** It had `ContentCensusTasklet` run the two statements itself, the first SQL literals in `pipeline`'s main tree to name another module's table, under the raw-SQL gap ADR-041 records, which `ModuleBoundariesTest` cannot see. It accepted that for one reason: a commit under `similarity/` moves every stage-2 run id (ADR-058, ADR-182 §2.5), and on the archive that is a whole stage 2 under a new run. ADR-188 then reached main. It moves stage 1's run id through `corpus`, and *"Every later stage's run id moves with it"*. A database upgraded across that change replays stage 2 under a new run whatever this record does. So keeping `similarity` untouched no longer spares the archive anything, and the operator chose on 2026-10-04 to put the statements where the table is.

### 4. Nothing here shortens the read

**The half hour is most likely the price of where stage 2's rows lie, and this record does not move them.** That the layout is the cause is a prediction from the probe, not something measured on `H:` (Measurements). The routes considered:

- **Read the database file from end to end before the read**, so that the read finds its pages in the file cache. It can be done from `pipeline`. Not taken. It is unmeasured on `H:`. It needs the machine to keep a file of 16.7 GB and growing in memory, where 8.7 GB were free that evening. ADR-187 left the same idea undecided for the drop, and gave both of those reasons.
- **Read the table without the index**, by rowid range or with `NOT INDEXED`. Two fifths of the pages are index pages, and in run *a* the steps longer than 64 pages number two for each index leaf. Not taken. It reads more for every stage-2 run the table keeps (Measurements). The table's own leaves were still met mostly backwards in runs *c* and *d* (56.6% and 79.2% of steps). And nothing here measured it on a spinning disk.
- **Keep stage 2's rows in file order**, by not freeing pages into the file it writes to. That is ADR-187 §2, which keeps the drop, or ADR-009, which keeps one database file. This record measures a layout neither looked at and reopens neither.

### 5. No line about the drive beyond these

**The tool does not look at what the working directory is stored on, and says nothing general about it.** #410 asks whether it should, three steps having waited on `H:` in one day. Each of those waits now has a line of its own before it, which says what is being done and that it can take long. A warning at start-up would need the tool to tell a spinning disk from a solid-state one on every operating system it runs on, and a rule for when to say so, and neither has been measured.

## Why this shape, and what the others cost

- **One line before, with the table's `MAX(rowid)`**, reusing `ShingleHashIndex.shingleRowsUpTo()`. Refused. It states every run's rows. In a table that holds several runs that is several times what the read covers, and it grows with every run kept.
- **`COUNT(*)` for an exact figure.** Refused in §2: it reads the run's part of the index, which by the arithmetic under Measurements is minutes at the rate `H:` was sampled at.
- **Stage 2's own record of how many rows it wrote.** There is none. `extraction_metric` holds no shingle count, and adding one is a schema change and a change under `extraction/`.
- **A new line after the read, and the old line left alone.** Refused. The two would be written one after the other about the same call. One line that states the time says both.
- **Progress from SQLite's progress callback**, which ADR-187 left undecided for builds. It counts steps of SQLite's virtual machine, not rows, and was not measured for this read. It stays where ADR-187 left it.

## Consequences

- **A stage 3 with rows to read says so, and says how many at most.** The wait of 2026-10-04 would have begun with a line that named it, and ended with a line that timed it.
- **This change moves the run ids of stages 2 to 6b.** It touches `similarity` and `pipeline`. Stage 2's implementation version spans `extraction` and `similarity` (ADR-058, `StageModules.EXTRACTION`), so, taken alone, this change replays stage 2 under a new run id. Stages 3 and 4 span `similarity` and `pipeline`, stages 5 to 6b span `pipeline`, and each names its upstream run (ADR-048), so every stage after stage 2 runs again under a new run id too.
- **On a database upgraded across ADR-188 it adds no replay to the one that upgrade already costs.** ADR-188 is on main. It moves stage 1's run id, and every later stage's with it, at the first invocation on a build that has it. This change's moves fall in the same invocation, provided this change is in the build the database is first run on after that upgrade. If it lands after that run, it costs a replay of stage 2 and of every later stage of its own. #320 plans no corpus run until ADR-188, ADR-189 and ADR-190 are all on main, so that their re-mints are paid once. That is the window this change has to land in.
- **The first stage 3 after the upgrade reads a new stage-2 run's rows, not the ones read on 2026-10-04.** Stage 2 runs again first, under its new run id, over cached conversions (ADR-070). Where `shingle_by_hash` is there, it drops it, with ADR-187's two lines, and it writes its rows into whatever pages that frees. Stage 3 then reads those rows. On `H:` that read is expected to be slow for the reason the one of 2026-10-04 most likely was, and how slow is not known. It will be the first one announced and the first one timed.
- **Every stage 3 after a stage 2 that dropped the index is expected to be slow on a spinning disk**, not only the one of 2026-10-04. In the probe, the runs written after a built index was dropped, *c* and *d*, lie further out of read order than the one before them, and the archive's later runs are written the same way. The layout is measured, off `H:`. The cost is predicted from it and measured nowhere. Either way the read is announced.
- **A new working directory is expected not to be affected, and that is not measured either.** Its first run is written into a new file, and its read never stepped more than 64 pages in the probe. It is still read one page at a time, with 78.5% of steps not to the next page and 19.5% backwards, and nothing was run on a spinning disk.
- **`pipeline` names no table of `similarity`'s** (§3). ADR-041's raw-SQL gap is not used by this record. `ModuleBoundariesTest` holds nothing more than it did: `pipeline` may already depend on `similarity`, and a string is still invisible to it.
- **No schema version moves** (ADR-059). No table changes and no index is added.
- **Text that states the old lines must change with the code**: before this record nothing in `src/test`, `README.md` or `docs/` quoted `measured shingle document frequency`, so the only text is the tasklet's own.
- **`AGENTS.md` says no defect is known and open against what ships**, and the change that ships this record adds #410 to that paragraph as closed by it, the thirty-third.
- **`CONTEXT.md` needs no new term.** Stage, run, invocation, survivor and shingle are used as defined there.

## Tests

**`ContentCensusSaysHowManyShingleRowsItReadsInvocationTest`** (`src/test/java/io/algernon/vespera/pipeline/`) is a whole-job slice test on `@CascadeSliceTest` over `ConverterStopsPartwayBeans`, and reads what the invocation wrote through `OutputCaptureExtension`.

1. A stage 3 over a second stage-2 run, in a table that already holds an earlier run's rows, writes the reading line once, between `starting under run` and `measured shingle document frequency`. The line states the span of that run's rowids, which the test shows to be the run's row count and less than the table's greatest rowid. It says that stopping loses only the time spent, and the measured line states seconds to one decimal. **Failed before the code was written**, at the claim that the reading line is there: stage 3 read and said nothing first. Two runs are used so that a line stating the table's `MAX(rowid)` does not pass.
2. A stage 3 already recorded under its run writes `was already recorded under run` and neither of the two lines. Passed before and has to go on passing.
3. A stage 3 over a run with no shingle row, each text being shorter than one shingle, starts and measures and writes no reading line. Passed before and has to go on passing.

**`DocumentFrequencyTest`** (`src/test/java/io/algernon/vespera/similarity/`) gains three tests of `shingleRowsUpTo(RunId)`, on that class's own `@JdbcTest` slice, with shingle rows written by hand:

1. For the second of two runs, each written in one unbroken stretch, the bound is that run's own row count, and less than the table's greatest rowid.
2. For a run with no shingle row, the bound is empty.
3. For a run another run wrote in between, the bound is more than the run's row count: the span takes in the other run's rows, which is why the line says "up to".

**`ContentCensusTaskletTest` builds the tasklet by hand**, through the constructor the tasklet had before this record: `DocumentFrequency`, `ConfidenceDistribution`, `StageRuns`, `Ledger`, `ProfileStore`, `Clock` and the working directory. It gains one test: no field the tasklet declares has a type in a package under `org.springframework.jdbc` or `javax.sql`, so it keeps no database handle of its own. The test checks declared field types and nothing in a method body. No class is added, so `CascadeSliceTest`'s `@Import` list is as it was.

Nothing here tests the two-statement form of §2. A test cannot tell one statement from two by what it answers, and the test profile's database is in memory. The second probe is the evidence, and the comment beside the statements has to say why there are two.

## Not known

- **Whether the layout is what made the read slow on `H:`**, or whether `H:` is that slow for any cold read of this size. Nothing was run there.
- **How many shingle rows the run read on 2026-10-04 held.** The database was opened by nothing but the invocation. The line this record adds will say, the next time.
- **Whether `H:` read 100 pages a second for the whole half hour.** It was sampled once.
- **Which case the run's pages were**: written into pages freed by a row-grown index, or by one stage 4b had built (ADR-187's Not known). Both were out of read order in the probe.
- **Why the probe's later runs lie worse than run *b***: the kind of index dropped, or how many freed pages the run took. The probe varied both together.
- **How the archive's pages actually lie.** The probe's runs are of equal size, written against the same occurrence ids, with random hashes. The archive's are not of equal size. That SQLite takes freed pages before new ones is not expected to depend on those. How far out of order the result is may.
- **Whether a second stage 3 over the same run is fast on `H:` with the file cache warm.** It was seven to eight times faster on the solid-state disk.
- **The spindle speed of `H:`**, which Windows does not report for it.

## What this does not decide

- **Shortening the read** (§4). Two of the three routes are measured off `H:`, and none on it. The first needs a measurement on `H:` while no run is using it, and a rule for a machine that cannot cache the file. The second is a change to `measure`'s statement and the third reopens where `shingle_by_hash` lives. Neither has been measured on a spinning disk.
- **Whether ADR-187 §2's arithmetic should be redone with the next stage 3 in it.** The drop saves stage 2 an estimated 15 to 41 minutes on the disks measured. On `H:` the stage 3 after it took 31 minutes 40 seconds. How much of that the drop caused is not known: how long that read takes over a run laid out in order is not measured there.
- **Progress during the read.** A count of rows read, against `<N>`, can only be taken where the rows are read, which is the row callback inside `DocumentFrequency.measure`. This record now changes `similarity`, so run ids no longer argue against it. It still needs a decision on cadence under ADR-093 and a way for `similarity`, which logs nothing and knows no stage, to hand the count to `pipeline`. Nobody has decided either. A ticket of its own if the two lines prove too little.
- **Where a working directory should be kept**, and whether `README.md` should say that a portable spinning disk makes these three waits long. That is the operator's.
