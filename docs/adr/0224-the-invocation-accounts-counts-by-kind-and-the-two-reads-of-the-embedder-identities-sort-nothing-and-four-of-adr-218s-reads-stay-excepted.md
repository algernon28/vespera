# ADR-224 — The invocation account's counts by kind and the two reads of the embedder identities sort nothing, and four of ADR-218's reads stay excepted: the containment candidates, the review list's read and 5f's two

> **Partly amended — see [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md).** What this record says of `shingle_by_hash` and its build is of the index over every run's rows, which no longer ships once ADR-221 is built: stage 4b builds the index on the granularity and the hash, over the rows of the one stage-2 run it reads. So these do not hold as written. Calibration's "The build of `shingle_by_hash` over 4,500,000 rows wrote 411,327,644 bytes of temporary files" is of the earlier build. Row 6's "a search of that index on the run, the granularity and the hash": the search is on the granularity and the hash, and the two temp B-trees are as written. Row 6's "As shipped", with its 15.0 bytes a row and its times, and "where the shipped build writes 91.4" in form B's row and in §4, were measured with the earlier index and of the earlier build; the build now writes 23.3 to 24.4 bytes of temporary files for each row of the run, and the containment read was not measured again under the new index. §4's "form A's cost, and the build's, grow with the `shingle` rows of earlier stage-2 runs, and whether those rows are kept is #468's": the rows are kept, the room the build needs no longer grows with them, and form A's cost was not looked at again. §4's "so ADR-218 §1's 91.6 and §5's 183 would move" and *Keeps*' "its 91.6 and its 183 bytes a row" are of the build ADR-221 replaces, and ADR-221 amends both in ADR-218. The four exceptions, §1 and the sequencing of form C after #468 stand.

> **Partly amended — see [ADR-225](0225-stage-4b-reads-its-candidate-pairs-and-containment-candidates-a-thousand-at-a-time-and-shingle-by-hash-ends-in-the-occurrence.md).** §4, *"Row 6 stays excepted here"*, no longer holds as of ADR-225's build: it takes form C, on the index of ADR-221 with the occurrence as a third column, and the statement of row 6 is gone. So three of this record's four exceptions are left, rows 7, 10 and 11. Everything else in this record stands as the note above leaves it.

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10. It rests on the operator's five answers recorded under *Rests on* and, for one choice, that a constant its count's text lacks ends the account there (§1), on the operator's delegation of the ticket's remaining decisions to the session holding it, recorded on [the ticket](https://github.com/algernon28/vespera/issues/477#issuecomment-6096217049). **§1 was built in `src/main` on 2026-10-10**, in the same pull request, by the session that builds: the tests that pin it were written first, those of their claims that §1 changes failing until `src/main` had it, and pass against the build (Tests, *What the commit that builds `src/main` owed*). The banners on the records it amends, its row in the index and its constant in `Adr.java` were written the same day (*What is written with this record, and what is still owed*).
- **Makes exceptions to**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s bound, as [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) reads it, for four statements, by the operator's choice, each with its measured size (§2 to §4). They are four of the reads [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) §3 excepted as a class and said were *"to be looked at again after #458"*. This record is that look for the seven rows no other ticket holds, and it adds no exception ADR-218 did not make.
- **Amends**: ADR-218 in five places. Its §3's class, which [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) left as rows 6, 7, 10 to 13 and 15 to 20: rows 15, 19 and 20 leave it now that §1 is built, so it is rows 6, 7, 10 to 13 and 16 to 18, and §3's *"Three of the reads, row 18's and row 20's two, were not measured"* holds of row 18 alone. Its Measured table in three rows: row 20's *"not measured: in memory at 20,000 and 10,020 rows; row 19's plan"* is 14.6 bytes a row for the extraction faults and 24.0 for the cluster faults; row 6's 15.0 is the figure for hashes of 64 bits, and 7.9 to 8.0 where the hashes are the numbers 1 to 100; row 10's *"4.0 to 5.0"* is 6.0 to 7.2 with seed ids the size the probe for this record wrote (Measured). Its *"Bounding each read of §3 … Not measured"* of *Alternatives refused*: measured here for the seven rows. Its *"Rows 18 and 20 at a size where their sort leaves memory"* and *"The cost of any bounded form"* of *What this record does not measure*: row 20 is measured, and the bounded forms of the seven rows are. Its Tests row's *"rows 1, 2, 6 to 13 and 15 to 20"*: less rows 15, 19 and 20 now that §1 is built. ADR-220 in these places, which its banner lists: §15's table in its rows *"15 … the statements are unchanged and still sort"* and *"19, 20 … as they were"*, which stop holding now that §1 is built; §15's two *"is #477's"* and the same item of its *What this does not decide*, which this record answers; and the count of two for `RelevanceDistribution` that its *Amends* and its Tests row give `EveryStatementThatSortsIsRecordedTest`, the class holding no statement that sorts and the test no entry for it. [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) §2's *"The counts are queries that select a count and a closed column"*: the three counts by kind select counts and no column, the kinds being named in the statement's text (§1); and its §3's *"the reads are `COUNT`s and nothing else"*: each of the three is a `COUNT(*)` and, for each constant, a `SUM` of a comparison that is 1 or 0 for a row, which counts rows and carries none out. ADR-060's list of exceptions, as ADR-218's banner on it words the third: *"the reads that sort a row for each survivor, verdict, vector or cluster, as a class"* loses the reads over `vector` and the account's three, and *"three reads and ten index builds"* is one read and ten index builds. Each of those records carries a banner at its head saying so, and is otherwise not edited.
- **Keeps**: ADR-198 §2's allow-list and §3's exception to ADR-041 in its three bounds, counts and nothing else, by a closed column or by none, in `InvocationAccount` and nowhere else; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §3.3, the same six counts in the same class; ADR-211 §10, 5f holds its largest partition whole; [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md)'s review list, by path; [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s order of a partition's members; ADR-218 §1, §2 and §5, its 91.6 and its 183 bytes a row, and the thirty-one indexes of `schema.sql`.
- **Rests on**: the operator's five answers of 2026-10-10 to the five questions the handover of [#477](https://github.com/algernon28/vespera/issues/477) put, given twice, through a question form in the session that held the ticket and to the session that keeps the coordination board as *"yes to all five"*, and passed to the session that wrote this record: rows 15, 19 and 20 are bounded with no new index, at the cost §5 states; rows 10 and 11 stay excepted with no new index; row 7 stays excepted; row 6 stays excepted here and its bounded form goes to [#476](https://github.com/algernon28/vespera/issues/476), after [#468](https://github.com/algernon28/vespera/issues/468); and rows 15, 19 and 20 ship in the same build as PR 478, #353 and #472, timing allowing (§6). The operator's later decision that #468 joins that build, as relayed by the session that keeps the coordination board (§6). The measurements of the handover, the ticket's comment of 2026-10-10 (Measured). [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and ADR-222 (which run ids move, §5). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Settles** [#477](https://github.com/algernon28/vespera/issues/477) as a decision. Its acceptance asks for `EveryStatementThatSortsIsRecordedTest` to hold the new counts, which it does since §1 was built (Tests).

## Context

ADR-218 excepted from ADR-060's bound, as one class, the reads that sort a row for each survivor, verdict, vector or cluster, each with its size, and set aside whether any of them should be bounded until #458 had said which of them stay. ADR-220 answered #458: two of the reads are gone, five go with #472, and of seven it says only that whether they stay or are bounded *"is #477's"*.

The seven are rows 6, 7, 10, 11, 15, 19 and 20 of ADR-218's table, nine statements in five classes. ADR-218's line numbers for them no longer hold, so they are named here by what they read, as ADR-220 §15 names them:

| Row | Stage | Class | The statement reads | What sorts |
| ---: | --- | --- | --- | --- |
| 6 | 4b | `similarity.RedundancyResolution` | the containment candidates of one occurrence: the `shingle` rows of the run that carry any of its rarest hashes | `GROUP BY` and `count(DISTINCT)` |
| 7 | 2, as it ends | `ledger.Verdicts`, `eachExtractionFailure` | the run's `extraction-failed` verdicts, for the review list | `ORDER BY o.path` |
| 10 | 5f | `embedding.RelevanceScoreCache`, `winningSeeds` | the winning seeds of the scoring run | `DISTINCT` |
| 11 | 5f | `embedding.RelevanceScoreCache`, `partitionMembers` | the members of one seed partition | `ORDER BY occurrence_id` |
| 15 | 5e, the relevance report | `embedding.RelevanceDistribution`, `anyEmbedderIdentity` and `embedderIdentityFor` | every row `vector` keeps, outside any run | `DISTINCT embedder_identity`, in both |
| 19 | the invocation account | `pipeline.InvocationAccount` | the verdicts of the invocation's runs, counted by kind | `GROUP BY kind` |
| 20 | the invocation account | `pipeline.InvocationAccount`, two statements | the extraction faults of the invocation's runs, counted by category; the cluster faults, by kind | `GROUP BY category`; `GROUP BY kind` |

## Measured

**Whose measurements these are.** They were made on 2026-10-10 by the analyst of the session that held #477 before this record was written, and are taken from its handover. The session that wrote this record measured nothing and ran no probe. It read the statements, `StageModules`, the three enumerations and `EveryStatementThatSortsIsRecordedTest` in the tree at `5b0cc20`, and what it found there that the handover does not say is marked as read here.

**Method, as the handover gives it.** ADR-218's: Java 26.0.2.1, sqlite-jdbc 3.53.2.1 (SQLite 3.53.2), the shipped `schema.sql` and the shipped URL's parameters, and SQLite's directory for temporary files set on a connection to no database before the database was opened. The sizes of the files in that directory were summed every 5 ms and the greatest sum kept. A time is the best of three runs, on one plain connection, the rows pulled into Java. A solid-state NVMe disk, NTFS, the file cache warm. The probe is throwaway and outside the repository, and its databases are deleted.

**Ledgers.** Synthetic only (ADR-196), at ADR-218's ratios: one of 1,000,000 occurrences and one of 2,000,000, and a smoke run of 20,000 in which nothing left memory, with an earlier scoring run's scores written before the run's own. **Every pair of figures below is the smaller ledger's and then the larger's**, and each size is given with the rows it is for. Row 20's two tables were filled to 250,000 and 500,000 rows, far above those ratios, so that the sort leaves memory.

**Calibration.** The build of `shingle_by_hash` over 4,500,000 rows wrote 411,327,644 bytes of temporary files, where ADR-218 records 411,327,654. Rows 11, 15 and 19 came to ADR-218's bytes a row. Row 7 came to 101.0 where ADR-218 records about 100.

**"The same rows"** below means the same rows in the same order as the shipped statement's, compared by the probe.

### Row 6: the containment candidates

Planned with `shingle_by_hash` built, the only state stage 4b reads in: a search of that index on the run, the granularity and the hash, a temp B-tree for the `GROUP BY` and one for `count(DISTINCT)`. As shipped it wrote 2,155,250 bytes for 143,995 rows, 15.0 a row, and took 479 to 517 ms a call; 1,016 ms on the larger ledger. With the probe's pool of hashes numbered 1 to 100 it wrote 7.9 to 8.0 a row, so the figure depends on how wide the hashes are, and 15.0 is that of hashes of 64 bits.

| Form | Plan | Temporary files | Time a call | Rows | Cost |
| --- | --- | --- | --- | --- | --- |
| A: the statement naming `shingle_by_occurrence` | that index read whole, a temp B-tree for `count(DISTINCT)` alone | none | 727 to 859 ms; 1,646 ms | the same | every `shingle` row of every stage-2 run is read on each call |
| B: an index on `(run_id, shingle_parameter_identity, shingle_hash, occurrence_id)` in place of `shingle_by_hash`, the text unchanged | a covering search, a temp B-tree for the `GROUP BY` | 15.0 a row | 39 ms; 77 ms | the same | the build writes 95.6 bytes a row of temporary files, where the shipped build writes 91.4 |
| C: B's index, one statement a hash, the 32 ordered reads merged in Java | a covering search, no temp B-tree | none | 37 to 62 ms; 84 ms | the same | the same build; 32 cursors open at once |

### Row 7: the review list's read

Planned by `verdict_by_run_id`, then `file_occurrence` by its primary key, and a temp B-tree for the `ORDER BY`. As shipped it wrote 5,048,363 bytes for 50,000 verdicts and 10,098,372 for 100,000, 101.0 a row, and took 143 ms; 284 ms.

| Form | Plan | Temporary files | Time | Rows | Cost |
| --- | --- | --- | --- | --- | --- |
| The walk's occurrences read in path order through the index SQLite keeps for `file_occurrence`'s unique `(walk_id, path)`, each joined to its verdict through `verdict_by_occurrence` | no temp B-tree | none | 443 ms; 8,072 ms | the same, a path being unique within a walk | every occurrence of the walk is read to find the one in twenty that failed |

The second time is 28 times the shipped statement's where the first is 3 times, and the handover does not explain the jump. **The 8,072 ms is one reading.**

### Rows 10 and 11: 5f's two reads

Row 10 is planned by `relevance_score_by_run_id` and a temp B-tree for the `DISTINCT`. As shipped it wrote 4,500,027 bytes for 750,000 scores and 10,832,086 for 1,500,000, 6.0 to 7.2 a row, and took 186 ms; 816 ms. ADR-218's 4.0 to 5.0 was with smaller seed ids.

Row 11 has a temp B-tree for the `ORDER BY`. As shipped it wrote 2,088,497 bytes for 350,000 members and 4,188,509 for 700,000, 6.0 a member, and took 245 ms; 472 ms.

| Row | Form | Temporary files | Time | Rows | Cost |
| ---: | --- | --- | --- | --- | --- |
| 10 | A: the least winning seed above the last one answered, asked once for each seed and once more | none | 1,340 ms; 4,024 ms | the same | the run's scores are read once for each seed |
| 10 | A′: A naming `relevance_score_by_winning_seed_occurrence_id` | none | 0 ms where the run's rows come first in the table; 497 ms; 1,101 ms behind one earlier run's | the same | grows with the scores of earlier runs the table keeps |
| 10 | B: a new index on `(run_id, winning_seed_occurrence_id, occurrence_id)`, the text unchanged, which the planner takes unprompted | none | 49 ms; 114 ms | the same | the build below |
| 11 | A: the statement naming the index SQLite keeps for the table's primary key | none | 265 ms; 721 ms a partition | the same | every run's scores are read once for each partition; it names an index SQLite named |
| 11 | B: the same new index | none | 58 ms; 136 ms | the same | the build below |
| 11 | C: `ORDER BY rowid` | none | 196 ms; 476 ms | the same only because the probe wrote the scores in occurrence order | ADR-087's order would rest on the order of writing and not on the statement |

**The new index's build** wrote 125,037,978 bytes of temporary files for 1,500,000 rows, 83.0 to 83.4 a row, over every run's scores. It would be a thirty-second index of `schema.sql`, built at the first start after the upgrade (ADR-218 §2). It would have to be added and not put in the place of `relevance_score_by_run_id`, which ADR-220's reads a page at a time are planned by.

### Row 15: the two reads of the embedder identities

Both are planned as a read of the whole of the index SQLite keeps for `vector`'s primary key, covering, and a temp B-tree for the `DISTINCT`. The handover gives the bytes and not the rows: its ledgers are at ADR-218's ratios, and ADR-218 records the same bytes for 200,000 and 400,000 rows of `vector`, with identities of 15 characters.

| Statement | As shipped | Bounded form | Plan of the bounded form | Its temporary files | Time, shipped and bounded | Rows |
| --- | --- | --- | --- | --- | --- | --- |
| `anyEmbedderIdentity` | 3,600,012; 7,200,024 bytes, 18.0 a row | `SELECT MIN(embedder_identity) FROM vector` | a covering search, no temp B-tree | none | 71 ms; 197 ms, and 29 ms; 97 ms | the same identity; over an empty table it answers one row holding NULL |
| `embedderIdentityFor` | 1,800,006; 3,600,012 bytes, 9.0 a row | `SELECT MIN(embedder_identity), MAX(embedder_identity) FROM vector WHERE embedder_identity LIKE ? ESCAPE '\'` | the covering index read whole, no temp B-tree | none | 52 ms; 151 ms, and 32 ms; 66 ms | one identity where the least and the greatest are the same |

`DISTINCT … LIMIT 2` still sorts. An index on `embedder_identity` wrote 21.8 to 21.9 bytes a row to build, and neither bounded form needs it.

### Rows 19 and 20: the invocation account's counts by kind

Each is planned by a search on the run, `verdict_by_run_id`, `extraction_fault_by_run_id` and the index SQLite keeps for `cluster_fault`'s primary key, and a temp B-tree for the `GROUP BY`.

| Statement | Rows | Temporary files as shipped | Bytes a row | Time as shipped |
| --- | --- | ---: | ---: | --- |
| Row 19, verdicts by kind | 250,000; 500,000 | 3,900,015; 7,800,027 | 15.6 | 153 ms; 413 ms |
| Row 20, extraction faults by category | 250,000; 500,000 | 3,638,914; 7,277,806 | 14.6 | 103 ms; 312 ms |
| Row 20, cluster faults by kind | 250,000; 500,000 | 6,000,019; 12,000,029 | 24.0 | 330 ms; 853 ms |

**Row 20 is measured here for the first time.** The cluster faults' figure is the greater because their kinds' names are longer.

The forms, with row 19's figures:

| Form | Temporary files | Time | What it costs or changes |
| --- | --- | --- | --- |
| A: one statement, `SELECT COUNT(*), SUM(kind = 'BROKEN' COLLATE NOCASE), … WHERE run_id IN (…)`, a `SUM` for each constant of the enumeration, `other` being the total less the sums | none | 144 ms; 344 ms | the same search, no temp B-tree, the same counts |
| B: a `COUNT(*)` for each kind, and a total | none | 839 ms; 1,662 ms | ten reads of the same rows |
| C: a new index on `verdict (run_id, kind)` | 15.6 a row with a list of two runs; none only as one statement a run | 50 ms; 102 ms | the build writes 85.5 to 89.6 bytes a row |
| D: every row handed to Java and counted there | none | 184 ms; 532 ms | a row is carried out, which ADR-198 §3 forbids |

Row 20 under form A wrote no temporary file for either table, and took 111 ms; 386 ms for the extraction faults and 262 ms; 547 ms for the cluster faults.

## Decision

Each of §1 to §4 is the operator's answer of 2026-10-10 to the handover's question, each put with the analyst's recommendation and each answered by accepting it.

### 1. Rows 15, 19 and 20 are bounded, with no new index

**`anyEmbedderIdentity` asks for the least identity and sorts nothing.** Its statement is `SELECT MIN(embedder_identity) FROM vector`. An empty table answers one row holding NULL, which the method answers as empty, as it answers no row now.

**`embedderIdentityFor` asks for the least and the greatest identity under the name and sorts nothing.** Its statement is `SELECT MIN(embedder_identity), MAX(embedder_identity) FROM vector WHERE embedder_identity LIKE ? ESCAPE '\'`, with the pattern it binds now. *"Exactly one identity answers to that name"* is: the least is not NULL, and the least is the greatest. Two identities under one name answer empty, and none answers empty, as now.

**Each of the account's three counts by kind is one statement that selects counts and no column.** Form A: `SELECT COUNT(*), SUM(<column> = '<CONSTANT>' COLLATE NOCASE), … FROM <table> WHERE run_id IN (…)`, with one `SUM` for each constant of the enumeration the line is written from, `VerdictKind` over `verdict.kind`, `FailureCategory` over `extraction_fault.category` and `ClusterFaultKind` over `cluster_fault.kind`. The count written as `other` is the total less the sums. Four things are kept, and a test holds each (Tests):

- **A kind no row carries writes no line, and neither does `other` at zero.** `SUM` over no row answers NULL, which is read as zero.
- **The lines are worded and ordered as now**: the verdicts' and the cluster faults' kinds by the constant's name, the extraction faults' categories by its name in lower case, each line set in ascending order of the name written, `other` among them. The text names each constant as the enumeration declares it, in capitals, for all three; a category is stored in lower case, and `NOCASE` is what makes the two meet.
- **Each text is a constant the test can plan**, naming every constant of its enumeration in its own text, and completed at run time by the list of runs alone. A text built from the enumeration at run time is not in the class's compiled strings as a statement: `EveryStatementThatSortsIsRecordedTest` would not plan it, and nothing would hold that the text and the enumeration agree. A test holds that they do (Tests).
- **Each text still opens with `SELECT ` and carries `COUNT(*)`**, which is what `EachTableIsNamedOnlyByItsOwnerTest` holds of every statement the account is allowed (ADR-209 §3.3). Form A does.

**`NOCASE` is not `equalsIgnoreCase`, and the difference is recorded.** `NOCASE` folds the 26 letters of ASCII and no other character. Java's comparison also folds a character outside ASCII whose other case is an ASCII letter, as the Kelvin sign is to `k`. A stored value that differs from a constant's name only by such a character is counted with its kind now and would be counted under `other`. **No writer stores one, so no line the account writes changes.** Read at `5b0cc20`: `ledger` writes a verdict's kind and `synthesis` a cluster fault's as the constant's own name; and an extraction fault's category reaches `pipeline` from `OccurrenceJudge`, which sets an occurrence aside in two places: with the category `ResponseScope` gives a response it blames on the converter, the constant's name in lower case under the root locale, and with the literal `timeout` of its own, for timeouts in a row. `ExtractionFaults` stores either as it is given. Every name of the three enumerations is ASCII letters and underscores. A row written by hand, or by a build that named its constants otherwise, is the only place the two comparisons could part.

**A constant the text lacks ends the account at that count, and that is chosen.** The writer reads one sum for each constant of the enumeration, by position, in the order the enumeration declares them, which is the order each text names them in. Read in `InvocationAccount` as built:

- **An enumeration given a constant its text does not name** has one constant more than the statement has sums. The read of the sum that is not there throws, `afterJob` catches it and writes ADR-198 §6's one warning to `vespera.log`, and the account ends there: no line of that count, and none of what follows it, the later counts, the failed steps' lines and `invocation ended`. That is every account of an invocation that recorded a run; one that recorded none makes no count and reads no sum. The invocation does not fail (ADR-198 §6). Its rows are not counted under `other`.
- **A constant moved in its enumeration** leaves the statement as many sums as there are constants, and a count is written under another constant's name.

Neither reaches a build: `TheAccountsCountsAreTextsTheBuildHoldsTest` fails on a text that does not name every constant, and `TheAccountsCountsByKindSortNothingTest` on a line that does not carry its own constant's count (Tests). The behaviour stays as built for that reason. The choice was made at the gate of the pull request by the session holding #477, under the operator's words to it, *"you take all decisions on #477. Do not come back until it's ready to merge"*; the delegation and the choice are recorded on the ticket, in [its comment of 2026-10-10](https://github.com/algernon28/vespera/issues/477#issuecomment-6096217049). Counting an unnamed constant under `other` in a run was not built: it would need the text put together from the enumeration at run time, which `EveryStatementThatSortsIsRecordedTest` could not plan.

**No index is added**, so `schema.sql` is not edited: no DDL, no schema version, no build at start-up, and the thirty-one indexes stand.

### 2. Rows 10 and 11 stay excepted, with no new index

**`winningSeeds` and `partitionMembers` stay as they stand**, excepted from ADR-060's bound by the operator's choice, at 6.0 to 7.2 bytes a score of the scoring run and 6.0 a member of one partition (750,000 and 1,500,000 scores; 350,000 and 700,000 members).

- **Row 11's sort is less than what 5f already holds.** 5f clusters a partition whole and holds its members and their keys on the heap (ADR-211 §10). Six bytes a member on disk beside that bounds nothing a run needs.
- **Row 10's forms without an index are slower or grow with something else.** Form A took 1,340 ms and 4,024 ms where the shipped statement took 186 ms and 816 ms. Form A′ grows with the scores of earlier runs the table keeps.
- **The index's one build sorts more than every read it spares**: 83.0 to 83.4 bytes a row over every run's scores, against 6.0 to 7.2 a row of one run's.

### 3. Row 7 stays excepted

**`eachExtractionFailure` stays as it stands**, excepted by the operator's choice at 101.0 bytes for each `extraction-failed` verdict of the run (50,000 and 100,000 verdicts). The figure grows with the path and the reason carried through the sort; ADR-218's fixture, whose ratios the handover says it kept, had paths of 43 characters, and the handover does not give its own. The form that sorts nothing reads every occurrence of the walk to find those that failed, and took 3 times as long on the smaller ledger and, in one reading, 28 times on the larger. The review list is by path (ADR-175), and since ADR-220 §6 none of its rows is kept on the heap.

### 4. Row 6 stays excepted here, and its bounded form is #476's, after #468

**The containment candidates' statement stays as it stands in this record**, excepted by the operator's choice at 15.0 bytes for each `shingle` row carrying one of the occurrence's rarest hashes, with hashes of 64 bits (143,995 rows).

**Form C is carried to [#476](https://github.com/algernon28/vespera/issues/476)**, which already holds the bounded form of the same candidates on the heap (ADR-220 §15, question 8), **and is sequenced after [#468](https://github.com/algernon28/vespera/issues/468)**. It is the only bounded form found worth having, and it changes the index stage 4b builds:

- its build wrote 95.6 bytes a row of temporary files where the shipped build writes 91.4, so ADR-218 §1's 91.6 and §5's 183 would move, and by how much at the peak is not measured;
- form A's cost, and the build's, grow with the `shingle` rows of earlier stage-2 runs, and whether those rows are kept is #468's;
- any edit to `similarity` moves stage 2's run id, and stage 2 then writes its shingle rows again.

Neither #476 nor #468 is decided here.

### 5. Which run ids move

**None by this record as it is written**: it is Markdown and tests, and nothing under `src/main` (ADR-058).

**The build of §1 moves the run ids of stage 5, both runs, of 6a and of 6b.** Read in `StageModules` as it stands on this record's branch, with [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) §2 built:

| Edit | Module | Stages whose implementation version names it |
| --- | --- | --- |
| the three counts, in `InvocationAccount` | `pipeline` | seed measurement and embedding scoring, which are stage 5's two runs, and generation, 6b |
| the two reads, in `RelevanceDistribution` | `embedding` | seed measurement, embedding scoring, arrangement, 6a, and generation |

Arrangement no longer names `pipeline`, and moves for the first edit all the same, through its upstream run, stage 5's (ADR-048); it names `embedding` for the second. **Neither edit moves content census or content redundancy, stages 3 and 4**: since ADR-222 neither names `pipeline`, and neither names `embedding`. Stage 1 names `corpus` alone and stage 2 `extraction` and `similarity`. `ledger` is in no stage's list.

**This says what this record's two edits add, and not what the build an operator installs moves.** ADR-222 is in the same build and mints content census and every stage after it again, once, on its own account. Stages 5, 6a and 6b are among those. **So this record's edits add no run id to the ones that build moves, and no replay to the one it costs, on one condition: that no build carrying ADR-222 without this record's edits is run over the working directory first.** Where one is, that run pays ADR-222's replay from content census, and the first run of a build with these edits then pays a second, from stage 5. §6's one build is what keeps the condition.

**What the operator accepted with the first answer, and what moves now.** The cost was put on 2026-10-10 as: `pipeline` mints stages 3 to 6b again and `embedding` stage 5 onward; stage 5 embeds again from the vector cache; an arrangement is minted again, and `arrangementApproved` must name the new one. That was `StageModules` before ADR-222, and it was accepted as stated. What this record's edits move now is less: stage 5 onward, and stages 3 and 4 not at all. The rest of the cost is as it was put. The verdicts under the old ids stay recorded and remove nothing (ADR-156).

**What this record's build does not touch**: no DDL, so no schema version, and an existing working directory opens; no cache key; and `StageModules`, which ADR-222's change edits and this one does not.

**§2, §3 and §4 move nothing.** No statement of theirs is edited.

### 6. When it ships

**The build of §1 ships in one build with ADR-220's change, ADR-222's and those of #468 and #472, timing allowing, before the operator next runs an existing working directory**, so that the stages they move are minted again once for all of them. That it ships with PR 478, #353 and #472 is the operator's fifth answer; that [#468](https://github.com/algernon28/vespera/issues/468), whose record is numbered ADR-221, joins the same build is the operator's later decision, as relayed by the session that keeps the coordination board.

**What is fixed**:

- **PR 478, ADR-220's change, and #353's, ADR-222's, are on main**, and main is merged into this record's branch.
- **Among this record, #472 and #468 no order is fixed: whichever is gated first lands first.** That is the board's rule as of 2026-10-10, relayed by the session that keeps it. It supersedes the order the ticket gave in [its comment recording the answers](https://github.com/algernon28/vespera/issues/477#issuecomment-6095966660), *"PR 478, #353, #472, then this ticket"*, which was the earlier one.
- **Whichever of the three lands later merges main in and sets the shared files for what is then on main**: the map in `EveryStatementThatSortsIsRecordedTest`, the index, `Adr.java` and `AGENTS.md`'s count of decisions.

This record states no place for itself among the three, and nothing in it depends on one.

## Alternatives refused

Each is in Measured with its figures.

- **Row 6, form A**, naming `shingle_by_occurrence`: no temporary file, 1.5 to 1.7 times the time, and every `shingle` row of every stage-2 run read on each call.
- **Row 6, form B**: the new index with the text unchanged still sorts 15.0 bytes a row.
- **Rows 10 and 11, the new index**: a start-up build of 83.0 to 83.4 bytes a row over every run's scores, a thirty-second index. What it adds to the database file and to 5e's writes was not measured.
- **Row 11, `ORDER BY rowid`**: the order ADR-087 needs would rest on the order the scores were written in.
- **Row 11, naming the index SQLite keeps for the primary key**: every run's scores read once for each partition, by a name SQLite chose.
- **Row 15, `DISTINCT … LIMIT 2`**: it still sorts. **An index on `embedder_identity`**: 21.8 to 21.9 bytes a row to build, and not needed.
- **Rows 19 and 20, form B**: ten reads of the same rows, 839 ms and 1,662 ms against 144 ms and 344 ms. **Form C**: an index built at 85.5 to 89.6 bytes a row, which still sorts 15.6 a row once the list names two runs. **Form D**: rows carried out of tables `pipeline` does not own, against ADR-198 §3.

## Consequences

- **ADR-218 §3's class, now that §1 is built, is rows 6, 7, 10 to 13 and 16 to 18.** Rows 6, 7, 10 and 11 stay by this record, each with its reason. Rows 12, 13 and 16 to 18 are #472's and are not decided here. Rows 15, 19 and 20 are gone from it.
- **The invocation account's counts cost no temporary file whatever the invocation's runs hold**, and the relevance report's two reads of the identities none whatever `vector` keeps.
- **Nothing an operator reads changes**: the account's lines and the report's stamp are what they were, but for a stored kind that differs from a constant by a character outside ASCII (§1).
- **This record's edits have an existing working directory replay from stage 5** (§5): seed measurement, embedding scoring, arrangement and generation. The build that carries them carries ADR-222 too, which has it replay from content census, so they add no stage to what that build replays, on §5's condition: where a build with ADR-222 and without them was run first, they cost a second replay, from stage 5.
- **A new constant of `VerdictKind`, `FailureCategory` or `ClusterFaultKind` is an edit to the account's text too**, and tests in two classes fail until it is made (Tests). A build that shipped without the edit would end, at that count, every account of an invocation that recorded a run (§1).
- **ADR-060's bound keeps its exceptions on disk, fewer by five statements, three rows of ADR-218's table**, and none of what stays excepted here is new.

## Tests

**Written on 2026-10-10, against the tree at `5b0cc20`, before `src/main` had §1**, but for one test added after the build, which the table marks. They name nothing this record adds to `src/main`, so the test tree compiled before the build; the constant of `Adr.java` they name was written with them.

| Test | What it pins |
| --- | --- |
| `EveryStatementThatSortsIsRecordedTest`, ADR-218's, edited | the entry for `embedding.RelevanceDistribution`, which held 2, and the entry for `pipeline.InvocationAccount`, which held 3, are removed, and the second map, a copy of the first, loses them with it: as `schema.sql` leaves the database and with `shingle_by_hash` built. This record changes no other entry: `similarity.RedundancyResolution`, `ledger.Verdicts` and `embedding.RelevanceScoreCache` keep what they hold, the six texts read by hand are unchanged, and so are the thirty-one indexes. #472 edits the same map, so these are what this record removes and not the map's final contents |
| `embedding.TheEmbedderIdentityReadsSortNothingTest`, new | over the shipped schema in memory: `anyEmbedderIdentity` answers empty where no vector is stored, and the least of two identities whichever was stored first; `embedderIdentityFor` answers the identity where one answers to the name, empty where two do, and empty for a name nobody stored; and of the two statements the reads send, neither is planned through a temp B-tree |
| `pipeline.TheAccountsCountsByKindSortNothingTest`, new | the writer fed by hand over two runs, as `InvocationAccountTest` feeds it: a kind no row carries writes no line, and no row at all writes none; a stored value in no enumeration is counted under `other` and its text is nowhere; a stored value that differs from a constant's name only in the case of its ASCII letters is counted with its kind, a category in lower case; the lines of each count come in ascending order of the name written; and of the statements sent, one reads each of the three tables, each names every constant of its enumeration, none names `detail`, `reason`, `label`, `title` or a path, and none is planned through a temp B-tree against the shipped schema. **Added after the build**: with a different number of rows stored for every constant of each enumeration, its place in the declaration counted from one, each line carries its own constant's number. The build reads the statement's counts back by position, in the order the enumeration declares its constants, and each text names them in that order; a constant moved without the text following would write one constant's count under another's name, and one put in without it would make the count's read fail and end the account there (§1); this fails in both cases, on a line that carries another's number or on lines that are missing. It was green when written, so it was not seen to fail |
| `TheAccountsCountsAreTextsTheBuildHoldsTest`, new, in the root package, where the compiled classes are read | one text among `InvocationAccount`'s compiled strings reads each of `verdict`, `extraction_fault` and `cluster_fault`, and it names every constant of `VerdictKind`, `FailureCategory` and `ClusterFaultKind`. This is what fails when an enumeration gains a constant and the text does not, before the count's read can fail in a run (§1), and what holds that the text is one `EveryStatementThatSortsIsRecordedTest` plans |
| `AModelNameMatchesOnlyItselfTest`, `pipeline.InvocationAccountTest`, `EachTableIsNamedOnlyByItsOwnerTest` | not edited, and green before the build and after it |
| `pipeline.InvocationAccountInvocationTest` | not edited, and green after the build: the account's lines over a whole invocation |

**What was run before the build**, once, under Java 26.0.2.1, the first seven classes above: 44 tests, 5 failing, none skipped.

- **Red, as they should have been**, each on the claim this record changes: both of `EveryStatementThatSortsIsRecordedTest`'s claims on the recorded classes, which found `embedding.RelevanceDistribution=2` and `pipeline.InvocationAccount=3` and no other difference; `TheEmbedderIdentityReadsSortNothingTest`'s claim on the plans, on `SELECT DISTINCT embedder_identity FROM vector ORDER BY embedder_identity`; and, in each of the two tests of the account's statements, the claim that the statement over `verdict` names every kind, the text being `SELECT kind, COUNT(*) FROM verdict WHERE run_id IN … GROUP BY kind`.
- **Green**: every claim on what the two reads answer, and every claim on the account's lines, which is what holds that the build changed none of them.
- **Not reached then**: the claims that come after a failed one in the same test, on the statements over `extraction_fault` and `cluster_fault`, on free text and on the plans of the account's three. They were first run against the build.

**What was run after the build**, once, under Java 26.0.2.1: the three new classes, `EveryStatementThatSortsIsRecordedTest`, `InvocationAccountTest` and `InvocationAccountInvocationTest`, 43 tests, none failing and none skipped, every claim reached. The session that built `src/main` reports the whole unit suite passing before the last test of the table was added, 1,567 run, none failing and none skipped; the session that wrote this record did not run it.

**What was run on the merged tree**, at `65e9437`, with ADR-222's change merged in, as reported by the session holding #477: `./mvnw -o verify`, 1,572 unit tests and 23 integration tests, none failing and none skipped, the run then ending at the step that writes the Allure report, which cannot install Node.js offline.

**What no test holds.** Any size, as under ADR-218. That a stored kind differing by a character outside ASCII goes to `other`: no writer stores one, and the test would pin a difference nobody wants kept. Which of several identities under one name `embedderIdentityFor` would answer: it answers none.

## What the commit that builds `src/main` owed

It owed what follows, and §1 is built in `src/main` on 2026-10-10. The session that wrote this record did not write that code. It read the account's three texts in it, each naming every constant of its enumeration in the order the enumeration declares them, and ran the tests above against it.

- **`embedding.RelevanceDistribution`**: `anyEmbedderIdentity` and `embedderIdentityFor` as §1, their signatures unchanged, and their javadoc where it speaks of the rows read.
- **`pipeline.InvocationAccount`**: the three counts by kind as §1, the four things it keeps among them; the counts of occurrences, clusters and synthesis docs are not touched; and its class javadoc's *"counts are numbers grouped by a closed enumeration"* where the build makes it untrue of the statements.
- **No other class, and no `schema.sql`.**
- **Edit no test.** One the build finds it has to edit is a finding for the analyst.
- **Verify with `./mvnw verify`.**

## What is written with this record, and what is still owed

**Written on 2026-10-10**: the tests above; the constant for this record in `Adr.java`; its row in `docs/adr/README.md`; `AGENTS.md`'s sentence on ADR-218's exceptions; and the banners on ADR-218, ADR-220, ADR-198 and ADR-060, each saying what *Amends* above says of its record. The banners speak of rows 15, 19 and 20 as gone as of this record's build, which follows in the same pull request. `docs/decision-ledger.md` stops at ADR-049 and takes no row; the two rendered pages were written again by `node docs/render-docs.mjs`.

**Still owed**:

- **Nothing of the count of decisions in `AGENTS.md` or the range in `docs/adr/README.md`**: both were set on 2026-10-10, when main was merged in with ADR-222, to the 222 records the branch holds, through ADR-224 and without ADR-221 and ADR-223, which are not on main.
- **By whichever of this record, #472 and #468 lands later than another** (§6): the merge of main, and the shared files set for what is then on main. For this record that is the two entries it removes from `EveryStatementThatSortsIsRecordedTest`'s map, its row in the index, its constant in `Adr.java`, and `AGENTS.md`'s count.
- **By #472's record, when it lands, and not by this one**: the "one read" of the banners on ADR-218 and ADR-060 and of `AGENTS.md`, the read being ADR-218's row 18, and the banner on ADR-218 naming the class as rows 6, 7, 10 to 13 and 16 to 18. Each is true of main as this record leaves it, #472 not having landed. If #472 measures or removes a read of rows 12, 13 or 16 to 18, it amends those words.

## What this record does not measure

- **Anything by the session that wrote it.** Every figure is the handover's.
- **Plans as the test sees them.** The handover's plans are over filled tables with no statistics table; the test plans over empty ones. The tests plan the built forms of §1 over empty tables, or a few rows, in both states of a working directory, and find no temporary storage (Tests); no plan of the build was made over millions of rows, the handover's being of its own probe's texts.
- **The write-ahead log**, for any statement or build here.
- **Row 6 without `shingle_by_hash`**: its plan was read and it was not run, as under ADR-218.
- **Row 7's bounded form on the larger ledger** beyond one reading, and why it took 28 times as long.
- **The search line of row 11's plan**, which was not read back.
- **Row 20 at the ledger's own ratios**: its tables were filled far above them. And the cluster faults' 24.0 bytes a row with the enumeration as it is: the handover took `ClusterFaultKind` to have three constants, from a search, and at `5b0cc20` it has four, `CITATION_NOT_IN_RANGE` the fourth. Which names the probe wrote is not stated, and the figure depends on their length.
- **The new index of rows 10 and 11**: its bytes in the database file, and what it would cost 5e's writes.
- **Form C of row 6 at the peak**: its temporary files are 4.2 bytes a row more than the shipped build's; its write-ahead log and its share of the file are not measured.
- **Anything off a solid-state NVMe disk, NTFS, with the file cache warm**: no spinning disk, no `H:`, no Linux, no cold cache.
- **Any invocation.** That stage 5 embeds again from the vector cache after the build, and which stages replay, are read from `StageModules`, ADR-222 and the handover and were not run.
- **The account's and the report's reads through the application's pool**: one plain connection was used.

## What this does not decide

- **The bounded form of the containment candidates**, on disk and on the heap: [#476](https://github.com/algernon28/vespera/issues/476), after [#468](https://github.com/algernon28/vespera/issues/468).
- **Rows 12, 13 and 16 to 18 of ADR-218's table**: #472 and its record.
- **Which stage names `pipeline`**: ADR-222 decides it, and this record reads it.
- **Whether rows 7, 10 and 11 should be looked at again** if a later change gives `relevance_score` or `verdict` an index for a reason of its own. Nothing here asks for one.
