# ADR-184 — Five failures in a row stop stage 2 only when the converter then fails a control conversion

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md), its breaker. *"Failing the step once the streak crosses 5"* becomes: once the streak reaches 5, stage 2 sends a control conversion, and fails the step only if that does not convert (§2). *"Resets to zero on any successful conversion"* is restated by §4. What ends the streak is an answer the converter gave about a file during this invocation. An answer read from the extraction cache no longer ends it, and neither does an occurrence with no detected format, a timeout that brought no response, or an occurrence that dropped the connection twice.
- **Amends**: [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §3a, the same way for its count of file occurrences that drop the connection twice (§2, §4). Two of its accepted consequences are replaced by §5 and §6: *"A cache hit ends the run of them"* and *"Five adjacent files that each really kill the sidecar stop the step on every invocation"*. Two of its "What this does not decide" entries are settled here: #385, and telling five such files from a sidecar that converts nothing.
- **Amends**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §4, third bullet. It says the faulted occurrences a resume reads again reach the breaker *"in read order … as it would have done in an uninterrupted invocation"*. They do not: they reach it back to back (Context). §5 replaces that bullet. The first two bullets of ADR-181 §4 stand, and so does ADR-181 §1's read order.
- **Amends**: [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §2, first bullet. *"A dead sidecar still trips, and fast"*: it still trips, after the wait and the one call §2 adds (§6).
- **Rests on**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §3 (a fault is resolved because the converter answered for others), ADR-140 §2 and §3 (the drain, and the one thread the step runs on), [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md), [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) (what the cache keeps, and that a refusal is asked again), ADR-181 §2 (a resume finds its place in the ledger).
- **Settles** [#385](https://github.com/algernon28/vespera/issues/385) and [#393](https://github.com/algernon28/vespera/issues/393). They are one question, so they get one record: two counts read "five in a row" as the sidecar's doing when it can be the files' doing.

## Context

### Two counts stop stage 2 for the converter's sake

- **ADR-071's breaker** counts service-scope failures in a row: `capacity`, `internal`, `target_unavailable`, and a timeout ADR-071's streak has flipped. At five it stops the step.
- **ADR-175 §3a's count** covers a sidecar that answers its health check and drops every conversion. It counts file occurrences in a row whose call dropped the connection twice. At five it stops the step.

Both read five failures in a row as a converter that converts nothing. That is right when it is true. Two tickets found where it is not.

### #393: five files that each kill the converter

Stage 2 reads in the order the walk recorded the files, so the files of one folder are usually adjacent. The archive has a folder with 410 BMPs, 90 of which declare the resolution that OOM-kills the sidecar (#326's evidence, 2026-09-28). ADR-167 keeps BMPs away from the converter. A PNG declaring the same resolution killed it the same way, and nothing keeps that away. Five such files side by side stop the step. A resume starts the count at zero, meets the same five, and stops again. Each of them should have been marked `crashed the converter` and skipped, which is the rule #326 exists for.

### #385: five repeat refusals read back to back on a resume

ADR-181 §1 resumes a stopped stage 2 by reading the survivors that carry no `extraction_metric` row under the run. `Ledger.survivors` orders by occurrence id. After a stop partway through, the faulted occurrences of the committed chunks carry lower ids than the unread rest, and have no metric row. So the resume reads them first, one after another, with none of the converted occurrences that sat between them on the first pass. Since ADR-183 each one reaches the sidecar again. If five of them are files the sidecar refuses in service scope every time, the breaker trips on every resume of that run. The only way out today is a new stage-2 run id.

### What the count cannot know, and what can tell it

Five in a row is a fact about adjacency. Whether the converter still converts is a separate question, and the count does not answer it. ADR-175 named the way to ask: send the converter something whose answer is known.

### What was considered and refused

- **Read the faulted occurrences in their place, or last** (#385's first option). There is no place to put them back into. The neighbours they had on the first pass are recorded and are not read again, so in any order they end up next to each other or next to arbitrary unread occurrences. Reading them last only moves the stop to the end of the stage.
- **Do not count a fault read again** (#385's second option). A resume whose only work is re-read faults would then complete against a sidecar that refuses everything, and ADR-139 would resolve every one of them into `extraction-failed` with nothing to go on. The health check that passed when the step began is not that evidence. ADR-175 §3a's own case is a sidecar that answers `/health` and converts nothing.
- **Leave it to the operator** (#385's third option, and #393's first). For #385 that means minting a new run id. For #393 it means moving files out of the corpus root, and the folder in Context has 90 candidates. The operator's rule from #326 is that a file that fails fails that file.
- **A second, higher bound after a control conversion that converts** (#393's third question). Any bound recreates #393 for a folder holding more killing files than the bound.

## Decision

### 1. What "in a row" means, now and once #369 lands

Both counts are over occurrences **on the drain** (ADR-140 §2). That is the order `ExtractionItemProcessor` takes occurrences in, one at a time, on the step thread. It is the order `ConversionDispatch.read` returns them, which is the order the survivors reader yields them (occurrence id), which is the order the walk recorded them, which is the file system's listing order. **Two occurrences are in a row when the processor takes the second directly after the first in the same invocation.**

- A chunk boundary does not break a row. An invocation boundary does, because both counts start at zero in each invocation (ADR-181 §4).
- [#369](https://github.com/algernon28/vespera/issues/369) dispatches calls up to `LOOKAHEAD` = 16 ids ahead of the one being returned, across chunk boundaries. Its `read()` still returns ids in the delegate's order, and the processor still takes them one at a time on the step thread. So the order and what is counted do not change. What changes is how many calls are already out when the sidecar fails: up to 17 dispatched calls can fail together, so a row of failures can be longer than one chunk before the processor reaches its end.
- Nothing in this record depends on `CHUNK_SIZE`, or on how far ahead calls are dispatched.

### 2. On the fifth, a control conversion decides

When either count reaches five, the step thread does three things before it fails the step:

1. **It waits until no dispatched call is still running.** Every call `ConversionDispatch` has dispatched and the processor has not yet taken must have finished. Their answers are not taken. They stay pending and are taken in read order afterwards, like any other.
2. **It waits for `/health`**, as ADR-175 §2 does, through `SidecarRecovery`. A sidecar that does not answer within the bound stops the step under ADR-175 §3, with that record's message.
3. **It sends the control conversion, alone**, through `DoclingExtractor.convertUncached`, which reads and writes no cache. The PDF it sends is described in §3.

**The control conversion converted** when the answer is a conversion (`ResponseScope.Conversion`) whose text contains the sentence `Vespera control document`. In that case:

- the five failures are the files' own;
- each of the five keeps what it earned: a service-scope failure stays an extraction fault, resolved when the step completes (ADR-139), and an occurrence that dropped twice keeps its `crashed the converter` verdict;
- both counts start again at zero;
- one WARN line says what happened: `Stage 2 (extraction): the converter converted the control document after 5 files in a row failed, so each failure is the file's own and the stage goes on`;
- the step goes on.

**Any other outcome means it did not convert.** That covers:

- a service-scope or document-scope failure;
- a timeout the converter reported;
- a conversion without the sentence;
- an HTTP error status;
- the call timeout;
- a dropped connection.

The step then fails as it does today, and the cause's message gains `, and did not convert the control document either`:

- `the extractor set aside 5 occurrences in a row without converting any of them, and did not convert the control document either`
- `docling-serve dropped the connection twice on each of 5 files in a row while still answering its health check, and did not convert the control document either`

ADR-175 §3a's ERROR line naming the five by path is still written, before the step fails.

**A control conversion that drops the connection is not retried.** Step 1 left the sidecar with nothing else to do, so nothing else can have killed it. The drop is the answer.

**Why step 1 waits.** ADR-175 §5 records that a call in flight can be killed by another file's call. If a sixth killing file were in flight, it could kill the control conversion, and five files that kill the converter would again read as a converter that converts nothing. Waiting makes the control conversion the only call the sidecar is handling.

**A conversion without the sentence does not count.** A sidecar that answers `success` with no text has not read the PDF. The sentence shows that it did.

### 3. The control conversion's PDF

- **What it is.** A one-page PDF with a text layer, shipped at `src/main/resources/io/algernon/vespera/pipeline/control-conversion.pdf`. Its one line of text reads `Vespera control document`. The line is in an **uncompressed** content stream, as the literal `(Vespera control document)`, so the posted bytes carry the sentence. That is how a test recognises the control conversion on the wire.
- **How it is sent.** As `DetectedFormat.PDF` with no subtype. Those are the options stage 2 sends for a born-digital PDF (ADR-100), so it goes through the converter's standard PDF pipeline. That pipeline runs the layout model, on the GPU under ADR-170, which is where a broken converter breaks. A plain-text control would exercise almost nothing. The step materialises the resource as a file it can post. Where it does so is the implementer's choice, as long as it is outside the corpus root.
- **It is not a file occurrence.** No walk found it. It gets no row, no verdict, no metric, no shingle and no fault. It does not move the progress line.
- **It is never cached**, and it does not touch the extractor identity. The identity is made from what the sidecar reports about itself and the options sent, never from what is sent to it.
- **It lives in `pipeline`, beside the two counts**, and not in `extraction`. So it does not move `extraction`'s implementation version (ADR-058), and no working directory replays stage 2 for it. The judging code ADR-181 names under "What this does not decide" is in `pipeline` too. When [#320](https://github.com/algernon28/vespera/issues/320) moves that code into `extraction`, the control conversion moves with it.

### 4. What ends a row, stated once for both counts

**A row is ended only by evidence that the converter answers about files now.** That evidence is:

- an answer to a call made for that occurrence during this invocation, which the processor judges about the file: a conversion, a failure the converter blamed on the document (ADR-143), or a timeout the converter reported below ADR-071's flip;
- an HTTP error status (ADR-175 §1);
- a control conversion that converted (§2).

| The occurrence on the drain | ADR-071's breaker | ADR-175 §3a's count |
|---|---|---|
| A service-scope failure, including a timeout the streak flipped | extends it | leaves it as it is |
| Dropped the connection twice | leaves it as it is | extends it |
| Answered from the extraction cache | leaves it as it is | leaves it as it is |
| No detected format, so no call was made | leaves it as it is | leaves it as it is |
| A timeout with no response, below the flip: the client's own silence, or a 504 saying `taking too long` | leaves it as it is | leaves it as it is |
| Judged about the file from a call made now, or answered with an error status | ends it | ends it |

**What moves.** The breaker no longer resets on a cache hit, an occurrence with no format, a timeout with no response, or an occurrence that dropped twice. Until now each of those was a completed item to it (`ExtractionCircuitBreaker.afterProcess`). §3a's count no longer ends on a cache hit, which answers #393's fourth question.

**Why.** With §2 in place, counting too much costs one control conversion. Counting too little lets a converter that converts nothing remove file after file. A cached answer, or no call at all, says nothing about the converter now. ADR-175 counted a cache hit as an end, on the ground that *"five real killers separated by cached files look adjacent, which is the next case made worse"*. §2 now tells exactly that case apart, so it is no longer worse.

**ADR-071's timeout streak is unchanged.** It decides whether a timeout is blamed on the file or on the converter. It does not decide whether the step stops. Whether a cache hit should reset it is not decided here.

### 5. A resume (#385)

ADR-181 §1's read order stands, so the faulted occurrences of committed chunks are still read first and back to back.

- **Five repeat refusers read that way** take the breaker to five. The control conversion converts. The step goes on, and when it completes the five are resolved into `extraction-failed` with their categories, as an uninterrupted invocation would resolve them.
- **If the sidecar refuses everything**, the control conversion is refused too. The step stops, and its faults are recorded and not resolved (ADR-139 §3), as before.
- **#393's five killing files** behave the same way on a resume: they are removed, not stopped on.

This replaces ADR-181 §4's third bullet. **ADR-139 §3's inference** rests on the converter having answered for other files. For refusals followed by a control conversion that converted, that answer is the control conversion. It completed after them, on a sidecar with nothing else to do, which is better evidence than a neighbour whose call overlapped theirs.

### 6. What it costs, accepted

- **A converter that converts the control PDF and fails every file of some kind is not stopped.** One example is OCR that is broken while the layout model works, met on a folder of scanned images. Each of those files fails on its own and is marked, with a control conversion every five. That is the operator's rule from #326. It is recoverable:
  - nothing is cached for a service-scope failure (ADR-183) or a crash (ADR-175 §2), so the next stage-2 run asks again;
  - the review list shows the files with their categories, and a run of identical categories is visible there.
- **Stopping a dead converter takes longer.** It costs the wait in §2 step 1 plus one call.
  - A converter that refuses or drops at once: seconds.
  - One that answers nothing within the 5-minute call timeout: the dispatched calls are waited out, at most 8 at a time (ADR-140). Today that is up to two waves of the open chunk. Under #369's window of 17 it is up to three waves. Then the control conversion waits its own call timeout. That is roughly 15 to 20 minutes before the step stops, where today it stops at the fifth failure. Against a run measured in days, and a case this rare, that is accepted.
- **More control conversions on a partly cached corpus**, because cached answers no longer end a row. That is one small call per five failures.
- **A test whose scripted converter is asked about the control PDF** sees one more call when a count reaches five. A converter scripted to refuse or drop everything refuses or drops the control conversion too, so those tests keep stopping. A converter that answers a generic conversion does not echo the sentence, so the control conversion does not count as converting there.

### 7. What the #369 implementation must keep

Facts from the #369 session, 2026-10-04:

- `ConversionDispatch.read()` returns ids in the delegate's order, and the processor takes them one at a time on the step thread.
- `ExtractionCircuitBreaker`, `ExtractionTimeoutStreak`, `ExtractionItemProcessor` and `PendingConversions.take` are not edited.
- ADR-181 §2 holds: ids read ahead and never committed are read again.

This record relies on those facts, and adds three constraints:

1. **Every dispatched call can be waited for without being taken**, until it is taken or the reader is closed. §2 step 1 needs one place that can say "every dispatched call has finished". `PendingConversions` holds them today, and the implementer of this record adds that wait there. #369 must not hold dispatched calls anywhere that place cannot see.
2. **Calls are dispatched only by the step thread, inside `read()`.** No worker and no completion callback may dispatch a call, for example to top up the window when a call finishes. While the step thread runs §2, nothing new then goes out, and the wait in step 1 is bounded by what was already dispatched. A self-refilling window breaks that.
3. **Neither count moves off the step thread** (ADR-140 §2, §3).

## Consequences

- **#393's folder no longer stops stage 2.** Each file that kills the converter costs two crashes and is removed. Every fifth such file in a row costs one control conversion more.
- **#385's resume completes.** The five repeat refusers are resolved into verdicts, as on the first pass.
- **A converter that converts nothing still stops the step**, by the same exceptions, whose messages now say that the control conversion failed too.
- **`CONTEXT.md` gains *Control conversion*.**
- **Nothing replays.** The change is confined to `pipeline` (§3).
- **Comments that state the old rule change with the code** (ADR-077):
  - `ExtractionCircuitBreaker`'s class javadoc, *"whether it turned out `EXTRACTION_FAILED` or passed through … which is what resets the streak"*;
  - `ExtractorStoppedAnsweringException`'s javadoc;
  - `DoclingKeepsDroppingConnectionsException`'s javadoc;
  - `ExtractionItemProcessor`'s class javadoc, `retryAfterDrop`'s javadoc, and the field comment on `droppedTwiceInARow`, which says a cached answer ends the run;
  - `ExtractionItemProcessor.CONSECUTIVE_DROPPED_TWICE_COUNT`'s javadoc.
- **`AGENTS.md`'s sentence on ADR-175**, *"or drops the connection twice on five files in a row"*, is made true of what ships in the change that implements this record.

## Tests

`ControlConversionInvocationTest` (`src/test/java/io/algernon/vespera/pipeline/`) drives whole invocations with the production `DoclingClient`, extractor and cache against `LoopbackSidecar`. `LoopbackSidecar` now recognises the control conversion by the sentence its bytes carry. By default it converts it, answering with text that carries the sentence. It drops it when it drops every call, and answers it like everything else when told to answer every call one way. Every test that needs files in a row learns the order the stage reads in first, because that order is the file system's and not the names'.

1. **Ten files in a row that each drop the connection twice**, between two that convert. The step completes. The ten are removed as `crashed the converter`. The control conversion was posted twice, once per five, so the count started again after the first. *Red today: the step stops at the fifth.*
2. **A converter that drops every call**, over six files. The step stops, the control conversion was posted once, the closing line says it did not convert the control document either, and nothing is removed. *Red today on the control conversion's count and the message.*
3. **#385: five files the converter refuses for `capacity` on every call**, at every other position of ten. The first invocation completes with five extraction faults resolved. Then its completion record is deleted. The resume completes. It posts each of the five once and the control conversion once, and no other file. The five are removed with `capacity` as their reason. *Red today: the resume's breaker trips.*
4. **A converter that refuses every call for `capacity`**, the control conversion included. The step stops with the breaker's message and the new suffix, and the control conversion was posted once. *Red today on the control conversion's count and the message.*
5. **A cached answer does not end a row of dropped files.** Nine files, every call dropped. The fifth in read order was converted earlier, from a copy under another root, so it is answered from the cache. The step stops, nothing is removed, and the cached file is not posted. *Red today: four, a cache hit and four never make five, so the step completes having removed eight.*
6. **A file that dropped the connection twice does not end a row of refusals.** Six files refused for `capacity`, every call and the control conversion included, except the third in read order, whose every call drops. The step stops with the breaker's message. *Red today: the dropped file reset the breaker, so the step completes.*
7. **A control conversion answered with a conversion that lacks the sentence counts as not converting.** Five files in a row each drop twice, and the control conversion is answered with a generic conversion. The step stops. *Red today on the control conversion's count.*
8. **The control PDF is shipped**: the classpath resource exists, opens with `%PDF-`, and carries `(Vespera control document)` uncompressed. *Red today: there is no such resource.*

**`ExtractionWhenTheSidecarDropsItsConnectionTest.aTimeoutBetweenThemDoesNotEndTheRunOfDroppedDocuments`** now also drops the control conversion. Its subject is that a timeout does not end the row, and the stop it expects needs a converter that converts nothing. It is green before this change and after it. Every other test in that class is untouched.

**`ExtractionCircuitBreakerTest` and `ExtractionConcurrencyTest`** pin the breaker as a counter. Where the implementation gives the breaker a collaborator, or changes what `afterProcess` is told, these tests may need their construction adapted. What they claim must not change: five in a row reach the count, and an answered file ends the row.

## What this does not decide

- **A converter that answers every call with an HTTP error status.** Each answer is a rejection of that file (ADR-175 §1) and ends both rows, so nothing stops the step and every file is removed. This is unchanged by this record and still open.
- **A completed step whose last faults have no conversion after them**, for example a resume of fewer than five faulted occurrences, all refused. ADR-139 resolves them, and no control conversion is sent. Whether resolution should first require one is its own question.
- **Whether a cache hit should reset ADR-071's timeout streak** (§4).
- **A control conversion of the failing files' own kind.** One PDF answers whether the converter converts at all, which is the question both counts were written to ask (§6).
- **Seed extraction.** It is unchanged: the first seed that drops twice still stops it (ADR-175 §6), and it has no breaker.
- **How an operator asks again about the files on the review list**: [#386](https://github.com/algernon28/vespera/issues/386).
