# ADR-155 — A seed file that will not open is recorded under a reason of its own, and seed extraction records no completion until it opens

- **Date**: 2026-09-25
- **Status**: accepted
- **Extends**: [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) (the unusable seed) and [ADR-152](0152-a-survivor-whose-file-will-not-open-is-still-asked-about-without-its-opening-and-a-step-that-records-completion-stops-instead.md) §4, which left this case out of its scope and filed it separately. ADR-152 drew its line at whether a step records completion. This record applies that line to the one step before 5c that reads the archive.
- **Keeps**: [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) and ADR-083's gate (no usable seed means no run), [ADR-092](0092-the-seed-side-of-the-mismatch-comparison-is-measured-by-seed-extraction-under-the-measurement-run.md) (seed extraction writes the seed side's measurements under the measurement run), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (a finished step does no work, and an unfinished one discards its own rows and is continued under its own id), [ADR-132](0132-stage-5s-gate-preamble-is-one-seam-that-answers-open-or-shut-with-the-one-sentence.md) (one gate preamble, one sentence per gate), and [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md) (this is tolerance, not a promise of consistency against a live archive).
- **Rests on**: a reading of `SeedExtractionItemProcessor`, `SeedExtractionItemWriter`, `SeedMeasurementRun`, `UsableSeedGate`, `StageFiveGates`, `SeedCorpusComparison` and `SeedCorpusComparisonReport` at commit `25eab18`. No real seed folder was run for this record. The invocation tests that ship with it are the execution.
- **Settles** [#291](https://github.com/algernon28/vespera/issues/291).

## Context

Seed extraction reads every seed of the seed walk before it writes anything, because ADR-083's gate (no usable seed at all) is a fact about the whole folder (ADR-092). For each seed, `SeedExtractionItemProcessor.doProcess` resolves the file and calls `DoclingExtractor.contentHashFor`, which reads the whole file. If the file is locked by another program, has lost its read permission, or has been moved or deleted since the seed walk, that call throws `UncheckedIOException` (`could not hash <path>`). Nothing catches it. The seed job is not fault-tolerant, so the step fails, the invocation fails, and nothing after stage 4 runs.

**The processor runs on every invocation, finished or not.** The writer skips its own writes when the step is already recorded as finished under the run, but the read-and-convert still happens, because it is what answers `UsableSeedGate` for the rest of stage 5 in this invocation. So today a seed file that is locked in invocation 3, long after seed extraction finished, fails that invocation too, although nothing it reads is needed.

**ADR-083's unusable seed is a fact about the document.** The converter answered, and the answer carried no text. ADR-083 made that safe to proceed on with two arguments: it is recorded and reported, and *a corrected seed folder is a different run*, so scores taken against a partial seed set can never be mistaken for scores against a complete one.

**A seed file that will not open is a fact about the archive at one moment**, which is ADR-152's distinction. The second of ADR-083's arguments does not reach it. The walk records only what a directory listing shows (ADR-115), so a locked file or a removed permission is the same observation as before. The measurement run's identity names the seed folder's path, not its contents (`SeedMeasurementRun.configConsumed`). Releasing the file therefore changes nothing in the run's identity: the corrected state is the *same* run.

**The project's rule** is that a bad row fails the occurrence and not the run, and that a run stops only for faults that spoil every row. A missing seed is of the second kind. A relevance score is the maximum over seeds (ADR-020), so a seed left out moves the score of every corpus document, and with it the floor, the partitions, the clusters and the arrangement.

**ADR-152 §4's line applies directly.** Seed extraction records its completion (ADR-116), and what it records, the `unusable_seed` rows, is the seed set every later step takes its maximum over. `EmbeddingScoringTasklet` and `RelevanceScoringTasklet` both read `UnusableSeeds.forRun` and drop those seeds. If seed extraction recorded the seed as unusable and then recorded itself finished, the scores, floor, clusters and arrangement would be computed and sealed without that seed, under a run every later invocation walks past (ADR-115). Releasing the file would change nothing, because the run is the same and the step is finished. This is the permanent hole ADR-152 and ADR-121 refuse.

## Decision

### 1. A seed file that will not open is not an unusable seed in ADR-083's sense, but it is recorded as one while the step is unfinished

When `contentHashFor` throws `UncheckedIOException` for a seed, seed extraction does not fail. It records that seed as having produced no text, under a reason of its own:

> the file could not be opened when seed extraction read it

This reason is distinct from `UsableText.NO_ALPHANUMERIC_CONTENT`. That reason covers both a conversion that carried no text and one the converter refused (ADR-083). The two are different facts. The new reason is about the archive at the moment it was read, and says nothing about the document. Its wording is fixed, one reason for every such seed, so that it can be queried. The exception's own message goes into the warning in §4, not into the row.

**The catch is narrow**, as in ADR-152 §1. Only the `UncheckedIOException` from hashing the seed is caught. Anything else the step meets still fails it. A file that hashes but vanishes before the converter reads it is not this case, and it is not decided here.

**No conversion is attempted and no measurement is taken for that seed.** Without the file's bytes there is no content hash to key the cache with and nothing to measure. It gets an `unusable_seed` row and no `extraction_metric` row.

### 2. While any seed file would not open, seed extraction records no completion, and stage 5 goes no further in that invocation

Once the whole folder has been read, the writer behaves as follows.

- **If no seed produced text**, ADR-083's gate applies exactly as it does today. No run is minted, no row is written, and the existing warning is logged. A seed whose file would not open is not usable, because no one knows whether it would be.
- **If the step is already recorded as finished under the run**, nothing changes. The rows written when it finished still stand, and the seed's recorded usability is the one that counts. A seed file that will not open today writes no row, removes no row and shuts no gate. The file is used for nothing this invocation records, which puts this case on ADR-152's tolerating side, the same as the labelling page. A later step that has not finished and needs the file, such as embedding scoring, still stops on it under ADR-152 §4.
- **If the step is not finished**, the writer discards and writes this step's rows as it does today, including one `unusable_seed` row for each seed whose file would not open, with §1's reason. **It then does not record the step as finished.** For the rest of this invocation, every stage-5 step that consults the usable-seed gate is shut, with its own sentence (§3).

**Why a gate and not a failure**, when ADR-152 §4 has embedding scoring, relevance scoring, clustering and the arrangement *fail*:

- **Seed extraction has already read every seed when it decides.** Those four steps meet the file partway through their own work. Seed extraction reaches its decision only after the whole folder has been converted (ADR-092), so it can name every seed file that would not open in one invocation. A failure would name only the first, and an operator with three locked seeds would need three invocations to find them.
- **It has somewhere to put the fact that corrects itself.** The rows go under a run whose seed-extraction step is not finished. The next invocation discards them before it writes (ADR-116), so no row outlives the state it describes. Nothing reads them in the meantime, because every step that reads `unusable_seed` is shut by §3 in the invocation that wrote them.
- **It is a gate by CONTEXT.md's definition**: a value the pipeline requires and does not have. The value is a seed's bytes. Release the file and no gate occurs. Leave it and the run ends there, having recorded everything it learned. This step already holds ADR-083's gate, which ends the invocation successfully, and this is the same kind of ending.

**The invocation succeeds** and exits 0, as it does at ADR-083's gate. The earlier stages keep everything they recorded.

### 3. The gate preamble gains one reason

`StageFiveGates` shuts every step that consults the usable-seed gate (the seed/corpus comparison, embedding scoring, relevance scoring, the relevance floor, clustering and the arrangement) with a fourth reason, asked right after the usable-seed question and in the same vocabulary:

> a seed file could not be opened

So the comparison logs `stage 5's seed/corpus comparison is gated: a seed file could not be opened.` The labelling page does not consult the usable-seed gate. With no scores under the run, it stays shut on its own reason (ADR-088). Generation is shut because no arrangement was made in this invocation (ADR-154 §2).

`UsableSeedGate` carries this second fact for the length of one invocation. It is in-process for the same reason as the first: the gate is answered before anything a later step could read it from exists.

### 4. Each such seed is named in a warning, and the step says why it stopped

For each seed whose file would not open, the processor logs one warning. The warning names the occurrence, the file as resolved under the seed folder, and the exception's message. It is logged in both branches of §2, finished or not, because in either one the operator should know the file is unavailable.

In the unfinished branch, the writer logs one warning after writing. It gives how many seed files could not be opened, says that seed extraction is not recorded as finished and that stage 5 goes no further in this invocation, and says to release them and run the same command again.

### 5. It is read again on every invocation, and the comparison report never counts it

**Retry is the next invocation, with nothing to configure.** The processor reads every seed on every invocation already. Once the file opens and the walk is unchanged, the run id is the same. Because the step is unfinished, it discards the rows from the invocation before and writes them again. The seed is then converted and measured like any other: usable, or unusable for ADR-083's reason. The step records its completion, and stage 5 carries on in that same invocation.

**The seed/corpus comparison report never shows such a seed.** The comparison is shut in every invocation in which a seed file would not open, so it never measures a seed set with a hole of this kind. Once the files open, the seed is an ordinary measured seed. `SeedCorpusComparisonReport`'s count of seeds that "carried no measurement at all" is not how this state is reported, and it needs no change.

**A seed deleted between invocations** is not this case. The walk sees a different observation, mints a new seed walk, and the file stops being a seed (ADR-115). What happens to a changed seed set under a seed path whose run id does not change is outside this record.

## Consequences

**One seed file that will not open no longer fails the invocation.** Stages 0 to 4 run and record as before. Seed extraction names every such seed in one pass, records each one under a reason that says what happened, and stops stage 5 with a sentence rather than a stack trace. The remedy is the same as in ADR-152: release the file and run the same command again.

**No relevance score is ever taken over a seed set that is missing a seed because its file was locked.** A partial seed set is still possible when a seed produced no text (ADR-083). It is not possible when a seed could not be read.

**A seed file that stops opening after seed extraction has finished costs a warning and nothing more** at this step. A later step that is not finished and must read that seed still stops, as ADR-152 §4 says.

**Accepted edge.** If every seed that produced text on an earlier invocation cannot be opened on this one, ADR-083's gate shuts stage 5 for this invocation, even though seed extraction's rows under the run stand. Nothing is lost, and the next invocation in which a usable seed opens carries on. Telling this case apart would mean minting or looking up the run while no seed is known to be usable, which ADR-080 refuses.

**What the implementation owes** (`spec-implementer`):

- `SeedExtractionItemProcessor.doProcess` catches the `UncheckedIOException` from `extractor.contentHashFor(file)` and nothing else. It logs §4's per-seed warning and returns an outcome carrying §1's reason, with no measurement and without calling `SeedConversions.convert`.
- `SeedExtractionOutcome` gains a way to carry that outcome: a factory for a seed whose file would not open, with the measurement absent, and a way to ask for it. The reason is held as one constant, like `UsableText.NO_ALPHANUMERIC_CONTENT`. Such an outcome is not `usable()`.
- `SeedExtractionItemWriter.afterStep` counts usable outcomes for `UsableSeedGate` as today. In the unfinished branch it writes no `extraction_metric` row for an outcome with no measurement, writes an `unusable_seed` row for it with §1's reason, and does not call `ledger.finishStep` when any such outcome exists. It then logs §4's summary warning and tells `UsableSeedGate` that a seed file could not be opened. The finished branch and the no-usable-seed branch keep their behaviour and do not set that fact.
- `UsableSeedGate` carries that fact for the invocation. `StageFiveGates.modelSeedWalkUsable` and `seedWalkAndUsable` shut on it with §3's reason, asked after the usable-seed question.
- `SeedExtractionItemProcessor`'s and `SeedExtractionItemWriter`'s javadoc state the rule, and `ContentHashing`'s one-line message stays as it is.
- `AGENTS.md`'s count of decisions is raised by one (ADR-129), and its open-defect paragraph records #291 as closed by this record.

**What pins it**, in `SeedExtractionInvocationTest`. The fixture moves a seed out of the seed folder at the moment seed extraction reads it, using a hook on `PathScriptedExtractor.contentHashFor`. The move is an atomic rename, so the file's size and times are kept. Because the read then really finds no file, the test runs on every platform and as any user:

- The seed file is moved away during the invocation that first extracts the seed set. The invocation must succeed, and one measurement run must be minted. One `unusable_seed` row must name that seed with §1's reason, and only the usable seed may carry a metrics row. Neither seed extraction nor the comparison may be recorded as finished, the comparison's shut sentence must name §3's reason, and a warning must name the file. Once it is moved back, the next invocation must keep the same seed walk and the same run, record both steps as finished, leave no `unusable_seed` row, and measure both seeds (§2, §5).
- A seed file is moved away during an invocation that comes after seed extraction finished. The invocation must succeed, the step must still be recorded as finished, no `unusable_seed` row may appear, both seeds' metrics rows must stand, and a warning must name the file (§2's finished branch).
