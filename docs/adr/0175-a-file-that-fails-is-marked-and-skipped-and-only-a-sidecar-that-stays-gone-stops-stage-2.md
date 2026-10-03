# ADR-175 — A file that fails is marked and skipped, and only a sidecar that stays gone stops stage 2

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md). *"A dead sidecar already fails the call itself, which the skip/breaker machinery below already handles"* held only for a sidecar that answers with a service-scope failure, or answers nothing within the call timeout. A sidecar that closed the connection failed the step on the first such call, and the breaker never counted it (Context). From this record a dropped connection is waited out and the call placed once more (§2), and a sidecar that does not come back, or that goes on dropping, stops the step by a failure of its own (§3, §3a). ADR-071's timeout, its two streaks and its breaker are unchanged (§4).
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md). *"A dead sidecar still trips, and fast"* was false for a dropped connection, for the same reason. What stops the step for a sidecar that drops connections is now §3's bound and §3a's count, not the breaker.
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md). It gave a conversion the converter refused two outcomes: a verdict, or an extraction fault. An HTTP error status was neither, and failed the step. It is now a verdict (§1).
- **Rests on**: [ADR-155](0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md) (a problem with one seed is recorded under a reason of its own), [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md) (the sidecar restarts by itself, which is what makes waiting worth it), [ADR-172](0172-docling-serves-synchronous-wait-outlasts-vesperas-call-timeout.md) (docling-serve's own wait), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) (a committed verdict is final within its run, and a report about stage 2 reads the ledger), [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) (what the extraction cache keeps). [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md) and [ADR-168](0168-videos-are-out-of-scope-and-stage-1-recognises-one-by-its-container-signature.md) are the two stop-gaps this record makes unnecessary as a defence, though both stand.
- **Settles** [#326](https://github.com/algernon28/vespera/issues/326), points 2 to 5. Point 1, the restart policy, is ADR-164.

## Context

### The operator's rule

> A file that fails should not stop the entire run. Skip it with a mark, and report it for review at the end.

A problem with one file fails that file, never the run. Only a sidecar that is really gone stops the run.

### What happened on the whole archive on 2026-09-28

- **09:56.** docling-serve was OOM-killed on one 29 KB BMP that declares a resolution of 1 pixel per metre, so that its 95 pixels become a page about 3,740 inches wide. `docker events` showed `oom`, then `die exit=137`. The call in flight got `HTTP/1.1 header parser received no bytes`, and stage 2 failed on that first dropped call (read=1328, written=631, filtered=683). The restart policy had the container back in 6 to 9 seconds. Sent alone to a freshly restarted sidecar, the same file ran for 64.7 s and killed it again: running the command again would have stopped at the same file every time. ADR-167 took BMP images out of scope. That unblocked the archive and fixes nothing: a PNG declaring the same resolution killed the sidecar the same way.
- **12:03.** Stage 2 failed on `404 Not Found: {"detail":"Task result not found. Please wait for a completion status."}`. Docling had tried to transcribe two MP4s, and each job failed inside docling-serve (`whisper is not installed`). The synchronous `/v1/convert/file` endpoint answers a job that failed in its worker with HTTP 404, not with `status: failure`. `DoclingClient.convert` let the `HttpClientErrorException` escape. It is not a `ServiceScopeFailureException`, so the skip policy rejected it and the step failed. ADR-168 took videos out of scope.
- **16:57.** Stage 2 failed on HTTP 504, `Conversion is taking too long`, docling-serve's own synchronous wait running out on a 161-page PDF. ADR-172 raised that wait past Vespera's call timeout in `compose.yaml`.

### What a dropped connection did before this record, measured

A test ran stage 2 over 40 files, a chunk of 16 and 8 calls in flight, with the production `DoclingClient`, extractor and cache against a loopback HTTP server that answered the first 20 conversions and then read each request and closed the socket. It is kept on the branch `wip/dropped-connection-draft`.

- 32 calls were sent: 20 answered, 12 dropped. Twelve is more than the breaker's five in a row. The breaker did not trip.
- The step failed on the first dropped item it processed. The skip count was 0, and no `extraction_fault` row was written.
- The failure chain was `FatalStepExecutionException`, `NonSkippableProcessException`, `ResourceAccessException`. `ExtractorStoppedAnsweringException`, the breaker's, was not in it.
- The second invocation, with the sidecar answering, finished the step, and paid again for the 4 conversions the failed chunk had already been answered for.

So stage 2 and seed extraction failed the same way, on the first dropped connection, and the sentences this record amends in ADR-071 and ADR-140 described nothing the code did.

## Decision

### 1. An HTTP error status on one file's call is a verdict against that file

Any 4xx or 5xx from `/v1/convert/file`, with the one exception below, becomes `DoclingCallRejectedException` in `DoclingClient.convert`. In stage 2 the occurrence earns `extraction-failed` with the reason `rejected: docling-serve answered HTTP <status> for <file>: <body, cut to 300 characters>`, and the step goes on.

- **It is not cached.** The failure is an exception, and only an answered call reaches `DoclingExtractor.remember`.
- **The breaker does not count it as a failure.** It is a completed item, so it ends the breaker's run of service-scope skips as any completed item does (`ExtractionCircuitBreaker.afterProcess`), and it resets the timeout streak and §3a's count, as a judged response does.
- **No metric row is written**, because no response came back to measure. That is the client's own timeout's shape (ADR-071), so ADR-181 §1's definition of a recorded occurrence already covers it: it carries an `EXTRACTION_FAILED` verdict under the run.

**The exception is HTTP 504 whose body contains `taking too long`.** That is docling-serve's `DOCLING_SERVE_MAX_SYNC_WAIT` running out. It is a timeout, not a rejection, and becomes `DoclingCallTimeoutException`, read through ADR-071's streak exactly as the client's own silence is. Its message says which it was — `docling-serve gave up on <file>: its own wait for a conversion ran out before an answer was ready` — because the two point at different settings. A 504 with any other body is a rejection: a proxy in front of the sidecar answers 504 for reasons of its own. ADR-172 makes this case rare under `compose.yaml`, where the sidecar's wait is twice Vespera's. The rule is kept for a sidecar started any other way.

### 2. A dropped connection waits for the sidecar and asks about the file once more

A `ResourceAccessException` that is not a timeout — connection refused, reset, or closed with no status line — becomes `DoclingConnectionLostException`. The step then:

1. waits for `/health` to answer, asking every 2 s until 180 s have passed (`SidecarRecovery`);
2. places that file's call once more, synchronously, on the step thread, through the cache.

If the retry answers, the answer is judged like any other, a service-scope failure included: that one is skipped and counted by the breaker as always. If the retry is rejected, or times out, §1 and ADR-071 apply to it. **If the retry drops the connection too**, the sidecar is waited for again, so that the next files find it up, and the file earns `extraction-failed` with the reason `crashed the converter: docling-serve dropped the connection twice while converting this file: <what the transport reported>`. It is not cached, and the step goes on, unless §3a stops it.

**A dropped connection is counted by neither of ADR-071's streaks.** The first drop touches nothing. The retry is counted once, by whatever it returns: a timeout by the timeout streak, a service-scope answer by the breaker. A file removed for dropping twice is a completed item, so, like a rejection, it ends the breaker's run of service-scope skips; it does not touch the timeout streak.

**Each health check waits at most 5 s for its answer** (`DoclingClient.HEALTH_CHECK_TIMEOUT`), on a client of its own. Asked through the conversion client it would wait the 5-minute call timeout on a sidecar that accepts the connection and says nothing. One check is made before the clock starts, and one can be in flight when the bound passes, so the wait ends no later than 5 + 180 + 5 = 190 s.

**180 s, 2 s and 5 s are code defaults, not profile values**, on ADR-071's reasoning for its own numbers: they say how long a machine takes to restart a container, not what an operator has judged about a corpus. The measured restart took 6 to 9 seconds; three minutes also covers a sidecar that reloads its models.

### 3. A sidecar that does not come back stops the step

If `/health` has not answered when the bound has passed, `SidecarRecovery` throws `DoclingDidNotComeBackException`. It is not skippable, so the step fails, and the chunk it failed in rolls back. Its message is what stage 2's closing line names, through `StepFailure.named`:

`docling-serve dropped the connection and did not answer its health check again within 180 seconds`

No file is removed on the strength of a sidecar that is gone.

### 3a. A sidecar that answers its health check and drops five files in a row stops the step

A sidecar can answer `/health` and still drop every conversion. §2 alone would then remove every file as `crashed the converter` and let the step complete: each wait returns at once, and each such verdict is a completed item, so the breaker never sees a streak. The measurement in Context is that case: with it, 20 files would have been removed and the invocation would have exited 0.

So the processor counts the file occurrences that drop the connection twice **in a row on the drain**. What ends the run of them, stated once:

- **A response the processor judges ends it**: a conversion, or a failure the converter reports in a response, a `timeout` it reports there included. So does an occurrence answered from the extraction cache, for which no call is made.
- **An error status ends it** (§1).
- **A call that timed out does not**: the client's own silence, or docling-serve's wait running out (§1's 504). Neither brought a response to judge, which is also why ADR-071 counts both in its timeout streak.
- **An occurrence with no detected format does not**: no call is made for it and nothing is judged.

The count starts at zero on a resumed step, as ADR-071's two counters do (ADR-181 §4).

On the fifth, `ExtractionItemProcessor` throws `DoclingKeepsDroppingConnectionsException`, which is not skippable, so the step fails and its chunk rolls back:

`docling-serve dropped the connection twice on each of 5 files in a row while still answering its health check`

**Five is ADR-071's breaker's count**, so a sidecar that converts nothing stops the step as soon whichever way it fails. It is a code default for the reason §2 gives. The operator decided on 2026-10-03 to bound it here and not to record it as an accepted consequence.

**What it leaves, accepted:**

- **Up to four files stay removed.** The count is over the drain, so it survives a chunk boundary, and up to four files removed as `crashed the converter` in a chunk that committed before the fifth stay removed under that run. The chunk the fifth is in rolls back, so none of its files is removed. That is ADR-071's own shape: the timeouts before a streak's limit stay verdicts. The review list (§7) shows them.
- **A cache hit ends the run of them**, so on a corpus that is partly cached, a sidecar that converts nothing is stopped only where five uncached files sit side by side on the drain. Until then each uncached file costs two calls and is removed. Not counting a hit would make five real killers separated by cached files look adjacent, which is the next case made worse.
- **Five adjacent files that each really kill the sidecar stop the step on every invocation.** The count cannot tell them from a sidecar that converts nothing. The drain is in the order the walk recorded the files, and the walk goes through a folder's entries before it leaves the folder, so files of one folder are usually adjacent: a subfolder met among them, or a file stage 1 removed, can sit between two of them. The archive's evidence has such a folder: `cir.bmp` and `sodexo.bmp` sit together, and 90 of its 410 BMPs declare the fatal resolution. A resume starts the count at zero, meets the same five and stops again, which is #326's original failure for a cluster of five where it was one file. ADR-167 and ADR-168 keep the two known kinds away from the converter; a PNG declaring the same resolution is not kept away. **What the operator does today**: the stop names the five. Beside the closing line, one ERROR line lists them by their paths under the corpus root: `Stage 2 (extraction): these 5 files in a row each dropped the connection twice: <paths>`. Nothing else would: the chunk they are in rolls back, so none of them reaches the review list. Moving them out of the corpus root, or taking their kind out of scope as ADR-167 did, gets the step past them. Telling the two cases apart is under "What this does not decide".

### 4. Timeouts and the breaker are unchanged

An isolated timeout is `extraction-failed`; three in a row are service scope; five consecutive service-scope failures on the drain trip the breaker (ADR-071, ADR-140 §2). **The breaker still stops a step whose sidecar answers every call with a failure it blames on itself, or answers none within the call timeout.** What it never did, and still does not, is count a dropped connection. A sidecar that drops connections stops the step by §3 when it also stops answering its health check, and by §3a when it does not.

### 5. Each call in flight when the sidecar dies retries alone, and one of them can be blamed wrongly

Up to eight calls are in flight (ADR-140). When the sidecar dies each gets its own dropped connection, and each waits and retries alone, one after another, as the step thread reaches it. **Accepted:** an innocent file's retry can overlap, on a worker thread, the call of the file that really kills the sidecar, drop a second time, and be marked as having crashed the converter. That needs a call still in flight while the step thread retries, which is the case of one connection dropped while the sidecar stays up for the rest, or of a sidecar killed again by a file dispatched later in the same chunk. Nothing is cached for the file wrongly blamed, so the next stage-2 run asks about it again, and the review list (§7) shows it with its reason meanwhile.

### 6. Seed extraction waits and retries the same way, and a seed that still fails stops the step

`SeedExtractionItemProcessor` waits for the sidecar and asks once more after a dropped connection, through the same `SidecarRecovery`. A seed is an input the operator chose, and no verdict is ever written against one (ADR-083), so a seed that is rejected, or drops the connection twice, still stops the step, as before. The failure names the seed: `seed <path> could not be converted: <reason>`. §3a has nothing to count here: the first seed that drops twice stops the step.

### 7. Stage 2 writes the review list of files it could not read

When stage 2 ends, `ReviewListListener` writes `extraction-failures.html` beside the database: one row per `EXTRACTION_FAILED` verdict under the stage-2 run, with its path relative to the corpus root and the verdict's reason, in path order. It logs `Stage 2 (extraction): N file(s) could not be read; they are listed in <absolute path>`. With none, the page says no file failed and the line says 0.

- **It reads the ledger under the run** (`Ledger.extractionFailures`), as ADR-181 §5 requires, so a resumed stage lists what earlier invocations of the run removed too, and an invocation that finds the stage already finished writes the page again.
- **It runs after `ExtractionFaultRecorder`**, so the verdicts that listener resolves at the end of a completed step are on the page. Spring Batch runs `afterStep` in the reverse of registration order, so it is registered before `ExtractionHealthCheckListener`.
- **A step that failed having read nothing writes no page.** Its health check failed, or the sidecar would not say what it is, so the survivors reader never asked for the run. Asking for it at the end of the step would call the sidecar again and mint a run behind the failed check, which #319 forbids. #326 asked for the page "every time stage 2 ends, completed or failed"; this is the one case it is not written in.
- **A page that cannot be written is said in one ERROR line and stops nothing.** Spring Batch only logs what an `afterStep` throws, and skips the listeners after it, so throwing would cost the breaker's own `afterStep` and change no exit code. The verdicts are in the ledger either way.

**The page does not say what #326 asked it to say.** The ticket's sentence was *"Running the same command again asks about these files again."* That is false. A finished stage 2 reads nothing under the same run (ADR-115), and a resumed one keeps the verdicts its committed chunks wrote (ADR-181 §1). The page says what is true, and only of the rows it is true of: each file was marked and skipped, they stay removed under this run, and a file whose reason begins with `rejected`, `crashed the converter`, `timeout`, `capacity`, `target_unavailable` or `internal` left nothing stored, so the next run of the stage asks the converter about it again. A failure the converter blamed on the document is stored (ADR-183) and is not asked about again; the page promises nothing for it. How an operator makes a new stage-2 run on purpose is [#386](https://github.com/algernon28/vespera/issues/386), which this record does not decide.

**Names.** `CONTEXT.md` calls the page the *review list*, and the two classes carry that name. `Ledger.extractionFailures` returns `RemovedOccurrence`, a record that names no verdict kind. #326 prescribed `verdictsOf(RunId, VerdictKind)` returning `FailedOccurrence`: a method generic over the kind returning a type named for one of them, beside "failed document", which `CONTEXT.md` rejects.

## Consequences

- **One file can no longer stop stage 2.** The three failures of 2026-09-28 would have cost four files between them: the BMP would be `crashed the converter`, each of the two MP4s `rejected`, and the slow PDF a `timeout`.
- **A sidecar restart inside a step costs the wait and one repeated call per file in flight**, not the invocation and the operator's attention.
- **A file that kills the sidecar costs two crashes and two restarts**, about 150 s on the measured BMP (64.7 s to the kill, twice, and two restarts), and is then removed. A new stage-2 run pays that again, because nothing is cached for it. ADR-167 and ADR-168 keep the two known kinds from reaching the converter at all.
- **The step thread waits while the sidecar is down**, and nothing else is drained meanwhile. Calls already dispatched go on, on their workers.
- **`extraction-failures.html` joins the files written beside the database**, and the README lists it.
- **`extraction`'s implementation version moves** (ADR-058), because `DoclingClient` changes and two exception types are added there. Every stage-2 run id moves with it, so the first invocation of the build that ships this replays stage 2 over cached conversions, and every stage after it. ADR-183 §3 records the operator's decision to ship #382, #384 and #387 in one release so that they cost one replay. Shipped in that release, this record adds none. Shipped in a later build, it costs a second.
- **`CONTEXT.md` gains *Review list*.**

## Tests

- **`DoclingClientTest`**: an error status is a rejection carrying the status and the body, with the message cut to 300 characters; a 504 saying `taking too long` is a timeout whose message says the sidecar gave up, and a 504 saying anything else is a rejection; a transport failure that is not a timeout is a lost connection; a health check against a server that accepts the connection and never answers returns not healthy on its own short clock.
- **`SidecarRecoveryTest`**: a sidecar that already answers is asked once; one that fails three checks and then answers is waited for; one that never answers ends the wait in `DoclingDidNotComeBackException`, whose message carries the seconds.
- **`ExtractionWhenTheSidecarDropsItsConnectionTest`** drives whole invocations with the production client against `LoopbackSidecar`, a real HTTP server on a loopback port:
  1. A 404, and a 500, on one document: it is removed with a reason starting `rejected: docling-serve answered HTTP <status>`, the step completes, the other documents are converted, no cache row exists for it, and the breaker counted nothing.
  2. One dropped connection, then a retry that answers: the document is posted twice and kept, nothing is skipped, and the breaker counted nothing.
  3. A retry answered with an error status, a retry answered `taking too long`, and a retry the converter refuses for want of capacity: the document is removed as `rejected`, as a `timeout`, and as an extraction fault resolved at the end of the step. In the timeout case the document read just before it timed out too, in the order the stage reads, which the test learns first; so a dropped call counted as a timeout would make three in a row and set the second aside. It is not.
  4. A document whose every call drops: it is posted twice and no more, removed with a reason starting `crashed the converter:`, and the step completes.
  5. A drop after which the health check never answers: the step fails, the closing line says the converter did not answer its health check again within the bound, and no document is removed.
  6. Every call dropped while the health check answers, over six documents in one chunk: the step fails on the fifth, the closing line says so, a line names the five by path and not the sixth, and no document is removed. Four documents that drop twice, one that converts, and four more, in the order the stage reads them, which the test learns first because it is the file system's and not the names': the step completes. The same with the one between them answered `taking too long`: the step stops, because a timeout does not end the run.
  7. The review list: it lists a rejected document with its reason and the log counts 1; after a clean stage it says no file failed and the log counts 0; a document set aside as an extraction fault and resolved at the end of the step is on it, which fails when the listener is registered after the fault recorder, checked by registering it there; a document removed at the end of the step is listed before one removed on its own turn, because the page is in path order; and a second invocation that finds the stage finished writes the page again with both.
- **`SeedExtractionWhenTheSidecarDropsItsConnectionTest`**: a seed whose call drops once is posted twice and seed extraction completes; a seed answered 404 fails the step, and the operator's line names the seed and the status.
- **`PendingConversionsTest`**: a dispatched call that was rejected, or lost its connection, reaches the step as that same exception, and nothing is handed on to be stored.
- **ADR-071's breaker tests are untouched and pass.**

Not pinned by a test: the ERROR line for a page that cannot be written, and `Ledger.extractionFailures` at the ledger's own seam. Its query is held through the invocation tests above.

## What this does not decide

- **How an operator asks again about the files on the review list** without waiting for a new build or changing a floor: [#386](https://github.com/algernon28/vespera/issues/386).
- **A resume that meets five repeated refusals back to back**: [#385](https://github.com/algernon28/vespera/issues/385), untouched.
- **Telling the file that killed the sidecar from the files in flight beside it** (§5). It would need calls placed one at a time after a crash, which costs the width ADR-140 bought.
- **Telling five files that each kill the sidecar from a sidecar that converts nothing** (§3a). A probe conversion of a known-good document after the fifth would tell them apart; nothing sends one. Until a record decides it, a cluster of five such files stops stage 2 until the operator moves them.
- **Whether the bounds should be configurable.** They are code defaults until a measured restart says otherwise.
- **Whether a locked database file met while the page is read should fail the invocation.** [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) keeps a second invocation out of the working directory and names a database file SQLite reports locked. It does not decide what one met in an `afterStep` does, and neither does this record: Spring Batch only logs what an `afterStep` throws, so the step's status would stand.
