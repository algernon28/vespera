# ADR-209 — The ledger is four records behind one type, no capability module names Spring Batch, and a table's SQL is its owner's

- **Date**: 2026-10-07
- **Status**: accepted
- **Amends**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md), in its consumer contract and one Consequence, and nothing else. *"The consumer contract is a Spring Batch `ItemReader<OccurrenceId>` (concretely, `JdbcCursorItemReader` or `JdbcPagingItemReader`, built inside `ledger` from the survivors SQL)"* now reads: the consumer contract is an `Iterable<OccurrenceId>`, built inside `ledger` from the survivors SQL and read a page at a time (§2). *"never a materialized `List` of what could be a million-plus ids"* stands, and is what §2 keeps. Its Consequence *"`ledger` takes on a light dependency on Spring Batch's reader interfaces"* no longer holds: `ledger` names no Spring Batch type. Its decision that `survivors` is a method of `ledger` and never a view stands whole.
- **Amends**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md), in two phrases. Its Context says ADR-049 *"specifies `schema.sql` per module"*, and its Consequences say *"Every module needs both a `schema.sql` (already required by ADR-049) and a compiled-in expected-version constant"*. There is one `schema.sql`, and each module's tables are marked as its own inside it (§4). The row per module, the check and the refusal stand.
- **Amends**: [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md), whose summary *"Records a known ArchUnit enforcement gap for raw SQL"* is the gap §3 closes: a test now reads which module's SQL names which table. [ADR-049](0049-verdict-rows-and-schema-versioning-without-a-migration-tool.md), only as `docs/architecture.md` §1.5 read it: its summary says *"Schema via `schema.sql` + version check"*, which is one file, and §1.5 said *"`schema.sql` per module"*. §1.5 is corrected, and the summary stands. Both are reconstituted records, so neither file is edited.
- **Amends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §7, in one phrase and for three statements. *"r is its steps a row, pinned in the module that owns the statement, beside its SQL"* and *"It is the one place each r is declared, beside the module's SQL"* stand for every statement but `EmbeddingStatement.CORPUS_METRICS` and `SEED_METRICS`: their r stays declared in `embedding`, which still reports them, and their SQL is `extraction`'s (§3). `SimilarityStatement.NEAR_DUPLICATE_METRICS` is timed and declares no r; its SQL is `extraction`'s too. Every constant, every ratio, every line and the order they are reported in are unchanged.
- **Amends**: [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md) §1, in the type and the class and nothing else. *"`Ledger.survivorsBySize(RunId)` returns an `ItemStreamReader<SizedOccurrence>`"* now reads: `Verdicts.survivorsBySize(RunId)` returns an `Iterable<SizedOccurrence>`. Its statement, its order, its paging by keyset and the plan `SurvivorsBySizeTest` pins are unchanged. The class `SurvivorsBySize` is gone: the paging it held is the one `ledger` now uses for all three long reads (§2).
- **Keeps**: [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) §3's exception to ADR-041, as that record bounded it (§3.3). [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md): a capability module depends on `ledger` alone, and `extraction` also on `corpus`'s two enumerations. [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md) and [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md): one writer, and nothing holds a walk's survivors where a reader did not. [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md): which runs a run's survivors answer to. [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §2: a restart finds its place in the ledger, never in a reader's saved state.
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (which run ids move, §5), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) and [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) (a capability module is handed what it may not ask for), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) (a repeated walk is discarded), a reading of every caller of `Ledger` and every SQL statement in `src/main` at commit `d3b95a5`, and one throwaway probe that made the whole change in a copy of the tree and ran the suite over it (§7). No archive and no working directory was opened for this record (ADR-196).
- **Settles** [#350](https://github.com/algernon28/vespera/issues/350), Wave 4 of the architecture simplification.

## Context

Three things were true of the tree at `d3b95a5`, and none of them was a bug an operator could see.

**`Ledger` did four jobs.** It was one class of 28 public methods: nine about a walk, six about the file occurrences a walk found, six about a run and its steps, and seven about verdicts and the survivors they leave. Thirty-nine shipped classes outside `ledger` named it, and each could reach all 28.

**A Spring Batch type crossed into the capability modules.** `Ledger.survivors`, `survivorsBySize` and `occurrencesOf` each returned an `ItemStreamReader`, as ADR-060 decided. So `ledger`, `corpus`, `extraction`, `similarity` and `embedding` each imported Spring Batch, and each caller opened the reader on an `ExecutionContext` it made for the purpose, read it in a loop and closed it in a `finally`. Three of them, and one helper in `pipeline`, did that only to put every id into a set. `ModuleBoundariesTest` checks one module's references to another, and Spring Batch is a library, so nothing held it out.

**Five SQL statements named a table another module owns**, outside the six ADR-198 allowed:

| Statement | Was in | Table, and its owner |
| --- | --- | --- |
| `DELETE FROM walk_anomaly WHERE walk_id = ?` | `ledger.Ledger.discardWalk` | `walk_anomaly`, `corpus` |
| `SELECT occurrence_id, alphanumeric_char_count FROM extraction_metric WHERE run_id = ? AND occurrence_id IN (…)` | `similarity.RedundancyResolution.loadOccurrenceProfiles` | `extraction_metric`, `extraction` |
| `SELECT occurrence_id, primary_language, mean_score, word_count, page_count, vowelless_word_count, single_character_word_count FROM extraction_metric WHERE run_id = ?` | `embedding.SeedCorpusComparison.metricRows` | `extraction_metric`, `extraction` |
| `SELECT MIN(rowid) FROM extraction_metric WHERE run_id = ?` | `embedding.SeedCorpusComparison.spanOfRun`, handed the table's name | `extraction_metric`, `extraction` |
| `SELECT MAX(rowid) FROM extraction_metric WHERE run_id = ?` | the same | the same |

The ticket counted three. The other two are the span the comparison takes before its read, which a search for `FROM extraction_metric` does not find, because the table's name is an argument. ADR-041 recorded that the boundary test cannot see a statement, and ADR-060 closed the gap for one query.

The six statements of `pipeline.InvocationAccount` that count rows of `file_occurrence` and `run`, `verdict`, `extraction_fault`, `cluster_fault`, `cluster` and `synthesis_doc` are also SQL outside the owner. They are ADR-198 §3's, which allowed them by name and gave its reason. §3.3 says what this record does about them.

## Decision

### 1. `Ledger` holds four records and has no method of its own

`ledger` gains four public classes, called its records here; none is a Java `record`. Each takes a `JdbcTemplate` in a public constructor, carries no Spring annotation, and holds the SQL of its own tables:

| Record | Tables | What `Ledger` did that it now does |
| --- | --- | --- |
| `Walks` | `walk` | `unfinishedWalk`, `startWalk`, `recordProgress`, `finishWalk`, `countsFor`, `walkFinished`, `finishedWalkFor`, `finishedWalkBefore`, `discardWalk` |
| `Occurrences` | `file_occurrence` | `fileOccurrence`, `occurrencesForWalk`, `occurrenceCount`, `factsFor`, `occurrenceId`, `occurrencesOf` |
| `Runs` | `run`, `run_upstream`, `finished_step` | `startRun`, `walkOf`, `stageOf`, `upstreamRuns`, `finishStep`, `stepFinished` |
| `Verdicts` | `verdict` | `verdict`, `discardVerdicts`, `discardVerdictsAgainst`, `extractionFailures`, `survivorCount`, `survivors`, `survivorsBySize` |

That is nine, six, six and seven: the 28. **Every method keeps its name, its parameters and its behaviour**, and all but three keep their return type; the three are §2's. `Ledger.x(…)` is written `ledger.walks().x(…)`, `ledger.occurrences().x(…)`, `ledger.runs().x(…)` or `ledger.verdicts().x(…)`, by the row `x` is in. Where an earlier record names `Ledger.x`, this table says where `x` is.

**`Ledger` survives, and does nothing.** It is still the one bean of the module, built from a `JdbcTemplate`, and its whole public surface is four methods: `walks()`, `occurrences()`, `runs()` and `verdicts()`. It holds no SQL. It is kept for three reasons:

- **The word is CONTEXT.md's.** The ledger is *"the single record of file occurrence identity, verdicts and run identity"*, and a type of that name that hands out exactly those is what the entry describes.
- **No constructor in the tree changes.** Every class that was built with a `Ledger` still is, in `src/main` and in the tests, so the change at each of the 83 calls made on a `Ledger` by 35 classes of `src/main` outside `ledger` is the receiver and nothing else. A reviewer checks a move, not a design.
- **Four beans would be worse at the composition root.** `GenerationTasklet` asks about a walk, an occurrence and a run; it would take three collaborators where it takes one.

It is not a facade. A facade would keep 28 methods that each call another, and a reader of any caller would still not know which job it uses. Here the record is named at every call.

Two things `Ledger` did across tables stay inside `ledger`, each with the record whose question it answers:

- **`Walks.discardWalk` deletes the walk's row and its occurrences**, `file_occurrence` first. Both tables are `ledger`'s. It no longer deletes the walk's anomalies (§3.1).
- **`Verdicts` finds the runs a run's survivors answer to** (ADR-156) by reading `run_upstream` itself, with the statement `Runs.upstreamRuns` uses. It is constructed from a `JdbcTemplate` alone, as the other three are, so a test can build one, or stand a recording one in its place.

The four are not `final`, and `Ledger`'s four methods can be overridden. Three tests stand a recording or a counting ledger in the real one's place, and they do it by handing out one record of their own.

### 2. The three long reads are an `Iterable`, read a page of 1,000 at a time

`Verdicts.survivors(RunId)` and `Occurrences.occurrencesOf(WalkId)` return `Iterable<OccurrenceId>`, and `Verdicts.survivorsBySize(RunId)` returns `Iterable<SizedOccurrence>`. No class of `ledger`, `corpus`, `extraction`, `similarity`, `embedding`, `synthesis` or `profile` names a Spring Batch type, in a signature or in a body. `pipeline` assembles the job and is the one module that does.

**What the type is, and why not a stream.** The ticket offered *"a cursor or a `Stream<OccurrenceId>` its caller closes"*. Neither is taken, because neither is what the readers were. Both readers already paged: each page was one statement that borrowed a connection and gave it back. Nothing was held open between pages, so there was nothing for `close` to release. A stream its caller closes would hold a statement, and so a connection, for the length of the read. Stage 1 writes a verdict for a survivor while it reads the next, and stage 2 writes for a whole chunk while its reader is a chunk and a window ahead, each through the one connection the writer has (ADR-127). An `Iterable` over pages needs no closing, can be gone through twice, which content identity does, and is read with a `for`.

**Each `iterator()` starts at the first page.** A page is one statement of at most 1,000 rows, asked for when the page in hand has been handed out, and it starts after the last row of the one before, by keyset: `id > ?` for the two reads in id order, and ADR-200's `(size_bytes, id) > (?, ?)` with `size_bytes >= ?` for the read by size. The read ends on a page that comes back short. So the most a read holds is 1,000 rows, whatever the walk. The page size is `ledger`'s, as it was.

**What is not the same statement.** `survivors` and `occurrencesOf` were built by Spring Batch's `SqlitePagingQueryProvider`, whose first page carried no `id >` condition and whose later pages carried one. Every page now carries it, the first asking for ids above the least a `long` holds. The rows and their order are the same. `survivorsBySize`'s statement is ADR-200's, unchanged.

**A verdict written during a read.** The ticket asks that every caller be checked for a write ahead of its own cursor, since a page is read ahead of the row in hand. None writes one:

| Caller | What it writes while it reads | Against |
| --- | --- | --- |
| `corpus.BrokenOrOutOfScope` (stage 1, first pass) | `broken`, `out-of-scope` | the survivor in hand |
| `corpus.ContentIdentityResolution` (stage 1, second pass) | `superseded-by` | members of the size in hand, all read (ADR-200) |
| stage 2's step, through `OccurrenceReader`, `UnrecordedOccurrences` and `ConversionDispatch` | `extraction-failed`, `degenerate-output` | the chunk being written, read a chunk and a window before (ADR-176) |
| stage 4a's step | signatures, and no verdict | — |
| seed extraction's step, over `occurrencesOf` | no verdict: a seed earns none (ADR-083) | — |
| `extraction.ConfidenceDistribution`, `similarity.DocumentFrequency`, `embedding.SeedCorpusComparison`, and the embedding, scoring and clustering steps | nothing until the read has ended | — |

And the pages are cut where they were, so a caller that did write ahead would see what it saw before.

**The sets that are still held.** The last row of that table is six places that put every survivor's id into a set, because each tests every row of another table against it. They held that set before, through a reader, and they hold it now. Each builds it where it is used, by going through the `Iterable`; `corpus` has none since ADR-200. The helpers that only wrapped the reader's opening and closing are gone: `ConfidenceDistribution.drainSurvivors`, `DocumentFrequency.drainSurvivors`, `SeedCorpusComparison.drain` and `pipeline.ItemStreamReaders`. With them goes the `IllegalStateException` each wrapped a failed read in; a read that fails now reaches its caller as the template throws it. The statements ADR-193 times around those reads are named and reported as they were.

**What a reader carried, and what became of it.**

- **An open cursor: there was none.** Nothing is held between pages, before or now.
- **`ExecutionContext` state.** `JdbcPagingItemReader` saved how many items it had read under `survivors.read.count`, and would have skipped that many on a restart. Nothing ever restored it: the job runs on `ResourcelessJobRepository`, so each invocation starts with an empty context, and ADR-181 §2 already forbade anything to depend on it. `pipeline.OccurrenceReader` is the adapter a chunk step is handed. It takes an `Iterable<OccurrenceId>`, begins it when the step opens the reader, hands out the next id or `null` at the end, saves nothing on `update`, and lets go on `close`. A closed gate hands the step an `OccurrenceReader` over nothing.
- **Restart.** A resumed stage 2 still reads the survivors less what its committed chunks recorded (ADR-181 §1). `pipeline.UnrecordedOccurrences` is that filter, now an `Iterable` over an `Iterable`.

### 3. A table is named in SQL only by the module that owns it

**`schema.sql` says who owns each table.** The line directly above each `CREATE TABLE` reads `-- owner: <module>`, for all 34 tables: seven of `ledger`'s, four of `corpus`'s, six each of `extraction`'s and `similarity`'s, seven of `embedding`'s and four of `synthesis`'s. The prose comments beside them stand. That line is where the rule is written down, and it is what the test below reads.

#### 3.1 `walk_anomaly` is deleted by `corpus`

`corpus.AnomalyLog` gains `discardForWalk(WalkId)`, which holds the statement `Ledger.discardWalk` held. `WalkRecorder`, which decides that a walk repeated the one before it (ADR-115), calls `anomalyLog.discardForWalk` and then `ledger.walks().discardWalk`, inside the one transaction it already opened for the discard. The order matters and is new: an anomaly refers to its walk, so with foreign keys on, the walk's row cannot go while an anomaly stands.

#### 3.2 `extraction_metric` is read by `extraction`, and handed to the two modules that need it

`similarity` and `embedding` may not name `extraction` (ADR-040). So each declares what it needs as an interface of its own, `pipeline` hands it an implementation, and the implementation is `extraction`'s. This is ADR-110's arrangement, and the callback form ADR-190 gave it.

- **`extraction.ExtractionMetrics`** gains `alphanumericCharCounts(RunId, Collection<OccurrenceId>)`, which answers a `Map<OccurrenceId, Long>` holding only the occurrences that have a row, and `eachMeasuredForm(RunId, LongConsumer, MeasuredFormRow)`, which gives each row of a run to a callback as it is read and gives SQLite's steps to the consumer (ADR-193). `metricRowsUpTo(RunId)`, the span of a run's rows, becomes public. The three hold the four statements over `extraction_metric` in the Context's table, word for word.
- **`similarity.AlphanumericCounts`** is one method, `recordedUnder(RunId, Collection<OccurrenceId>)`. `RedundancyResolution.resolve` takes one after the boilerplate hashes, in both its forms. `pipeline` passes `extractionMetrics::alphanumericCharCounts`.
- **`embedding.MeasuredForms`** is two methods, `rowsUpTo(RunId)` and `each(RunId, LongConsumer, Row)`. `SeedCorpusComparison.measure` takes one after the seed walk, in both its forms. `pipeline` passes the two methods of `extraction` above.

**Nothing an operator reads moves.** The comparison still keeps only the rows of its candidates, as it reads them, so it holds no more than it did. Each module still reports its statements itself, at the point it reported them: `NEAR_DUPLICATE_METRICS` timed around the hand-over, `CORPUS_METRICS` and `SEED_METRICS` counted through it, each started with the span and given its steps. The lines, their order and their wording are those of ADR-204.

#### 3.3 The invocation account's six counts stay where ADR-198 put them

ADR-198 §3 made *"one exception, bounded three ways"*: the reads are counts and nothing else, each is grouped by a closed column or by none, and they are made in `InvocationAccount` and nowhere else. It refused to move them, because *"that would move run ids of stage 2 and cost a replay of stage 2"*.

That cost is nil in the commit that carries this record, which moves every run id (§5). The exception is kept all the same. Ending it reverses a decision another record made on purpose, and that is not this record's to do in passing. What this record adds is that the exception is now held to its bounds by a test, where it was held by a sentence.

#### 3.4 The test that holds the rule

`EachTableIsNamedOnlyByItsOwnerTest` reads the owner lines out of `schema.sql`, and reads every string each shipped class holds out of its compiled form, where a statement written as joined literals is one text and a comment is absent. A class names a table where a text has the table after `FROM`, `JOIN`, `INTO`, `UPDATE`, `TABLE` or `ON`, or is the table's name and nothing else, which is how the comparison's span carried it. It fails when a class names a table its module does not own. `InvocationAccount` is excepted for ADR-198's seven tables, and only in a statement that begins with `SELECT` and carries `COUNT(*)`.

**What it cannot see**: a table's name assembled from parts at run time, and a statement that reaches a table through a view or a trigger. The schema has neither.

`ModuleBoundariesTest`'s javadoc said that a statement reaching into another module's table *"stays a matter for review by eye"*. It now names this test, and `OnlyPipelineNamesSpringBatchTest` for the library type it cannot see either.

### 4. One schema file, and six schema-version classes

Two things the ticket proposed are refused.

**The schema stays one file.** Splitting it into six would make ADR-059's phrase true and buy nothing else. `StartUpIndexAnnouncement` reads the one file to say which index it builds (ADR-187), six test classes apply it as one script, and the tables' foreign keys fix an order across modules that six files would have to be listed in. Ownership needed to be readable by a test, and a line above each table does that. So ADR-059's two phrases and `docs/architecture.md` §1.5 are corrected instead.

**`LedgerSchema`, `CorpusSchema`, `ExtractionSchema`, `SimilaritySchema`, `EmbeddingSchema` and `SynthesisSchema` stay.** They are one declaration per module already. Each is the module's name, its number and one call to `SchemaVersionGuard.require`, made in a constructor so that a mismatch fails while the context is built (ADR-059). Each must be a bean, to be built at all, and must live in its module: a version bumped in `corpus` moves `corpus`'s implementation version with the tables it describes (ADR-058), and a list of six numbers kept in `ledger` would move no stage. What repeats is the javadoc saying when to bump, which is the part worth having in each. `SchemaVersionDeclarationTest` is unchanged.

### 5. Which run ids move

**Every run id from stage 1 to 6b, once**, on the first build that ships this. Stage 0 mints no run. `StageModules` names the modules each stage's implementation version spans (ADR-058):

| Stage | Modules named | What this change touches in them |
| --- | --- | --- |
| 1, byte-level reduction | `corpus` | `BrokenOrOutOfScope`, `ContentIdentityResolution`, `WalkRecorder`, `AnomalyLog` |
| 2, extraction | `extraction`, `similarity` | `ExtractionMetrics`, `ConfidenceDistribution`, `ExtractionFaultResolution`; `RedundancyResolution`, `DocumentFrequency` |
| 3 and 4 | `similarity`, `extraction`, `pipeline` | the same, and `pipeline` |
| 5, both runs | `embedding`, `extraction`, `pipeline` | `SeedCorpusComparison`, and the same |
| 6a and 6b | `synthesis`, `extraction`, `embedding`, `pipeline` | the same; nothing in `synthesis` |

So each stage moves through its own modules, and would move through its upstream run in any case (ADR-048). `ledger` is in no stage's list: the split of §1, on its own, moves nothing. It is the callers that change.

**What does not move.** No DDL changes: the owner lines are comments, so no table, column or index differs and no module's schema version moves. An existing working directory opens. No cache key changes, so stage 2's conversions and stage 5's vectors are found in their caches under the new runs. No `run.stage` value and no `finished_step.step` value changes. `StageModules` is not edited, so `RunIdentityGoldenTest` is not edited: its module lists and its `config_consumed` text are as they were. The verdicts written under the old run ids stay recorded and remove nothing (ADR-156). An arrangement is minted again, and `arrangementApproved` must name the new one.

### 6. One landing

The three parts could each be a commit. They are one, because each alone touches `corpus` and so moves every run id from stage 1 down: three landings would mint three sets of runs for one that is used. The risk of one is the same, since each part is held by the suite that was green before it.

## Alternatives refused

- **`Ledger` deleted, and four beans injected where one was.** It narrows what each class can reach. It changes the constructor of every class that takes a `Ledger`, and every test that builds one, to buy that, and a class that asks about a walk, an occurrence and a run takes three collaborators for one.
- **A `Stream` or a closeable cursor for the three reads.** §2.
- **The two reads of `extraction_metric` made by `pipeline` first, and their rows handed over as lists.** The resolution learns which occurrences it needs only once its components are found, and the comparison would be handed every row of a run where it keeps its candidates'. Both would also report their statements in a different order than ADR-204 lists.
- **Moving `CORPUS_METRICS`, `SEED_METRICS` and `NEAR_DUPLICATE_METRICS` into `ExtractionStatement`.** The statement would be declared where its SQL is, as ADR-193 §7 says. But the module that reports a statement would then not be the one that waits on it, and one reported order would be split across two progress interfaces. The phrase is amended for three statements instead.
- **Ownership held in the test as a list of 34 names.** The test would then be the only place the rule is written, and a new table would have an owner only once somebody edited a test.
- **Six schema files, and one class for six versions.** §4.

## Consequences

- **A class that reads the ledger says which part.** `ledger.verdicts().survivors(run)` and `ledger.walks().finishedWalkFor(root)` cannot be confused, and a record's tests are about one table.
- **A module that reads survivors needs no Spring Batch**, and its loop is a `for`.
- **A statement that names another module's table fails the build**, where before it passed review or did not. ADR-041's gap is closed, less the one exception ADR-198 made and the two forms §3.4 names.
- **A failed read of the survivors is no longer wrapped.** Nothing read the wrapping message.
- **Every run from stage 1 down is minted again**, with its caches warm (§5).
- **A test in `similarity` or `embedding` that builds the resolution or the comparison by hand** hands it `extraction`'s read itself, through `RecordedAlphanumericCounts` and `RecordedForms` in the test tree.

## Tests

The proof of a move is the suite that was green before it. In the probe of §7 all 1,377 tests that existed passed over the moved code, none skipped.

**Existing tests edited because a name they use moved, and for no other reason**: 67 files. In 55 the only edit is the receiver, `ledger.x(` to `ledger.<record>().x(`. Twelve carry more:

- `ledger.SurvivorsTest` and `ledger.SurvivorsBySizeTest` go through an `Iterable` where they opened and closed a reader. Every claim stands, the three pages and the plan included.
- `corpus.StageOneHoldsBoundedMemoryTest` counts what the read has handed out by standing a counting `Verdicts` in the ledger's place. A read was *open* between `open` and `close`; it is open from the moment it is begun until it says nothing is left. The claims are the same.
- `corpus.ContentIdentityResolutionCountsItsSurvivorsFirstTest` records what is asked of the ledger through a recording `Verdicts` and `Occurrences`.
- `pipeline.StepCompletionOrderProbe` watches `finishStep` through a probing `Runs`.
- `pipeline.SurvivalOverAReusedWalkTest` asserts over the survivors themselves, where it emptied them into a set through `ItemStreamReaders`.
- `ledger.DiscardingAWalkSearchesEveryReferenceTest` says the anomalies' delete goes with the discard, where it said it was the discard's own.
- `similarity.RedundancyResolutionTest`, `RedundancyResolutionReportsItsCountsTest` and `SimilarityStatementProgressOrderTest`, and `embedding.EmbeddingStatementProgressOrderTest` and `SeedCorpusComparisonTest`, hand the resolution and the comparison their source of metrics, from `RecordedAlphanumericCounts` and `RecordedForms`, two fixtures new to the test tree.

**New**, 14 tests in six classes:

| Class | What it holds |
| --- | --- |
| `ledger.LedgerHoldsFourRecordsTest` | `Ledger`'s public methods are the four and no fifth; each of the 28 is on its record and on no other; the three long reads return `Iterable` |
| `ledger.ReadsOfTheLedgerHoldOnePageTest` | for each of the three reads over 2,500 rows: no statement before the first row is asked for; one, two, then three statements issued as each page of 1,000 begins; three in all; none bringing back more than 1,000 rows; and a second pass starting again from the first row |
| `OnlyPipelineNamesSpringBatchTest` | no shipped class outside `pipeline` holds the name of a Spring Batch type; `pipeline.ItemStreamReaders` is not among the shipped classes |
| `EachTableIsNamedOnlyByItsOwnerTest` | §3.4 |
| `corpus.ADiscardedWalksAnomaliesAreCorpusToDeleteTest` | `discardForWalk` removes that walk's anomalies and no other's; and the ledger refuses to remove a walk whose anomaly still stands, which it could not if it still deleted them |
| `extraction.MetricsReadForAnotherModuleTest` | the counts are those of the run and the occurrences named; every row of a run is handed over, a missing score and a missing page count told apart from zero; the span |

`RunIdentityGoldenTest`, `SchemaVersionDeclarationTest`, `OperatorTextTest` and `InvocationAccountTest` are not edited. `ModuleBoundariesTest` is edited in one sentence of its javadoc (§3.4) and in no claim, and it gains no allowed dependency.

## 7. What was measured

A throwaway probe, outside the repository, made the whole change in a copy of the tree at `d3b95a5`: the four records, the paged `Iterable`, the adapter, the five statements moved and the owner lines. Over it:

- the 1,377 existing tests passed, none skipped, with the 67 files edited as above;
- the 14 new tests passed. Before the probe removed `ItemStreamReaders`, the one that says it is gone failed, as it should.

And over the tree at `d3b95a5` with nothing moved:

- `OnlyPipelineNamesSpringBatchTest` named seven classes in five modules: `corpus.BrokenOrOutOfScope` and `ContentIdentityResolution`, `embedding.SeedCorpusComparison`, `extraction.ConfidenceDistribution`, `ledger.Ledger` and `SurvivorsBySize`, and `similarity.DocumentFrequency`;
- `EachTableIsNamedOnlyByItsOwnerTest` failed on its first claim, the schema naming no owner. Given the owner lines and nothing else, it named exactly the three classes of the Context's table, and no column, header or sentence that shares a table's name.

It did not measure a real walk. How long a page of `survivors` takes on the archive's database is what it was before, since the statement is the provider's with one condition more on its first page, and that is an argument and not a measurement.

**The size of the change is not stated here.** The plan's *"about −300 lines"* was a guess, and the commit's own diff is the measure.

## What this does not decide

- **Whether ADR-198's exception should end.** It could, at no cost in runs, only in a commit that moves every run id for another reason, as this one does. It is the operator's to say (§3.3).
- **A file that cannot be read when stage 1 or 2 hashes it** ([#452](https://github.com/algernon28/vespera/issues/452)). `ContentIdentityResolution` and stage 2's reader change here in how they are fed and in nothing they decide.
- **The repeated `discardForRun` methods**, and **moving the walk half of the ledger into `corpus`**: the ticket rules both out, and ADR-041 keeps occurrence identity in `ledger`.
- **The `IN` list of `alphanumericCharCounts`**, which is as long as the components' members and is not cut into batches. It was not before.
- **Splitting `Deliverable`**, which is Wave 5.
