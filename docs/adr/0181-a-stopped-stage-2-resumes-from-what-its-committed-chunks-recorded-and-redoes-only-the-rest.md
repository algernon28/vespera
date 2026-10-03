# ADR-181 — A stopped stage 2 resumes from what its committed chunks recorded, and redoes only the rest

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) — **its run half's discard rule, for the `extraction` step only.** *"A run that exists and is unfinished: the step discards its own rows under that id and does the work again"* no longer holds for stage 2. Its walk half, its mint-or-continue `startRun`, its finished-step skip, its `verdict` uniqueness and its 6b exception are untouched, and so is the discard rule for every other step.
- **Amends**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) — **its second re-keyed rule, for the `extraction` step only.** *"A step whose completion is not recorded discards its own rows under that run and does the work again"* becomes, for that step: it keeps every row its committed chunks wrote, discards the rows its own end of step wrote, and does what no committed chunk recorded. Its first rule, its third rule (completion written on success only), `finished_step` and its safety condition all stand, and the safety condition is the reason this record is safe at all (§3).
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §2 — **"the reader's discard gains `extraction_fault`"**, for what the discard now covers. The reader no longer discards a whole stage-2 run. It deletes the run's fault rows and the verdicts that resolved them, and keeps what the chunks wrote (§1). ADR-139 §2's rationale, *"a re-run discards this run's rows and does the work again … and the refusal is deterministic, so it is faulted again"*, still holds through §1: every faulted occurrence is read again, so it is faulted again and recorded again. ADR-139's fault rows, its resolution on completion, its choice of the reader over the recorder's `beforeStep`, and its listener order (§4) are untouched.
- **Amends**: [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md) §2, first bullet, for the `extraction` step. *"The next invocation discards its unfinished rows under the same run id and redoes them"* no longer describes stage 2: the next invocation keeps what its committed chunks recorded, and the occurrences of a chunk a power cut lost are unrecorded, so they are read again. ADR-180's header line *"How an interrupted stage resumes does not change"* is no longer true of stage 2 either. ADR-180's conclusion stands, and this record relies on it: a power cut leaves the database in a state the resume handles (see "Relation to ADR-180 and #369").
- **Rests on**: [ADR-036](0036-spring-batch-with-resourcelessjobrepository-camel-dropped.md) (the job repository holds nothing past the JVM), [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability owns its own tables, so the queries this needs are `extraction`'s), [ADR-048](0048-walk-and-run-identity.md) and [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (what a run id folds in), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) (the two streaks), [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) (never a rewrite of another run's rows), [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) (the chunk is the read-ahead, and the drain is single-threaded), [ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md) (the confidence-distribution report), [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) (stage progress over a known denominator).
- **Settles** [#379](https://github.com/algernon28/vespera/issues/379).

**One word here is Spring Batch's, not the project's.** A *chunk* is Spring Batch's commit interval: the 16 occurrences stage 2 reads, processes and writes in one transaction. It is not the piece of a document's text whose boundaries the **Chunk cache** stores. This record uses the word only in Spring Batch's sense.

## Context

### What happened

On the whole-archive run of 2026-10-02/03, stage 2 had reached 7,280 of 10,406 survivors (70%) when the invocation had to be restarted at 22:57. The next invocation, at 09:09, began stage 2 from the first survivor again. No file went back to Docling, because the conversions are in the extraction cache, keyed outside the run (ADR-070). Everything stage 2 had written per occurrence was deleted and rebuilt: shingles, extraction metrics, verdicts and faults. Reaching 70% again took about 80 minutes, most of it spent inside SQLite, in each chunk's transaction. [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md) measured where that time goes: on the scattered index pages `shingle_by_hash` makes each chunk change, not on the journal or the commit (its §6, carried by [#381](https://github.com/algernon28/vespera/issues/381)).

That is ADR-115/ADR-116 working as written. A step whose completion is not recorded discards its own rows under its run and redoes the step, so that a resume can never mix two versions of a stage. On this run the run id, the code and the settings were all the same between the two invocations. Stage 2 paid for the whole replay and gained nothing from it.

### What stage 2 writes, and in which transaction

Read off the code at `80d5277`, against `spring-batch-core` 6.0.5's `ChunkOrientedStep`.

**One chunk is one transaction.** `doExecute` wraps each chunk in `transactionTemplate.executeWithoutResult`. Inside it, `processChunkSequentially` reads the chunk (`CHUNK_SIZE` = 16 calls to `ConversionDispatch.read`), processes every item, and writes the processed chunk. The transaction commits only after the writer returns. A skippable exception from the processor does not roll the chunk back: `processItem` catches it, `doSkipInProcess` drops the item, and the chunk goes on. Any other exception reaches `processChunkSequentially`'s `catch`, which marks the transaction rollback-only and fails the step. That includes a non-skippable one and the `ExtractorStoppedAnsweringException` ADR-071's breaker throws from its skip listener. So the chunk the step fails in leaves nothing behind, including the occurrences of that chunk that were processed cleanly before the failure.

**Written inside the chunk transaction, on the step thread:**

| Table | Written by | When |
|---|---|---|
| `extraction_metric` | `ExtractionItemProcessor`, through `ExtractionMetrics.write` / `writeAndJudge` | every converted occurrence, every document-scope failure, every Docling-reported timeout below the streak |
| `shingle` | `ExtractionItemProcessor`, through `Shingler.write` (JDBC batches since #367, on the same connection) | every converted occurrence, after its metric row |
| `verdict` (`EXTRACTION_FAILED`, `DEGENERATE_OUTPUT`) | `ExtractionItemWriter` | a document-scope failure, a timeout below the streak, an occurrence with no detected format (ADR-100), a converted occurrence the degeneracy floor removes |
| `extraction_cache` | `PendingConversions.take` → `DoclingExtractor.remember` | every answered call that was not a cache hit. Keyed by content hash and extractor identity, not by the run |

**Written outside any chunk:**

| Table | Written by | When |
|---|---|---|
| `run`, `run_upstream` | `StageRuns.extraction()` → `Ledger.startRun` | when the reader is built, before the first chunk. Mint-or-continue (ADR-115) |
| deletes of the four stage-2 tables | `ExtractionJobConfiguration.extractionReader` | when the reader is built, before the first chunk, and only if the step is not finished |
| `extraction_fault`, and the `EXTRACTION_FAILED` verdicts that resolve them | `ExtractionFaultRecorder.afterStep`, in one transaction of its own | after the last chunk. The verdicts only when the step completed (ADR-139) |
| `finished_step` | `RunCompletion.afterStep` | after the fault recorder, and only when the step completed (ADR-116, ADR-139 §4) |

`ExtractionHealthCheckListener`, `ExtractionCircuitBreaker` and `ExtractionTimeoutStreak` write nothing. They log, and they keep counters in memory.

**So every occurrence the processor finished is all there or not there at all.** Its metric row, its shingles and its verdict commit together or not at all. An occurrence the converter blamed the failure on itself for, which becomes an extraction fault, leaves none of stage 2's own rows in the chunk: only its extraction-cache row, which is keyed outside the run. Its record is a `PendingFault` held in the recorder's memory until `afterStep`. If the JVM dies first, that record is gone. If the step fails, the record is written as a fault row and no verdict is written (ADR-139 §3).

### How the restart could find where to continue

**Spring Batch's saved position.** A restartable job resumes a reader from the `ExecutionContext` the job repository saved at the last commit. Here that context does not outlive the JVM, because the repository is resourceless (ADR-036): there are no batch metadata tables, and every invocation launches a fresh job execution with an empty context. Using it would mean adding the metadata tables and making the job restartable. That reverses ADR-036 to recover something the ledger already records. And [#369](https://github.com/algernon28/vespera/issues/369)'s plan lets `ConversionDispatch` read up to `LOOKAHEAD` occurrences past the chunk being processed. Its delegate's saved position would then name occurrences that were dispatched and never committed, and a restart from that position would skip them. #369 rests its own safety on *"nothing depends on the reader's saved position"*.

**The ledger.** The rows a committed chunk wrote are committed in the same transaction as the work. Asking the ledger which occurrences are recorded under this run therefore gives the right answer after any stop: an exception, Ctrl-C, a killed process, or power lost mid-commit. Nothing has to be written for the answer to exist. This is how ADR-111 resumes 6b, which skips every cluster carrying a `synthesis_doc` row.

## Decision

### 1. A committed chunk is final within its run, and stage 2 resumes after it

When the `extraction` step starts under a run whose completion is not recorded, it **keeps** what earlier invocations' committed chunks recorded under that run, and reads only the occurrences none of them recorded.

**An occurrence is recorded under a stage-2 run** when it carries an `extraction_metric` row or an `EXTRACTION_FAILED`/`DEGENERATE_OUTPUT` verdict under that run, and carries no `extraction_fault` row under it. Every outcome the processor can reach leaves one of the first two in the chunk transaction. The third marks the outcomes it cannot reach: those are decided only at the end of the step.

Concretely, at the point where `extractionReader` discards today, and only when `stepFinished` is false:

- **Deleted:** first the `EXTRACTION_FAILED` verdicts under the run against occurrences that carry an `extraction_fault` row under the run, then those fault rows. These rows are the end-of-step's work (ADR-139), not a chunk's. Every faulted occurrence is read again, so this invocation's end of step re-records every fault of the stage, and resolves every one of them if it completes. The verdicts go first because the fault rows are how they are found. A fault row is never found beside a processor-written verdict for the same occurrence and run: the fault row is deleted before that occurrence is read again. The verdicts are there to delete when the fault recorder's transaction committed and `RunCompletion`'s did not: a stop between the two, or a power cut that lost only the later commit (ADR-180 §2). `verdict` has no unique key, so leaving them would resolve the same fault twice. A faulted occurrence from a committed chunk is judged again from the extraction-cache row that chunk committed, not converted again; only a fault from a chunk that rolled back or was killed reaches the sidecar again. Today's discard-and-redo behaves the same, because the cache is keyed outside the run.
- **Kept:** every `extraction_metric` row, every `shingle` row, and every verdict the processor's chunks wrote under the run.
- **Read:** the run's survivors (ADR-060, ADR-156), less every occurrence carrying an `extraction_metric` row under the run. Occurrences carrying a stage-2 verdict under the run are already left out by the survivors query, because both kinds are blocking and a run is in its own scope. One filter is therefore enough, and it reads the table that is the marker for most of the corpus.
- **Said:** when anything was kept, one INFO line at the start of the step names the run, how many occurrences were already recorded, and how many faulted occurrences are read again. The progress line's denominator (ADR-093) is what this invocation reads, not the whole survivor set.

A run with nothing under it (the first invocation of a new run id) reads exactly what it reads today, since the filter removes nothing. The discard is still made where the reader is built, outside the chunk transaction, for the reason `extractionReader`'s comment gives: a delete there cannot be rolled back.

### 2. The restart finds its place in the ledger, never in the reader's saved position

For the reasons in Context. The ledger answer is committed with the work it describes, so it cannot be wrong about it. It needs no batch metadata, so ADR-036 stands. And it does not care where the reader had got to, so #369's read-ahead window may run as far ahead of the last commit as it likes. **`ConversionDispatch.update` and the survivors reader's own saved state stay meaningless, as they are today**, and nothing may come to depend on them. If a later change wants to restart from a saved position, that needs a record of its own amending this one.

### 3. What still forces the whole stage to be redone: a different run id, and nothing else

A run id is stage 2's identity: the stage-1 run it read, the walk, the extractor identity (sidecar versions, image, sent options; ADR-090, ADR-147), the degeneracy floor, and the implementation version of `extraction` and `similarity` (ADR-058). A change to any of these mints another id. That run has nothing recorded under it, so it reads every survivor. The stopped run's rows stay where they are, and nothing reads them unless that id is arrived at again (ADR-077, ADR-156).

ADR-116 already states the condition this rests on: recording a step's work is safe only where the run id names everything the step consumes. Resuming asks for nothing more. A same-id resume finishes the stage under the inputs that started it, so each occurrence it records is recorded as an uninterrupted invocation would have recorded it, with one exception §4 states: ADR-071's two counters start again at zero. So where a run of consecutive timeouts spans the restart, the resumed stage can record a document-scope verdict for an occurrence on which an uninterrupted invocation would have reached the streak's limit and stopped the step instead. Nothing else an occurrence's record depends on is carried from one occurrence to the next.

Nothing in the ledger needs to force a redo as well:

- **A half-written occurrence** cannot exist, because of the transaction table above.
- **A run left by code before this record** has the same row shape: chunks committed the same way, and faults written only at the end of the step. A same-id resume over it is just as sound. In practice none is resumed, because the change that implements this record mints another id (Consequences).
- **A schema move** in `extraction` or `similarity` deletes the module's tables (ADR-059), so there is nothing to resume.
- **A stage-1 or walk change** is a different id.

One gap in the id is real and is **not** closed here. It is under "What this does not decide".

### 4. ADR-071's breaker and both streaks start from zero on a resumed step, and that is harmless

Both counters are step-scoped and held in memory, so a new invocation starts them at zero. Today's discard-and-redo does the same, so this record changes nothing about them. They are also not owed any history from the stopped invocation:

- **The service-scope breaker** stops a step that keeps meeting a sidecar that does not answer. On a resume, the health check has just passed (`beforeStep`), which is better evidence about the sidecar than a streak carried over from before the restart. If the sidecar is still failing, five fresh consecutive service-scope failures trip the breaker, as on any invocation.
- **The timeout streak** is defined over consecutive occurrences on the drain (ADR-140 §2). A process restart breaks the drain, so nothing on either side of it is consecutive with anything on the other. The timeouts that were committed as document-scope verdicts before the stop stay verdicts, exactly as they would if the sidecar had recovered within one invocation. ADR-071 already accepts that reading for the first two timeouts of any streak.
- **The faulted occurrences** a stopped invocation set aside are read again (§1). The breaker meets them again, in read order, mostly as the cached answers their chunks committed (Consequences), as it would have done in an uninterrupted invocation.

### 5. Whatever describes the whole stage reads the ledger under the run

Stage 2 writes no report of its own. Stage 3 measures the confidence distribution (ADR-075) from `extraction_metric` under stage 2's run, restricted to that run's survivors, and counts refused conversions from `extraction_fault` under the same run. Both are ledger reads, so after a resume they cover every occurrence of the stage, the ones an earlier invocation recorded included. The fault resolution in `afterStep` covers the whole stage too, because every fault is re-encountered in the invocation that completes it (§1).

This is a rule, not only an observation. **Any report about stage 2's outcome is computed from the ledger under the stage-2 run, never from a step execution's in-memory counts.** That includes `extraction-failures.html` if [#326](https://github.com/algernon28/vespera/issues/326) adds it. The step's closing line keeps Spring Batch's read/written/skipped/filtered counts, because those describe this invocation's execution (ADR-093), and the starting line in §1 says how much an earlier invocation had already recorded.

### 6. Stages 3 to 6b: the same rule where the work is per occurrence, and not where it is computed over the whole set

The rule generalises to a step only when both of these hold:

- (a) it writes per-occurrence rows that commit with their own marker in one transaction;
- (b) the result over the whole set is the union of the per-occurrence results.

- **Candidates for a later ticket:** `redundancy-signature` (stage 4a, a chunk step writing one `minhash_signature` per occurrence), and `seed-extraction` (a chunk step, though ADR-155's rule that it records no completion while any seed will not open would need to be read against it). `relevance-scoring` is a candidate only if its writes turn out to commit per occurrence; that has to be read off the code, not assumed.
- **Keep discard-and-redo:** `content-census` (document frequency is a count over the whole set), `content-redundancy` (resolution compares across the set), `seed-corpus-comparison` (one read over the whole seed set and the whole survivor set, so (b) fails), `relevance-floor`, `relevance-report`, `clustering` and `arrangement`. A partial result there is not part of the final one, so keeping it would be wrong rather than cheap.
- **Already keeps what it wrote:** `embedding-scoring` discards nothing (ADR-157 §5), because a vector is keyed by content and instrument, outside the run (ADR-085), so a re-run keeps every vector already stored. It writes no per-occurrence row under the run, so (a) fails and there is no marker to resume from. What a re-run still pays is one Ollama call per chunk, because `ChunkEmbedder` needs the returned vector's length to compose the identity it looks the vector up by. Whether that is worth a marker is a cost question for its own ticket, not this rule.
- **6b** already resumes per cluster (ADR-111) and is unchanged.

This record decides stage 2 only. Each other step needs its own ticket, which reads its transaction boundaries the way §Context does here.

## Relation to ADR-180 and #369

**This does not depend on [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md) (write-ahead logging with `synchronous=NORMAL`), and ADR-180 does not depend on it, but the resume needs two properties of whichever journal mode is in force, and ADR-180's mode has both.**

- **A chunk is atomic.** SQLite gives that in either journal mode: an uncommitted chunk is rolled back on the next open, from a hot rollback journal or by ignoring uncommitted WAL frames. So no occurrence is half-written, and an occurrence is recorded exactly when its chunk committed.
- **What a crash loses is a suffix of whole commits.** Under ADR-180's mode a process that dies loses nothing, and a power cut or an operating-system crash loses at most the commits since the last WAL checkpoint, each one whole (ADR-180 §2). Because the commits are lost from the end, what survives is still a prefix of the invocation's work, so the ledger never shows a later chunk as recorded while an earlier one is not, and never shows `finished_step` without the fault recorder's transaction before it. A lost chunk leaves its occurrences unrecorded, and the resume reads them again. A lost `finished_step` with a surviving fault-recorder transaction leaves a run whose faults are resolved and whose step is not finished, and §1's deletion of the resolving verdicts is what makes that state safe to resume.

The two are complementary: ADR-180 makes the small commits of every other stage cheaper, and this record means fewer of stage 2's commits are done twice.

**[#369](https://github.com/algernon28/vespera/issues/369) is compatible with this, and its premise becomes a decision rather than an observation.** Its plan says *"a stage-2 run that did not finish is discarded and redone as a whole … reading ahead of what was committed therefore changes no restart behaviour"*. The first half stops being true with this record, and the conclusion still holds for a stronger reason: the restart asks the ledger, so occurrences that were read ahead and never committed are simply unrecorded and are read again. Whoever implements #369 should cite this record in place of ADR-115's discard for that point. Its instruction to grep `ExecutionContext` under `src/main` remains a good check: at `80d5277` nothing restarts a step from one.

## Consequences

**A restart costs only what was not committed.** On the run in Context, the next invocation would have read the 3,126 occurrences left rather than all 10,406, plus the at most 16 of the chunk that was open and any occurrences faulted earlier. The 80 minutes of replay is gone. The conversions of the chunk that was open are not saved: `DoclingExtractor.remember` writes the extraction cache inside the chunk transaction (the first table above), so a chunk that rolled back or was killed leaves none of its conversions cached, and those at most 16 go back to Docling. A faulted occurrence from a committed chunk is answered from the cache row that chunk committed.

**Implementing this costs one full replay of stage 2, and of every stage after it, on the first invocation of the build that ships it.** ADR-041 keeps each capability's tables its own, so the reads §1 needs are `extraction`'s to provide, as query methods beside the writes it already owns:

- **`ExtractionMetrics`**: the occurrences carrying a metric row under a run.
- **`ExtractionFaults`**: the occurrences carrying a fault row under a run. Today it has only `write`, `discardForRun` and `countForRun`.

No SQL against `extraction_metric` or `extraction_fault` may be written in `pipeline` or `ledger`. Deleting the resolving verdicts for a list of occurrences is a `Ledger` method, because `verdict` is the ledger's table. Adding the two queries is a commit under `src/main/java/io/algernon/vespera/extraction`, so it moves `extraction`'s implementation version (ADR-058, `.mvn/scripts/implementation-versions.groovy`), and with it every stage-2 run id. Every existing working directory therefore meets a stage-2 run with nothing under it, and replays stage 2 once over cached conversions, then every later stage, whose run ids name stage 2's. This is accepted: it is paid once, and every later restart is cheaper by it. **Operator advice:** a whole-archive run with stage 2 in progress should be let finish stage 2 on the build it started on before the build that ships this is installed. Installed mid-stage, the new build starts stage 2 again from the first survivor, under its own run, and nothing the old run committed is kept.

**`Shingler.discardForRun` loses its only caller.** `ExtractionMetrics.discardForRun` keeps one in `SeedExtractionItemWriter`, under the seed measurement run, which this record does not touch. `ExtractionFaults.discardForRun` keeps its caller, since every fault row under the run is deleted. `Ledger.discardVerdicts` keeps its other callers, and stage 2 stops calling it.

**Comments that state the old rule must change with the code**: `ExtractionJobConfiguration.extractionReader`'s comment, the javadoc on `extractionConversionDispatch`, the comment in `ExtractionItemProcessor`'s constructor, and `ConversionDispatch`'s class javadoc where it says the delegate decides "the already-finished check and the discard". ADR-077 was written to stop a comment contradicting the method beneath it.

**`extractionReader`'s comment also says a skip rolls the chunk back.** Under `spring-batch-core` 6.0.5 a skip in processing does not. A non-skippable failure or a tripped breaker does, and one of those in the first chunk is still reason enough to discard outside the chunk transaction. The comment should say which.

**`CONTEXT.md` needs no new term.** *Run* already says it is *"continued when work resumes"*. *Verdict* already says a verdict is deleted only *"by the step that wrote it, redoing its own unfinished work under the same run"*, which still describes the one deletion stage 2 keeps: the end-of-step verdicts that resolved a fault. *Checkpoint* stays the walk's word, and nothing here uses it.

**The tests that pin this** are `ExtractionResumeInvocationTest`. The first four stop stage 2 after some chunks have committed and invoke again over the same corpus; the fifth stops nothing.

1. A resumed stage asks the converter only about occurrences no committed chunk recorded. Its metric, shingle, verdict and fault counts equal an uninterrupted run over an identical corpus. Before resuming, it claims that the fault and a committed removal of each kind were reached before the stop, so an order that puts them after it fails there.
2. The stop leaves a whole number of chunks, with no occurrence half-written, and the chunk it stopped in is done whole on resume. Only the conversion count discriminates; the atomicity claims are guards that pass under the old rule too.
3. A different run id over the same walk, made by a commit to `extraction`, reads every occurrence again and leaves the stopped run's rows as they were. It passes under the old rule too, and pins that the read filter asks under the run.
4. The confidence distribution counts every survivor of the stage, the ones the first invocation recorded included. Only the conversion count discriminates; the total is a guard.
5. A stage whose fault recorder committed and whose completion record was lost asks the converter only about the faulted occurrence, and ends with exactly one `EXTRACTION_FAILED` verdict and one fault row for it.

The scripted converter places its outcomes by the order documents are first asked about, pinned to their content, not by name, because the walk records a folder in the order the file system lists it.

## What this does not decide

**Whether `pipeline`'s stage-2 judging code is versioned into stage 2's run id.** `StageModules.EXTRACTION` spans `extraction` and `similarity`. Part of the code that decides stage 2's verdicts lives in `pipeline`: `ExtractionItemProcessor`'s scope reading, ADR-071's timeout streak, the extractor identity's composition, `ExtractionFaultRecorder`'s resolution and `ConversionDispatch`. A change confined to those classes therefore keeps the stage-2 run id. That gap is older than this record. ADR-115's finished-step skip already keeps a finished stage 2 across such a change. Resuming widens it: a half-done stage 2 can now be finished by different judging code under the same id, where until now it was redone whole.

**It is not closed by adding `pipeline` to stage 2's module list.** `pipeline` is the composition root, and nearly every commit touches it. Naming it would move the stage-2 run id on every such commit, so stage 2, and every stage after it, would replay on nearly every build, not once. It is closed by moving the judging code into `extraction`, which stage 2's id already names, with every `StageModules` list unchanged. That is [#320](https://github.com/algernon28/vespera/issues/320) (Wave 2, "stage 2's classification and identity to extraction"), which moves ADR-070's classification, ADR-071's timeout streak and the extractor identity's composition. **`ExtractionFaultRecorder`'s resolution and `ConversionDispatch` are not in #320's table, and they belong with it**, since each decides what stage 2 records.

**Whether a service-scope answer belongs in the extraction cache.** It is cached today, so neither a resume nor ADR-140 §5's retune path asks the sidecar again about a cached `capacity` or `internal` refusal. That is older than this record, and is [#383](https://github.com/algernon28/vespera/issues/383).

**Stages 3 to 6b.** §6 states the position, and each step needs its own ticket.

**Whether committed timeout verdicts should be retried on a resume.** They are kept, as they would be after a recovery within one invocation (§4). A retry would cost up to a five-minute call each, to revisit a reading ADR-071 already accepts.
