# ADR-210 — A file that cannot be read is marked and the step goes on, and a corpus root that can no longer be listed stops it

- **Date**: 2026-10-07
- **Status**: accepted
- **Amends**: [ADR-206](0206-stage-2-records-the-key-it-looked-the-extraction-cache-up-under-and-no-step-after-it-opens-an-archive-file.md) §7, one sentence: stage 2 no longer fails on `could not hash <path>` (§8.1).
- **Amends**: [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7, the sentence that lists the reasons whose file left nothing stored, and with it the review list's own sentence, word for word (§8.2).
- **Amends**: [ADR-207](0207-a-file-is-hashed-through-a-fixed-buffer-so-its-size-sets-no-limit.md) §4, which left this open, and the Consequence that repeats it (§8.3).
- **Extends**: [ADR-184](0184-five-failures-in-a-row-stop-stage-2-only-when-the-converter-then-fails-a-control-conversion.md) §4 and ADR-206 §2, each by one row of its table (§8.4, §8.5); [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) §2's `HashingProgress` and `CheckingProgress`, each by one method with a default body, and its sentence on the order within one survivor, by one step (§4).
- **Keeps**: ADR-207 §3 (an `Error` thrown while a file is hashed is caught by nothing), [ADR-155](0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md) (seed extraction, unchanged, its §1 sentence on a seed that vanishes after it is hashed included), [ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md) and [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) (the broken check and the formats stage 1 records), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) (a stopped stage 2 keeps its committed chunks), [ADR-185](0185-stage-2-asks-the-converter-again-under-a-run-of-its-own-when-extractionattempt-is-raised-and-nothing-is-discarded.md) (asking again is a new stage-2 run), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (the module rule and its one exception), [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) (no schema version moves).
- **Rests on**: ADR-175 (a file that fails is marked and the run goes on, and §3a's reason for bounding that rule), ADR-184 §4 (what is evidence about the converter), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (which run ids move); the operator's decisions on #452, grilled to the design recorded here; and the invocation tests that ship with this record, each run against commit `6eb88a2` before anything in `src/main` changed, with one throwaway measurement beside them (Context). No archive and no working directory was opened for this record (ADR-196).
- **Settles** [#452](https://github.com/algernon28/vespera/issues/452), which is the second point of [#449](https://github.com/algernon28/vespera/issues/449).

Two phrases in this record are its own. A file **cannot be read** when the file system will not hand over its bytes: it is locked by another program, it has gone since the walk, its permission was removed, or a read fails partway. The **archive has gone** when the corpus root can no longer be listed: the disk dropped out, the share was disconnected, the folder was moved.

## Context

ADR-207 §4 measured what a file that cannot be read does, and left the change to this ticket because of one fact: today's stop is the only thing that notices an archive that has gone. Marking the file and going on would, with nothing else, mark every file after a disk drops out, complete the step and exit 0.

**Measured at `6eb88a2`**, by the tests that ship with this record and by one throwaway measurement that does not ship (the last row). Each test the rows below come from is red against that commit; one more test that ships with them is green there by design and measures nothing in this table (What pins it). Synthetic files only; the "folder" is a corpus root of the test's own, moved away whole.

| Where | What the test did | What happened |
| --- | --- | --- |
| stage 2's reader, its hash | moved one file away as stage 2 hashed it | the step failed on `could not hash <path>`, its closing line said to fix that and bring docling-serve back, and the invocation exited 1 |
| stage 2's processor, its hash | deleted the file before the processor hashed it | `UncheckedIOException: could not hash <path>` left the processor |
| stage 2, the call, for a file stage 1 had hashed | deleted five such files, all read in a row, after stage 1 and before stage 2 read them | **each read as the converter's doing.** The production client reads a file as it posts it, so a file that is gone fails the call before a byte is sent, and the client reports `I/O error on POST request for "<url>": <path>` as a lost connection. Each file was waited for, asked about again, and marked `crashed the converter: docling-serve dropped the connection twice while converting this file: …`; the five made a row of five; the control conversion was sent; where it did not convert, the step stopped |
| stage 2, the call, the folder gone | moved the folder away just before stage 2 placed its first call | **every file was marked `crashed the converter`, and the step completed.** This is the outcome ADR-207 §4 said a bound must prevent, and it happens today, by the route #452 suspected |
| stage 1, hashing | deleted one of two files of one length after the other was hashed | the step failed and the whole stage rolled back: no verdict, no hash, nothing recorded as finished |
| stage 1, hashing, the folder gone | moved the folder away after the first hash | the same, on `NoSuchFileException` naming the file |
| stage 1, first pass, the folder gone | moved the folder away after the first file was checked | the first file's log count could not be read, a warning; **every later file was marked `broken`** (*"the file could not be read: …"*), and stage 1 finished |
| stage 2's setup, the folder gone | stage 2 began after the folder had gone: in the invocation of the row above, once stage 1 had finished, which that row's test logs and asserts nothing about; and in a throwaway measurement, outside the repository and not shipped, that moved the folder away as stage 2 checked the converter's health | the step failed before reading anything, building its run holder: `Error creating bean with name 'scopedTarget.extractionReader' … StageRuns: Constructor threw exception`. Nothing was removed; the line names neither the folder nor what to do |

So three of the four places a file is read already fail the wrong way for an archive that has gone: stage 1's first pass marks every file broken, and stage 2's call marks every file as having crashed the converter, without stopping.

## Decision

### 1. The rule, and its bound

**A file that cannot be read is marked, and the step goes on**, which is ADR-175's rule for a file the converter could not convert. Where the mark is depends on the stage (§4, §5).

**Before any file is marked for that, stage 1 and stage 2 ask whether the archive has gone, and if it has, they stop and remove nothing.** The question is asked on every such failure, not once per step: a disk drops out partway, and the first failure after it is where it can be seen.

### 2. The bound: can the corpus root still be listed

**The check** opens the canonical corpus root as a directory listing and closes it, reading no entry. If that throws, or the root is not a directory, the archive has gone. If it succeeds, the failure was the file's own.

- **Why listing the root.** It is the cheapest question whose answer is about the archive and not about one file. It reads no document. A count of failures in a row, ADR-175 §3a's shape, was the alternative: it cannot tell a folder of files another program holds from a disk that has gone, where the listing asks the question directly.
- **`pipeline` supplies it.** The canonical root is held by `ByteLevelReductionTasklet` and `StageRuns`, both `pipeline`'s. `extraction` may not name `corpus.Walk` (ADR-040, ADR-100), stage 2's sites are `pipeline`'s, and stage 1's two sites in `corpus` reach it through the callbacks of §4. One check, one message and one line of advice, written where the operator's lines are written (ADR-188).

**What it throws** is an exception of `pipeline`'s own, not skippable by stage 2's policy (it is not `ServiceScopeFailureException`). Its message is exactly the text below, with the canonical root written where it says `<canonical root>`, as a plain path with nothing around it:

`the corpus root <canonical root> can no longer be listed, so the archive has gone and nothing was removed for it`

**What the operator reads.**

- **Stage 2's closing line**, when this is the failure `StepFailure` finds beneath the framework, ends with `Reconnect the archive and run the same command again.` in place of the advice about docling-serve, as ADR-177 replaced it for a locked database file.
- **Stage 1** has no closing line of its own today. Where this stops it, `ByteLevelReductionTasklet` logs one ERROR line before the exception leaves the step: `Stage 1 (byte-level reduction) failed and is not recorded as finished: <the message>. Reconnect the archive and run the same command again.`

**What it removes: nothing, with one exception this record leaves open.** Stage 1 is one tasklet in one transaction, so all of its work in that invocation rolls back (ADR-207, Context). Stage 2's chunk in progress rolls back; its committed chunks stay (ADR-181), and a `could not be read` in them was written after the root had listed, so it is about its own file. The exception is a disk lost while stage 2 waits for the sidecar after a first lost connection: §3's checks run before `SidecarRecovery.awaitHealthy`, which can wait up to 190 s (ADR-175 §2), and nothing checks again after it, so a disk lost during that wait can leave one file marked `crashed the converter` for the archive having gone (What this does not decide). The next invocation, with the archive back, starts stage 1 again or resumes stage 2 from its committed chunks.

### 3. Where the check runs

| Where | What fails | If the root lists | If it does not |
| --- | --- | --- | --- |
| stage 1, second pass: `ContentIdentityResolution`, `ContentHash.sha256` | an `IOException` hashing a file of a length another file shares | the file is left unhashed (§4) | stop (§2) |
| stage 1, first pass: `BrokenCheck.check` | its read of the size (`Files.size`) or of the first bytes; the zip container's `IOException` that is not a `ZipException`; the PDF trailer's read | the file is marked `broken`, as today (§4) | stop |
| stage 2, reader: `ConversionDispatch.dispatchIfConvertible` | `could not hash <path>` from `DoclingExtractor.contentHashFor` | nothing is dispatched; the occurrence earns `could not be read` (§5) | stop, from `read()` |
| stage 2, processor: `ExtractionItemProcessor.contentHashOf` | the same | the same | stop |
| stage 2, processor: the first `DoclingConnectionLostException` of an occurrence, before `SidecarRecovery` is asked to wait | the call lost its connection | then check that the file opens: if it does not, `could not be read`, with no wait and no retry; if it does, ADR-175 §2 as today | stop |

**Not sites, and why.** `BrokenOrOutOfScope`'s log count (`TimestampedLines`) and its leading-bytes read already treat a failed read as "not a log" and "unreadable" in the mix: the file is kept, so nothing is removed for it, and the next file's broken check meets a gone archive. The retry after a first drop, ADR-175 §2: the file opened a moment before it, so a second drop is the converter's; that reading is wrong when the disk was lost during the wait between the two, which is left open (What this does not decide). Seed extraction: ADR-155, unchanged.

**Why the opening check on a lost connection.** The table above measured it: the client reports a file it cannot read as a lost connection. Without the check, a file gone after stage 1 hashed it costs the wait for the sidecar, a second call and a `crashed the converter` mark, and counts towards a row of five. The check opens the file for reading and closes it, reading no byte.

### 4. Stage 1

**Hashing: a file that cannot be read is left unhashed, with no verdict, and stage 2 meets it.** `ContentIdentityResolution` catches the `IOException` from `ContentHash.sha256` for that one file, records no hash and no verdict for it, and resolves the rest of its length among themselves. Stage 2 reads every survivor stage 1 recorded no hash for (ADR-206 §2), so it hashes that file itself, and marks it under §5 if it still cannot be read.

- **`HashingProgress` gains one method with a default body that does nothing**, called once for such a file in place of `hashed`, with the occurrence and the exception (recommended: `notHashed(OccurrenceId, IOException)`). `pipeline`'s implementation asks §2's question first, then logs one WARN line naming the occurrence and the cause, and counts the file as gone through, so ADR-192's `Stage 1 (byte-level reduction, content hash)` counter still reaches its total.
- **Why not `broken`.** The file has passed the broken check: it opened then. A verdict now would say something about the file that stage 2 can say more truly, and stage 2 reads it anyway.
- **Accepted:** a file left unhashed is never recorded as a copy at stage 1. If it is one and opens again by stage 2, both reach stage 2 and are looked up under one key. The conversion is paid once when one of them is read after the other's answer is stored, and twice when both are dispatched before either answer is stored: stage 2's reader dispatches every cache miss in its window, up to a chunk and sixteen more ahead of the occurrence being decided (ADR-176), and the cache is written only when an answer is taken (ADR-140 §3). Whether stage 4 then removes one of them is stage 4's rule and is not measured here.

**The broken check: unchanged, apart from the root check.** A file whose size or first bytes cannot be read is still marked `broken` with the reason it carries today.

- **`BrokenCheck.Result` gains a flag saying the read failed**, set by the four catches §3 names and by no other branch. `BrokenOrOutOfScope` tells `CheckingProgress` before it writes the verdict, through one method with a default body that does nothing (recommended: `couldNotRead(OccurrenceId, String reason)`), and `pipeline`'s implementation asks §2's question there.
- **ADR-188 §2's sentence on the order within one survivor gains that one step**, and otherwise stands. It reads, extended: check, record the detected format, count into the mix, decide, tell `CheckingProgress.couldNotRead` where the read failed, write the verdict, then call `CheckingProgress.checked`.
- **Why the verdict stays.** Stage 1 is the only place a format is detected, and it records one for every occurrence (ADR-094, ADR-095). A file left with no verdict and no format would reach stage 2's "no detected format is recorded" branch (ADR-100) and never `could not be read`, and nothing at stage 2 can ask stage 1 again: ADR-185 asks again by a new stage-2 run over the same stage-1 run. So a file that would not open at stage 1's first pass is sealed `broken` under a finished run, a lock included. That is left open (What this does not decide).

### 5. Stage 2

**A file stage 2 cannot hash, or that does not open when its call has lost its connection, earns `extraction-failed` at once**, with a reason that begins `could not be read: ` and goes on with what the file system reported: the cause beneath `could not hash <path>`, or the exception the opening check met.

- **It is not an extraction fault.** A fault is a call the converter got and blamed on itself (ADR-139). The converter was never asked about this file, or never received it.
- **No metric row, no key row, no cache row** (ADR-183, ADR-206 §2): no response came back. ADR-206 §2's table gains the row (§8.5).
- **No evidence about the converter**, as an occurrence with no detected format: it neither ends nor extends ADR-071's breaker, ADR-175 §3a's count of files dropped twice, or ADR-071's timeout streak. ADR-184 §4's table gains the row (§8.4). The judging is `extraction`'s (ADR-189): one method beside `OccurrenceJudge.noDetectedFormat`, which gives the reason and tells `FailuresInARow` there was no evidence.
- **It is on the review list** (ADR-175 §7), being an `EXTRACTION_FAILED` verdict under the run, and the page's sentence about what left nothing stored names it (§8.2).
- **It is asked about again by a new stage-2 run, and not by a resume.** A resume keeps it, as a recorded occurrence (ADR-181 §1). Raising `extractionAttempt` mints a run that reads every survivor (ADR-185 §2): the file has no cache row, so it is hashed and sent again.
- **In the reader**, where nothing can be marked, `ConversionDispatch` files the occurrence in `PendingConversions` as one that could not be read, with the cause, and dispatches no call. The processor finds it there, before it hashes anything, and gives it the verdict on its turn, so the drain's order is unchanged (ADR-140 §2).

### 6. What does not change

- **An `Error` is caught by nothing** (ADR-207 §3). `AnErrorWhileHashingStopsTheStepInvocationTest` holds it, unchanged.
- **Seed extraction**: ADR-155, unchanged.
- **The broken check's verdicts and reasons**, the control conversion, the breaker and the count of five for every case they counted before, and the review list's other rows.

### 7. Which run ids move, and what is kept

The change that implements this touches three modules under `src/main`, read against `StageModules` (ADR-058):

- **`corpus`**: `ContentIdentityResolution`, `HashingProgress`, `BrokenCheck`, `BrokenOrOutOfScope`, `CheckingProgress`. **Stage 1 moves**, and every later stage through its upstream run (ADR-048).
- **`extraction`**: `OccurrenceJudge`. **Stage 2 moves**, and stages 3, 4, both runs of stage 5, 6a and 6b, which all name `extraction`.
- **`pipeline`**: the check, the exception, `ByteLevelReductionTasklet`, `ConversionDispatch`, `PendingConversions`, `ExtractionItemProcessor`, `ExtractionHealthCheckListener`, `StepFailure`, `ReviewListReport`. Stages 3 to 6b, already moved.

So **every stage's run id from 1 to 6b moves once**; the census records a walk, which has no implementation version. **No `ConfigConsumed` record and no module list changes**, so `RunIdentityGoldenTest` is not edited. **No schema version moves**: no table, column or index changes.

**An existing working directory is kept**, and every stage runs again over it, as after ADR-207: stage 1 reads the archive again (the broken check, and a hash of every file sharing a length), stage 2 finds every conversion in the extraction cache under the same keys, and every later stage replays. A build that ships this beside another change moving the same ids costs one replay for both.

### 8. What this amends, sentence by sentence

Earlier records are not edited. Each correction is made here.

**8.1 ADR-206 §7**, under *Gone, because what they reported cannot happen*: *"`could not hash <path>` as the failure of 5c, 5d, 5f or 6a. Stage 2 can still fail on it, for a file stage 1 left unhashed."* **Read instead**: its second sentence is withdrawn. Stage 2 no longer fails on it: a file it cannot hash earns `extraction-failed` with a reason beginning `could not be read: `, and the step goes on, unless the corpus root can no longer be listed (§2). Seed extraction still catches it under ADR-155.

**8.2 ADR-175 §7**: *"a file whose reason begins with `rejected`, `crashed the converter`, `timeout`, `capacity`, `target_unavailable` or `internal` left nothing stored, so the next run of the stage asks the converter about it again."* **Read instead**: *"a file whose reason begins with `could not be read`, `rejected`, `crashed the converter`, `timeout`, `capacity`, `target_unavailable` or `internal` left nothing stored, so the next run of the stage asks the converter about it again."* The page says, word for word:

> A file whose reason begins with could not be read, rejected, crashed the converter, timeout, capacity, target_unavailable or internal left nothing stored, so the next run of this stage asks the converter about it again.

`ExtractionWhenTheSidecarDropsItsConnectionTest.theFilesExtractionCouldNotReadAreListedForReview` pins it, moved with this record.

**8.3 ADR-207.**

- **§4's title**, *"unchanged here, and not yet what the operator's rule asks"*, and its table's rows for stage 2 (*"the step fails on `could not hash <path>`, exit 1"*) and stage 1 (*"the step fails, exit 1, and the whole stage's work is rolled back"*): historical. They are what happened at `251ee64` and at `6eb88a2`, and are replaced by §4 and §5 here. Its three bullets of what was to be decided are decided by §2 to §5.
- **§4**, *"Until then a locked or vanished file stops stage 1 or stage 2 as it did, with the file named, and the remedy is ADR-152's: release it, or let the next walk observe that it is gone, and run the same command again."* **Read instead**: such a file is marked, or left for stage 2, and the step goes on; to have it read again, release it and raise `extractionAttempt` (ADR-185).
- **Consequences**, *"A file that cannot be read still stops stage 1 and stage 2, which is not what the operator's rule asks, and is stated here as open (§4)."* Settled by this record.

**8.4 ADR-184 §4's table gains one row**, and its rule above the table stands, since this is not evidence that the converter answers:

| The occurrence on the drain | ADR-071's breaker | ADR-175 §3a's count |
|---|---|---|
| Could not be read: its file would not hash, or would not open when its call lost its connection | leaves it as it is | leaves it as it is |

**8.5 ADR-206 §2's table gains one row**, and its rule, that the occurrences with a key row are exactly those with a metric row, stands:

| What stage 2 got for the occurrence | Metric row | Key row | Cache row (ADR-183) |
| --- | --- | --- | --- |
| nothing: its file could not be read, so no call, or no call that reached the converter | no | no | no |

## Alternatives refused

- **ADR-155's shape: record no completion while a file cannot be read, so the step is asked again.** It is right for a seed, whose absence moves every score. A corpus file is one candidate among many, and its absence moves nothing else; holding the whole stage open for it would stop every later stage until it opened. The operator chose ADR-175's rule. Its cost, accepted: a file that was only locked stays removed under a finished run until a new stage-2 run asks again (ADR-185).
- **`broken` at stage 1 for a file it cannot hash.** §4.
- **One root check per step.** A disk that drops out partway is not seen.
- **A count of files in a row that could not be read**, as ADR-175 §3a counts drops. §2.
- **Waiting for the sidecar and asking again about a file that does not open.** The converter was never the problem. The wait is up to 190 s (ADR-175 §2) for nothing, and the second drop would mark the file as the converter's doing.
- **An extraction fault, resolved when the step completes.** §5: a fault is the converter's refusal.
- **`corpus` checking the root itself in stage 1.** It could, and it would be a second copy of the check, its message and its advice; stage 2 needs `pipeline`'s copy in any case.

## Consequences

- **One file that cannot be read no longer stops stage 1 or stage 2**, and no longer costs stage 1 the work it had done.
- **An archive that goes away stops the step at the next file that cannot be read**, in stage 1's hashing, its first pass and stage 2, with a line naming the corpus root and saying to reconnect it. Where it stopped nothing before (stage 2's call) or marked every remaining file (stage 1's first pass, stage 2's call), it now removes nothing, apart from the one file §2's exception can leave marked.
- **A file gone after stage 1 hashed it is no longer taken for a converter that drops connections**, so it costs no wait, no second call and no place in a row of five.
- **Every stage's run id from 1 to 6b moves once**, and the working directory is kept (§7).
- **`AGENTS.md`** counts this record, and says the defect it decides is decided and not yet shipped until the implementation lands.

**What the implementation owes** (`spec-implementer`):

- the check and its exception in `pipeline`, with §2's message, built in stage 2 from `StageRuns.canonicalRoot()` and in stage 1 from `ByteLevelReductionTasklet`'s own root, canonicalised as the tasklet already does; and `StepFailure` recognising the exception beneath the framework;
- `ExtractionHealthCheckListener.afterStep` ending stage 2's closing line with `Reconnect the archive and run the same command again.` where `StepFailure` recognises the exception, in place of the advice about docling-serve, and its class javadoc;
- stage 1's ERROR line of §2, in `ByteLevelReductionTasklet`;
- `HashingProgress`'s and `CheckingProgress`'s new default methods, `ContentIdentityResolution`'s catch, `BrokenCheck.Result`'s flag on exactly the four catches of §3, and `BrokenOrOutOfScope` telling `CheckingProgress` before the verdict, in the order ADR-188 §2's sentence on the order within one survivor states once §4 extends it;
- `ByteLevelReductionTasklet`'s two implementations: the root check, the WARN line and the counter;
- `OccurrenceJudge`'s method for `could not be read`, telling `FailuresInARow` there was no evidence;
- `ConversionDispatch` and `PendingConversions` filing an occurrence that could not be read; `ExtractionItemProcessor` finding such an occurrence in `PendingConversions` before it hashes anything (today it looks up only a key there, and would hash the file again), and acting at its own hash and at the first lost connection, the root check and the opening check before `SidecarRecovery.awaitHealthy`;
- the constructors the tests build, kept as they are: `ExtractionItemProcessor`'s ten-argument one, which `ExtractionItemProcessorTest` calls, and `ByteLevelReductionTasklet`'s;
- `ReviewListReport`'s sentence of §8.2, and the class javadoc that lists the same reasons;
- the javadoc that states the old rule: `ContentIdentityResolution`, `HashingProgress`, `CheckingProgress`, `BrokenCheck.check`, `ConversionDispatch`, `ExtractionItemProcessor`'s class javadoc and `retryAfterDrop`'s;
- `AGENTS.md`'s paragraph on the open defect, which says ADR-210 is decided and not yet built: rewritten in the same change that ships the code, to say #452 is closed by ADR-210.

**What pins it.** Each test below was run against `6eb88a2`. Every one fails there for the reason given, except the two marked green, which pass there and must still pass after.

- `pipeline.AFileThatCannotBeReadIsMarkedInvocationTest`, five tests, through the extraction double's `beforeHashing` seam and two seams of the test's own on `corpus`'s content-identity and detected-format records. Each seam acts once per arming, the first time it is reached, so a step that reaches it again meets what the first time did and nothing more:
  - a file moved away as stage 2 hashes it: stage 2 completes with nothing set aside, the file alone is marked with `could not be read: `, has no metric, key, fault or cache row, is not counted by the breaker, and is on the review list. *Red: the step fails on `could not hash`.*
  - a file deleted as stage 1 hashes the files of its length: stage 1 finishes, its content-hash counter reads `1 of 2` and `2 of 2`, the file has no hash and no stage-1 verdict, and stage 2 marks it `could not be read: `. *Red: stage 1 fails and rolls back.*
  - the folder gone as stage 1 hashes, and as stage 1 checks: the invocation fails, stage 1's line says it failed, names the root as one that can no longer be listed and says to reconnect, stage 1 is not finished, and no verdict is written. *Red: the line is not there; in the first pass, stage 1 finishes with every later file `broken`.*
  - the folder gone as stage 2 hashes: the invocation fails, stage 2's closing line names the root and says to reconnect, stage 1 is finished and stage 2 is not, and nothing is marked. *Red: the line names `could not hash` and the converter.*
- `pipeline.AFileGoneBeforeItIsSentInvocationTest`, four tests, with the production client against `LoopbackSidecar`, which gains a hook run before each health check and a count of them:
  - five files gone after stage 1 hashed them, read in a row, with the control conversion dropped: stage 2 completes, each is marked `could not be read: ` and none `crashed the converter`, no control conversion is posted, the health check is asked once, and the file read last is posted once and kept. *Red: the five are a row of five and the step stops.*
  - a gone file read third among five that drop every call: the step still stops at five in a row, and the line naming the five names the five that dropped and not the gone file. *Red: the gone file is among the five.*
  - a gone file read third among five refused for `capacity`, the control conversion refused too: the breaker's stop. **Green against `6eb88a2` and after**: a file marked `crashed the converter` leaves the breaker as it is, and so does one that could not be read. It is there so that an implementation that counted the file as an answer fails.
  - the folder gone just before stage 2 places its first call: the invocation fails, the closing line names the root and says to reconnect, nothing is marked, and the health check is asked once. *Red: every file is marked `crashed the converter` and the step completes.*
- `pipeline.ExtractionItemProcessorTest`, two tests at the processor's own hash, which the job never reaches: a file gone is marked `could not be read: `, nothing is sent and nothing measured or keyed; the folder gone stops the processor with a failure naming the root. *Red: `could not hash` leaves the processor.*
- `pipeline.ExtractionWhenTheSidecarDropsItsConnectionTest.theFilesExtractionCouldNotReadAreListedForReview`, moved to §8.2's sentence. *Red: the page carries ADR-175's.*
- `pipeline.AnErrorWhileHashingStopsTheStepInvocationTest`, unchanged and **green**: §6.

**Not pinned, and why:**

- a file locked rather than gone. Holding a lock needs a second handle that excludes readers, which only Windows gives; gone and locked reach the same catches;
- the zip and PDF catches of the broken check, one by one: the first-pass test's folder is gone, so the size read fails first;
- the WARN line of §4 and the exact wording after `could not be read: `;
- the timeout streak's half of §5's "no evidence": no test here arranges timeouts around a file that cannot be read;
- a text file converted in parts (ADR-178) that cannot be read when its call is placed. `TextParts.convertInParts` reads the whole file with `Files.readAllBytes`, on the worker, before it posts any part, so a file gone after it was hashed, by stage 1 or by stage 2's reader, fails there. That `IOException` leaves as an `UncheckedIOException`, not a lost connection, and is none of §3's sites, so as this record is written it stops the step as at `6eb88a2`; that is read from the code and not run. No test covers it: every file the tests here take away is small enough to be posted whole;
- §7: nothing in a test can see a commit.

## What this does not decide

- **A file that would not open at stage 1's first pass is sealed `broken` under a finished run** (§4), a file that was only locked included. Releasing it does not bring it back: the next invocation arrives at the same stage-1 run, and ADR-185's way of asking again starts at stage 2. Telling a lock from damage there, or a stage-1 attempt like `extractionAttempt`, is a follow-up ticket's to decide; none is filed by this record.
- **A disk half dead**: the root lists while reads of files fail. Every such file is marked `could not be read` and the step completes. The bound does not see it, and the review list is where the operator does.
- **A disk lost while stage 2 waits for the sidecar after a first lost connection.** §3's root check and opening check run before `SidecarRecovery.awaitHealthy`, which can wait up to 190 s (ADR-175 §2), and nothing checks either again after the wait. A disk lost during it fails the second call, which the client reports as a second lost connection, so the file is marked `crashed the converter` and counts towards ADR-175 §3a's row, for the archive having gone and not for anything the converter did. Whether that mark stays depends on whether the chunk it is in commits before a later file meets the root check and stops the step. The operator decided to leave this open: no second root check, after the wait, is added by this record.
- **A corpus root gone before a step sets itself up**, measured in Context: stage 2, and every later stage, builds its run holder from the canonical root, and fails there with a bean-creation message that names neither the root nor what to do. Nothing is removed, so it is safe; whether that line should be §2's, and whether stages after 2, which since ADR-206 open no archive file, should need the root to exist at all, is not decided here.
