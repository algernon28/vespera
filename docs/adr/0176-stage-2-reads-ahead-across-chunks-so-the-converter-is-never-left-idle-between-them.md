# ADR-176 — Stage 2 reads ahead across chunks, so the converter is never left idle between them

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §1 and Consequences, where they say *"The read-ahead is Spring Batch's own chunk loop, which dispatches every occurrence of a chunk before processing the first"*. That bounded what is in flight to one chunk. The read-ahead is now a window `ConversionDispatch` keeps itself, and it crosses chunk boundaries (§1). ADR-140's width of eight, its §2 definition of "consecutive", its §3 serial write path, its §4 call shape and its §5 are untouched, and `CHUNK_SIZE` stays sixteen.
- **Amends**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) Consequences, where it says of the chunk that was open at a stop that *"those at most 16 go back to Docling"*. With the window, up to 16 more do: the conversions that finished for occurrences read ahead and not yet taken (Consequences). ADR-181's resume rule and everything else in it are untouched.
- **Rests on**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §2 (a stopped stage 2 finds its place in the ledger, never in the reader's saved position), [ADR-036](0036-spring-batch-with-resourcelessjobrepository-camel-dropped.md) (the job repository holds nothing past the JVM), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) (the call timeout and the two streaks), [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) (a dropped connection is retried by the processor), [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) (what the extraction cache keeps).
- **Settles** [#369](https://github.com/algernon28/vespera/issues/369).

**One word here is Spring Batch's, not the project's.** A *chunk* is Spring Batch's commit interval: the 16 occurrences stage 2 reads, processes and writes in one transaction. It is not the piece of a document's text whose boundaries the **Chunk cache** stores.

## Context

### What was measured

On the whole-archive run of 2026-09-29, with Docling on the GPU (ADR-170), stage 2 converted about **40 to 50 files a minute**: 46, 34 and 69 in three whole minutes, 10:38 to 10:40. Docling was mostly idle in that time.

- **Docling's CPU** was 0.2%, 0.8%, 4% and 0.7% in 4 of 6 samples taken 4 seconds apart, and 111% and 458% in the other two. **The GPU** was at 3 to 20% in 5 of the 6, and 56% in one.
- **Docling's own log** showed jobs finishing in 0 to 12.5 seconds, in bursts, with gaps between them.
- **Vespera's step thread** was found by `jstack` in `PendingConversions.take` → `FutureTask.get` in 7 of 10 samples, waiting on one particular conversion.
- **Vespera's conversion threads**: only **1 to 3 of the 8** `stage-2-conversion-N` threads were in a Docling call in each of 3 samples.

### Why

ADR-140 keeps up to 8 conversions in flight, but only within one chunk. `ConversionDispatch.read` dispatched each occurrence as Spring Batch read it, and Spring Batch reads exactly one chunk before it processes any of it.

1. The 16 reads dispatch 16 calls onto a pool of 8 threads.
2. The processor then takes them in read order. A slow first file blocks the take while later files have already finished.
3. As the chunk drains, fewer and fewer calls are in flight. Nothing new is dispatched until the next chunk's reads begin, and those begin only after the writer has committed the chunk's shingles, metrics and verdicts.

So Docling idled at the tail of every chunk and during every commit.

## Decision

### 1. `ConversionDispatch` keeps a window of its own, sixteen occurrences beyond the one it returns

`ConversionDispatch` holds a first-in, first-out window of occurrence ids it has already read from its delegate and already dispatched.

- **`LOOKAHEAD` is `2 × CONVERSION_CONCURRENCY`, which is 16**: a constant in `ExtractionJobConfiguration` beside `CONVERSION_CONCURRENCY`. Two waves of the width, so that a whole wave is queued behind the one converting when a chunk's own calls are done. It is a code default for ADR-140 §1's reason: it says what the machine can carry, not what an operator judged about the corpus. That it equals `CHUNK_SIZE` is a coincidence, and nothing depends on it.
- **`read()` tops the window up, then returns its head.** While the window holds fewer than `LOOKAHEAD + 1` ids and the delegate has not ended, it reads one id from the delegate, dispatches it exactly as before (`dispatchIfConvertible`: the cache lookup on the step thread, a worker for the HTTP call only), and adds it to the window. Then it removes and returns the oldest id, or `null` when the window is empty and the delegate has ended.
- **The delegate is never read again after it first returns `null`.**

So after the *n*th `read()`, `min(total, n + LOOKAHEAD)` occurrences have been dispatched. After the 16 reads of a chunk, the next 16 are already with the workers, and they convert while the chunk's last occurrences are taken and while the chunk commits.

### 2. Everything else about stage 2's concurrency stands

- **The order.** `read()` returns ids in the delegate's order, and the processor takes them in that order, whatever order their calls finish in. ADR-140 §2's streaks are still counted over consecutive occurrences on the drain. `ExtractionCircuitBreaker`, `ExtractionTimeoutStreak`, `ExtractionItemProcessor` and `PendingConversions.take` are not changed.
- **The width.** Still 8 worker threads, so still at most 8 HTTP calls open at once. ADR-071's five-minute call timeout starts when a worker places the call, not when the call is queued for a worker, so a call waiting in the window spends none of it.
- **The write path.** Still serial, on the step thread (ADR-140 §3). The window holds ids, and a worker still touches nothing but the Docling call.
- **`CHUNK_SIZE` and `CONVERSION_CONCURRENCY`** keep their values.

### 3. Memory stays bounded

What is dispatched and not yet taken is at most one chunk, which Spring Batch has read and not yet processed, plus the `LOOKAHEAD` ids in the window: 32 responses at most, each one Docling JSON document, where it was 16.

### 4. Reading ahead of what was committed changes no restart behaviour

Nothing restarts the extraction step from a saved reader position. This was checked by reading every use of `ExecutionContext` under `src/main` at the commit this record ships with:

- `InvocationRuns`, `StageRuns`, `RunCompletion`, `VesperaCommand`, `ByteLevelReductionTasklet` and `GenerationTasklet` use the **job** execution's context to carry the run ids one invocation minted from step to step. None stores a reader position.
- `ConversionDispatch`, `OccurrenceReader` and `UnrecordedOccurrences` implement `open` and `update` only by passing them to the reader they wrap.
- `SeedCorpusComparison`, `ConfidenceDistribution`, `DocumentFrequency`, `ItemStreamReaders`, `UnrecordedOccurrences` and `ByteLevelReductionTasklet` open a reader over `new ExecutionContext()`, an empty one, to read it from its start.

The job repository is resourceless (ADR-036), so no context outlives the process. A stopped stage 2 finds its place in the ledger (ADR-181 §2): an occurrence that was read ahead and never committed carries no row under the run, so the next invocation reads it again. #369's ticket rested this point on ADR-115's discard; ADR-181 replaced that for stage 2, and the conclusion holds for the stronger reason ADR-181 gives.

`ConversionDispatch.update` still delegates. The position the delegate saves may be up to `LOOKAHEAD` occurrences past the last one processed, and by the above nothing reads it.

### 5. `close()` drops what was read ahead and never taken

`close()` shuts the workers down, as before. It then empties the window, and for each id in it removes the entry from `PendingConversions` and cancels its call. Nothing was written for those occurrences, because a response is written to the extraction cache only in `PendingConversions.take`, on the step thread, when the processor takes it.

## Consequences

**The converter is fed across the commit.** When a chunk's reads end, the 16 occurrences after it are already dispatched, so the workers have up to two waves to convert while the step thread waits on a slow file, takes the chunk's tail and commits. Nothing more is dispatched until the next chunk's reads begin, because dispatch happens only in `read()`: the first chunk's reads dispatch 32 calls, and each later chunk's reads dispatch 16, as before, but for the chunk after their own.

**The step thread still waits on a slow first file.** The processor takes in read order, so one slow file still holds the drain. What changes is that the workers go on converting up to 16 occurrences beyond the chunk while it does. Past that the window is full and nothing more is dispatched, so a file, or a commit, that outlasts those 16 conversions still leaves workers idle. Taking out of order would remove that, and would change what "consecutive" means (ADR-140 §2). It is not done here.

**A stop loses up to 16 more conversions.** A conversion that finished for an occurrence read ahead and not yet taken was never written to the extraction cache (§5), so after a stop those occurrences go back to Docling, beside the at most 16 of the chunk that was open (ADR-181 Consequences).

**A sidecar that stops answering fails a longer block.** Up to 32 calls can be dispatched when it stops, where it was 16. The processor still meets their failures consecutively, in read order, and ADR-071's breaker still stops the step at the fifth. A dropped connection is still retried by the processor itself, on the step thread (ADR-175).

**The throughput after the change is not measured in this record.** #369 asks for Docling's CPU and GPU samples and the files-per-minute figure from a real stage-2 run after the change, beside the figures in Context. They belong in the pull request that ships this.

**`CONTEXT.md` needs no new term.** The window is an implementation detail of the reader, named only in code.

**The tests that pin this** are `ConversionDispatchTest`, which drives `ConversionDispatch.read` and `close` directly over a converter whose calls do not answer until the test lets them:

1. After each `read()`, the converter has been asked about that many occurrences and the 16 after them, up to the total. After the 16 reads of the first chunk, 32 have been asked about.
2. Occurrences are returned in the delegate's order, with the calls the converter was holding let go latest first.
3. After the delegate ends, the window is returned, then `null`, and the delegate is read exactly once more than it has occurrences.
4. Closing with occurrences in the window leaves no call running, nothing pending for them and nothing in the extraction cache.

`PendingConversionsTest` pins that an abandoned call is cancelled and that a later `take` for it finds nothing.

ADR-140 §2's order of failures is pinned by `ExtractionConcurrencyTest` and `ExtractionCircuitBreakerTest`, and the assembled step by `ExtractionStepTest` and the stage-2 invocation tests. No claim of theirs changes. Two things in them had to follow the window:

- **`FailedStepSaysItFailedTest`** scripts 16 conversions and then timeouts, and its script was handed out in the order calls arrived. With the window, a call for the second chunk can arrive before the first chunk's last one and take its answer. `ScriptedExtractor` can now hand its answers out in the order documents are read, and that test asks it to.
- **Wording** that said a chunk is all that is dispatched ahead: one claim's text in `ExtractionStepTest`, and comments in `ConverterStopsPartwayBeans`, `ExtractionResumeInvocationTest` and `ServiceScopeRefusalInvocationTest`.

## What this does not decide

**Taking completed conversions out of read order.** See Consequences.

**The window's size as a profile value.** It is a constant, for ADR-140 §1's reason.

**How the stop counts treat faults in a row across a restart or across dropped connections.** That is [#385](https://github.com/algernon28/vespera/issues/385) and [#393](https://github.com/algernon28/vespera/issues/393).
