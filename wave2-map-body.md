# Wave 2: rules go home

This is Wave 2 of the architecture simplification (`handouts/architecture-simplification.md`, §5 "Wave 2" and §2 D2). It comes after Wave 1 (#303, ADR-157, merged as e191dfb).

**It re-mints every stage from stage 1 down.** Content-identity resolution moves into `corpus`, which is the only module stage 1's run identity names (ADR-058), and a re-mint is transitive (ADR-048). No persisted cache key changes, so stage 2's conversions and the embeddings are cache hits on the re-run. Land it at a corpus boundary.

## Why

`pipeline` is meant to be wiring, but it still holds rules that decide what gets written. That miscosts in two opposite directions (plan §2 D2):

- **Stages 3 to 6b over-invalidate.** A rule in `pipeline` is versioned under `pipeline`, which those stages name, so any edit to the wiring re-mints them.
- **Stages 1 and 2 under-invalidate.** Neither names `pipeline`. ADR-070's classification, ADR-071's timeout streak and stage 1's content-identity resolution are versioned under nothing today, so a change to any of them re-mints no run.

Moving each rule into the module its stage already names versions it for the first time. That is also the precondition for Wave 7 (taking `pipeline` out of stages 3 to 6b's identity).

## Checked against main at e191dfb

The plan's line numbers were taken before Waves 0b and 1, so these are the current ones. The plan is otherwise still accurate.

| Rule | Where it is now | Goes to |
|---|---|---|
| 6b's per-cluster loop, the five-in-a-row breaker (`CONSECUTIVE_TURNED_DOWN_ANSWERS`, ADR-111) and the completion rule (ADR-116) | `GenerationTasklet.java:225-382`, the work handed to `TaskletSteps.once`'s work | `synthesis` |
| The lead-document and cluster-label rule (ADR-106). It is still computed twice per cluster: `labelFor` at :163 and `reportOf` at :192 each call `leadDocumentOf` | `ArrangementTasklet.java:233-266` | `synthesis` |
| ADR-070's failure classification and ADR-071's timeout streak | `ExtractionItemProcessor.java:254-376`, plus `ExtractionTimeoutStreak` | `extraction` |
| The extractor identity string (the persisted extraction-cache key, ADR-012, ADR-147). `ExtractorIdentity` the record is already in `extraction`; only its composition is left in `pipeline` | the `@Bean @Lazy extractorIdentity` method, `ExtractionJobConfiguration.java:287-297` | `extraction` |
| Content-identity resolution: group by size, hash within groups of two or more, resolve (ADR-057, ADR-067, ADR-069). `ContentHash`, `DuplicateResolution` and `ContentIdentity` are already in `corpus`; the size-then-hash driver is not | `ByteLevelReductionTasklet.java:219-279` | `corpus` |
| The eight profile keys, written out by hand, in the same order as `Profile`'s components (`profile/Profile.java:88-96`) | `GenerationTasklet.profileValues`, `:507-517` | derived from `Profile` |

What Wave 1 changed for this wave:

- **Generation's loop now runs inside `TaskletSteps.once`'s work** (ADR-157 §4). The step shell and the order of writing the deliverable before recording completion (ADR-157 §5) stay in `pipeline`.
- **Step and stage names come from `StepNames` and `StageModules.** `RunsAndNamesHaveOnePlaceTest` fails if any other `pipeline` source holds a persisted name as a literal.

## Scope

1. **`synthesis` gets 6b's loop, its breaker and its completion rule.**
   - The loop reaches a cluster's exemplars through a `synthesis`-owned callback, `ClusterExemplars`, on the precedent of `SurvivorPictures` (ADR-149). The callback also supplies the winning seed's path, which `ClusterCall` needs.
   - The callback stays lazy per cluster, so file reads, warnings and memory stay as they are.
   - The loop returns a sealed result: finished, incomplete, or stopped with its faults.
   - `pipeline` keeps three things: stopping the step (`stopTheStep`), writing the deliverable, and recording completion through `once`.
   - This extends ADR-110's hand-over, and needs an amendment saying so.
2. **`synthesis` gets the lead-document and label rule,** computed once per cluster.
3. **`extraction` gets ADR-070's classification and ADR-071's timeout streak,** with what ADR-139 and ADR-140 say about them.
   - ADR-140's rule that both streak counters are observed on one thread must survive the move.
   - `ServiceScopeFailureException` stays the Batch skip marker.
   - `ExtractorStoppedAnsweringException`'s fully-qualified name is pinned by `ExceptionNamingTest.java:58`. Either the name stays, or the `analyst` moves the test with it.
   - `ExtractionItemProcessorTest`'s 18 tests, which use the test-only constructor, are ported to the new home, not deleted.
4. **`extraction` gets the extractor identity string.** It must come out byte-identical, and `ExtractorIdentityCompositionTest` is the guard. The composition must stay lazy, so that nothing asks the sidecar for its version before `ExtractionHealthCheckListener` has run (ADR-071).
5. **`corpus` gets content-identity resolution,** size then hash. ADR-040 is a reconstituted record, and this changes the reading of it given at `ByteLevelReductionTasklet.java:54-56` ("Lives here rather than in `corpus` because `corpus` does not know what a stage is"). The amendment should quote that sentence.
6. **The profile keys come from `Profile`.** `synthesis` may not read `profile` (ADR-110), so `pipeline` still hands `Deliverable` the pairs. What changes is that the list is derived from `Profile`'s component order, not written out by hand.

## What must not move (plan §4)

- **Persisted cache keys stay byte-identical,** the extractor identity string above all.
- **Persisted names stay byte-identical:** every `run.stage` and `finished_step` value.
- **Run ids re-mint only as declared,** which here means every stage. `RunIdentityGoldenTest` is not edited: every `config_consumed` and every module list stays byte-identical. Only the implementation versions move.
- **Operator-visible text does not change.** The guards are `OperatorTextTest`, `docs/check-claims.mjs` and the README.
- **The module rule holds:** capability modules depend on `ledger` alone, plus the declared `extraction` → `corpus` exception (ADR-100). No new exception.
- **Gates, faults and exit codes keep their outcomes** (ADR-080, ADR-099, ADR-141), and so does ADR-111's repair pass.
- **The job keeps its shape:** step order, ADR-139's listener order, and ADR-140's chunk size, concurrency and single writer thread.
- **One writer, bounded memory:** nothing new writes from a worker thread or holds the whole corpus (ADR-127, ADR-140, ADR-149 §9).
- **No test is weakened.** A test that has to move is moved by the `analyst`.

## Decisions for this wave's ADR

- **The callback's shape and the sealed result:** what `ClusterExemplars` takes and returns, where the result type lives, and the ADR-110 amendment.
- **Whether `ExtractionCircuitBreaker` (ADR-139's service-scope breaker) moves with the timeout streak.** The plan names only the streak, but the two counters share ADR-140's one-thread rule.
- **`ExtractorStoppedAnsweringException`:** keep its fully-qualified name in `pipeline`, or move it and have the `analyst` update `ExceptionNamingTest`.
- **Where "derived from `Profile" lives:** a `profile` method yielding the key and value pairs in component order, or reflection over the record in `pipeline`. Say which modules' implementation versions each choice moves.
- **The ADR-040 amendment's wording.**
- **The measured size.** The plan estimated about −1,000 lines in `pipeline`, partly moved rather than deleted. Replace that estimate with the measured diff.

## Sequencing with open work

Three open tickets edit the classes this wave moves. They are owned by another session, and the ADR should say which lands first. Otherwise each merge rewrites the other's diff.

- **#317 and #318** change `ClusterSynthesis`'s prompt, reply allowance and answer parsing. Scope 1 moves the loop that calls it.
- **#319** fixes stage 2's `afterStep` chain when docling-serve is down. That chain reaches `extractorIdentity` through `ExtractionFaultRecorder` and `StageRuns.extraction()`, and scope 4 moves the bean behind it.
- **#305** (Docling segfaults during seed extraction) may touch `extraction`'s Docling client.

## Out of scope

- Recording stage 2's cache key (Wave 3, #287).
- `Ledger` and table boundaries (Wave 4).
- Splitting `Deliverable` (Wave 5).
- Taking `pipeline` out of run identity (Wave 7, deferred).
- Changing any module list in `StageModules`.

## Acceptance criteria

- [ ] An ADR records the decisions above, with its row in `docs/adr/README.md` and the count in `AGENTS.md`. It amends ADR-110 and ADR-040.
- [ ] No rule in the table above remains in `pipeline`. The test from the plan's §3 holds for every class left there: no change to it can alter a verdict, a cache key, a cluster or a sentence of the deliverable.
- [ ] Each moved rule has unit tests in its new module. `ExtractionItemProcessorTest`'s 18 tests are ported, not deleted.
- [ ] `ExtractorIdentityCompositionTest` passes unedited. The extractor identity string is byte-identical.
- [ ] `RunIdentityGoldenTest` passes unedited.
- [ ] `ModuleBoundariesTest` passes with no new allowed dependency.
- [ ] `./mvnw clean test` is green, and so are `docs/check-claims.mjs` and `docs/render-docs.mjs --check`.