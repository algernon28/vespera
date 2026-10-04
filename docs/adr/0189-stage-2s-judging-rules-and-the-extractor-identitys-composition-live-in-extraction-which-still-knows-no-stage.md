# ADR-189 — Stage 2's judging rules and the extractor identity's composition live in `extraction`, which still knows no stage

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md), the first two entries of "What this does not decide". *"Part of the code that decides stage 2's verdicts lives in `pipeline`: `ExtractionItemProcessor`'s scope reading, ADR-071's timeout streak, the extractor identity's composition, `ExtractionFaultRecorder`'s resolution and `ConversionDispatch`"* no longer holds for the first four: they are `extraction`'s (§1). *"`ExtractionFaultRecorder`'s resolution and `ConversionDispatch` … belong with it"* holds for the resolution, which moves, and not for `ConversionDispatch`, which stays in `pipeline` (§5).
- **Amends**: [ADR-184](0184-five-failures-in-a-row-stop-stage-2-only-when-the-converter-then-fails-a-control-conversion.md) §3, its last bullet: *"It lives in `pipeline`, beside the two counts, and not in `extraction`. So it does not move `extraction`'s implementation version … When #320 moves that code into `extraction`, the control conversion moves with it."* The two counts and the reading of the control conversion's answer move into `extraction`, so a change to either moves `extraction`'s implementation version. Sending it, and the PDF it sends, stay in `pipeline` behind an interface `extraction` owns (§2, §4). Its "What this does not decide" entry on whether a cache hit should reset ADR-071's timeout streak is carried across unanswered.
- **Amends**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md) §3, its first sentence: *"The extractor identity is composed in one place, the `@Lazy` bean method `ExtractionJobConfiguration.extractorIdentity`."* It is composed in one place, `ExtractorIdentity.composedOf` in `extraction`, which that bean calls once its image check has passed. The check, its order and its exception are unchanged.
- **Amends**: [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md) §3, in where its reason is composed only: *"its reason composed by the recorder as `category + ": " + detail`"*. It is composed by `extraction`'s `ExtractionFaultResolution`, in the same shape. [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §3, in where its two structures live only: *"`ExtractionFaultRecorder`'s plain `ArrayList` and `ExtractionCircuitBreaker`'s plain `int` stay plain"* — they are `ExtractionFaultResolution`'s list and `FailuresInARow`'s counts now, and they stay plain. [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §3, its stage-2 half: *"ADR-070's failure classification and ADR-071's timeout streak in `ExtractionItemProcessor`. A change to those re-mints nothing"* is no longer true of stage 2. [ADR-178](0178-text-over-the-docling-ceiling-is-converted-in-parts-and-merged-into-one-answer.md) §4, in where a reason is built only: *"built by `ExtractionItemProcessor` as it builds every reason today"* — read `OccurrenceJudge`. [ADR-143](0143-an-uncategorised-conversion-failure-is-a-verdict-against-the-file.md) Consequences, in the test it names only: `ExtractionItemProcessorTest.verdictsEveryUncategorisedFailureInARow` is `extraction`'s `OccurrenceJudgeTest.verdictsEveryUncategorisedFailureInARow`, and it drives the judge rather than the processor. Nothing else in any of the eight is changed.
- **Rests on**: [ADR-012](0012-extraction-engine-is-configurable.md) (the cache key carries the whole extractor identity), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) (`extraction` knows no stage), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (stage 2's implementation version is `extraction`'s and `similarity`'s), [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md), [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md), [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (`extraction`'s one declared reach into `corpus`), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (a capability module is handed what it cannot ask for, and gains no dependency), ADR-140 §2 and §3 (the drain, and the one thread every count is touched on), ADR-143, [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md) (the image is in the identity), [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §2 and §3a, [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md) §1 (`ResponseScope`, already in `extraction`), ADR-184 §1, §2 and §4.
- **Settles** [#407](https://github.com/algernon28/vespera/issues/407), under the decisions recorded on [#320](https://github.com/algernon28/vespera/issues/320) on 2026-10-04.

## Context

ADR-181 recorded the gap this closes. Stage 2's run id names `extraction` and `similarity` (`StageModules.EXTRACTION`, ADR-058), and the code that decides stage 2's verdicts and stops sits in `pipeline`, which it does not name. A change to that code ships under the run id of the code that wrote the old verdicts, and since ADR-181 a half-done stage 2 can be finished by different judging code under the same id. Naming `pipeline` in stage 2's identity would replay stage 2 on nearly every build (ADR-181). Moving the rules into `extraction` moves the run id only when a rule changes.

What `pipeline` holds today that decides a verdict, an extraction fault or a stop:

- **`ExtractionItemProcessor`**: what each reading of an answer earns (`judge`, `judgeConverted`, `judgeDocumentScope`, `resolveTimeout`, `rejected`, `unreadableFormat`, and the reason each one writes); the count of occurrences that dropped the connection twice (`droppedTwiceInARow`, `CONSECUTIVE_DROPPED_TWICE_COUNT`); what ends that count (`endTheRun`); and what follows when it reaches five.
- **`ExtractionTimeoutStreak`**: ADR-071's count of timeouts and its threshold of three.
- **`ExtractionCircuitBreaker`**: ADR-071's count of set-aside occurrences, its threshold of five (ADR-139 §4), and what follows when it is reached (ADR-184 §2).
- **`ExtractionRowEvidence`**: whether an occurrence brought evidence that the converter answers about files now (ADR-184 §4).
- **`ControlConversion`**: whether the control conversion converted (ADR-184 §2), beside the sending of it.
- **`ExtractionFaultRecorder.afterStep`**: writing each held fault and, where the step completed, resolving it into `extraction-failed` with the reason `category: detail` (ADR-139 §3).
- **`ExtractionJobConfiguration.extractorIdentity`**: the composition of the extractor identity string (ADR-090, ADR-147), after the image check (ADR-179 §3).

### What the move cannot take into `extraction`

- **The shingles.** `Shingler` is `similarity`'s, and `extraction` may name `ledger` and `corpus` alone. Writing a conversion's text to the shingle table stays in `pipeline`. It decides no verdict.
- **The exceptions.** `ServiceScopeFailureException` is what the step is told to skip (`faultTolerant().skip(...)`), so it is a Spring Batch contract, and `ExtractorStoppedAnsweringException` and `DoclingKeepsDroppingConnectionsException` end the step. `extraction` returns decisions; `pipeline` throws from them. `ExceptionNamingTest` is not edited.
- **The sending of the control conversion.** It waits for `PendingConversions` and `SidecarRecovery`, which are `pipeline`'s, and its PDF is a `pipeline` resource that `ControlConversionInvocationTest` reads by that path.
- **The text that names the stage.** Every line an operator reads from stage 2 names it (`[extraction]`, `Stage 2 (extraction)`), and `extraction` knows no stage. Every such line stays in `pipeline`, word for word.
- **The image check and the lazy bean** (ADR-179 §3, #319). Asking the sidecar for `/version` stays behind `ExtractionHealthCheckListener`.

## Decision

### 1. What moves into `extraction`

Every rule that decides a stage-2 verdict, an extraction fault or a stop, with its behaviour unchanged:

1. **What an answer earns** (ADR-070, ADR-143, ADR-183 §1): the branching on `ResponseScope`, the reasons, which readings write a metric row, and the degeneracy floor's verdict, which `ExtractionMetrics.writeAndJudge` already decides in `extraction`.
2. **What a call that brought no answer earns**: a timeout (ADR-071), an error status (ADR-175 §1), a connection dropped twice (ADR-175 §2), and an occurrence with no recorded format (ADR-100).
3. **The three counts, in one plain class**: timeouts in a row (ADR-071), set-aside occurrences in a row (ADR-071, ADR-139), and occurrences in a row that dropped the connection twice (ADR-175 §3a), with their thresholds of 3, 5 and 5, and what ends each one (ADR-184 §4).
4. **What follows from each count**: carry on, send the control conversion, or stop (ADR-184 §2).
5. **The test of whether the control conversion converted** (ADR-184 §2).
6. **The end-of-step resolution of extraction faults** (ADR-139 §2 and §3).
7. **The composition of the extractor identity string** (ADR-012, ADR-090, ADR-147).

### 2. The API `extraction` exposes

All in `io.algernon.vespera.extraction`. No type carries a Spring annotation, and none names a Spring Batch type. `extraction`'s `allowedDependencies` stay `ledger` and `corpus`.

```java
/** Sends the control document; pipeline implements it. */
@FunctionalInterface
public interface ControlConversion {
    /** The one line of text the shipped control PDF carries. */
    String SENTENCE = "Vespera control document";

    /**
     * Sends the control document alone and returns the converter's answer, or empty where the converter
     * gave none: a rejection, a timeout, a dropped connection. A local fault (the PDF missing, a temporary
     * file that cannot be written) and DoclingDidNotComeBackException propagate.
     */
    Optional<DoclingResponse> send();

    /** One that never gets an answer, so a count that reaches its threshold stops. */
    static ControlConversion never() { return Optional::empty; }
}

/** Whether the control document converted, read off its answer. */
public enum ControlReading {
    CONVERTED, NOT_ANSWERED, NOT_A_CONVERSION, LACKS_THE_SENTENCE;

    /** Empty is NOT_ANSWERED; not a ResponseScope.Conversion is NOT_A_CONVERSION; a conversion whose
     *  DoclingDocumentTexts.lines contains SENTENCE (String.contains) is CONVERTED; else LACKS_THE_SENTENCE. */
    public static ControlReading of(Optional<DoclingResponse> answer);

    public boolean converted();   // this == CONVERTED
}

/** Where one count stands after an event. */
public sealed interface InARow {
    int inARow();
    record Counted(int inARow) implements InARow {}             // below its threshold; nothing sent
    record Reached(int inARow) implements InARow {}             // set-asides only: due; nothing sent yet
    record ControlConverted(int inARow) implements InARow {}    // sent and converted; both rows start again
    record ControlNotConverted(int inARow, ControlReading reading, List<String> recordedPaths)
            implements InARow {}                                // sent and not converted: stop
}

/**
 * The three counts. Plain: not final, so pipeline can scope it to the step; not thread-safe; touched
 * on the step thread only (ADR-140 §3).
 */
public class FailuresInARow {
    public static final int CONSECUTIVE_TIMEOUT_COUNT = 3;
    public static final int CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT = 5;
    public static final int CONSECUTIVE_DROPPED_TWICE_COUNT = 5;

    public FailuresInARow(ControlConversion controlConversion);

    /** An occurrence starts: forgets whatever the last one that did not complete said. */
    public void occurrenceStarted();
    /** An occurrence completed: ends the set-aside row unless it brought no evidence; forgets that either way. */
    public void occurrenceCompleted();
    /** One more occurrence set aside: Counted below five, Reached at five and over. Sends nothing. */
    public InARow setAside();
    /** Sends the control conversion the set-aside row is due: ControlConverted or ControlNotConverted
     *  (recordedPaths empty). IllegalStateException, sending nothing, unless the last setAside() was Reached. */
    public InARow controlConversionAfterSetAsides();

    // Package-private, for OccurrenceJudge: count a timeout and return the row's length; end the timeout
    // row; end the dropped row; mark the occurrence as no evidence; count one dropped twice (marking it
    // as no evidence, and at five sending the control conversion and reading it).
}

/** What one file occurrence's call earns. */
public sealed interface OccurrenceDecision {
    /** A conversion: pipeline shingles its text; a verdict of degenerate-output where a reason is present,
     *  else none (a survivor). Its metric row is already written. */
    record Converted(Optional<String> degenerateReason) implements OccurrenceDecision {}
    /** extraction-failed, with this reason. */
    record Failed(String reason) implements OccurrenceDecision {}
    /** Set aside unjudged: pipeline throws ServiceScopeFailureException(occurrence, category, detail). */
    record SetAside(String category, String detail) implements OccurrenceDecision {}
    /** Dropped the connection twice: extraction-failed with this reason, unless row is ControlNotConverted. */
    record DroppedTwice(String reason, InARow row) implements OccurrenceDecision {}
}

/** Decides what each occurrence's call earns, and keeps the counts through FailuresInARow. */
public final class OccurrenceJudge {
    public OccurrenceJudge(ExtractionMetrics metrics, FailuresInARow failuresInARow,
                           RunId extractionRun, Double confidenceFloor);

    public OccurrenceDecision answered(OccurrenceId occurrence, DoclingResponse response, boolean fromCache);
    public OccurrenceDecision timedOut(OccurrenceId occurrence, String detail);       // no response came back
    public OccurrenceDecision rejected(OccurrenceId occurrence, String message);      // an HTTP error status
    public OccurrenceDecision droppedTwice(OccurrenceId occurrence, String recordedPath, String cause);
    public OccurrenceDecision noDetectedFormat(OccurrenceId occurrence, RunId byteLevelReductionRun);
}

/** Holds the set-aside occurrences of one step and resolves them at its end. */
public final class ExtractionFaultResolution {
    public ExtractionFaultResolution(ExtractionFaults faults, Ledger ledger);
    /** Holds one, writing nothing. */
    public void hold(OccurrenceId occurrence, String category, String detail);
    public boolean nothingHeld();
    /** Writes every held fault under run, in the order held; where completed, also writes extraction-failed
     *  against each with the reason category + ": " + detail. In the caller's transaction. */
    public void resolve(RunId run, boolean completed);
}

public record ExtractorIdentity(String value) {
    // ...the existing record, plus:
    /** "docling-serve;image=" + image + ";" + every reported entry as key=value, sorted by key and joined
     *  by ";", + ";" + DoclingClient.sentOptions(). Checks nothing and calls nothing. */
    public static ExtractorIdentity composedOf(String image, Map<String, String> reported);
}
```

### 3. The behaviour each keeps

Every rule below was run through today's `ExtractionItemProcessor` and `ExtractionCircuitBreaker` by a throwaway probe before this record was written, with a scripted converter and a scripted control conversion, over 35 sequences. The rules state what it measured.

**What an answer earns** (`answered`), by `ResponseScope`:

- **A conversion**: ends the timeout row; ends the dropped row if the answer came from a call made now, and marks the occurrence as no evidence if it came from the cache; writes the metric row and judges the floor through `writeAndJudge`; returns `Converted`, with the floor's reason where it is degenerate.
- **A timeout the converter reported**: counts one timeout. At three or more in a row it returns `SetAside("timeout", <the error's message>)` and touches nothing else, so it writes no metric row, ends no row and marks nothing. Below three it writes the metric row, ends the dropped row (or, from the cache, marks no evidence) and returns `Failed("timeout: " + <the message>)`.
- **A failure blamed on the document**: ends the dropped row (or marks, from the cache); ends the timeout row; writes the metric row; returns `Failed(<category in lower case> + ": " + <message>)` for the error `ResponseScope` blamed, or `Failed("unknown: no categorized error was reported")` where it blamed none.
- **A failure blamed on the converter**: ends the timeout row; returns `SetAside(<its category>, <its message>)`. It ends no dropped row, marks nothing and writes no metric row.

**What a call with no answer earns**:

- `timedOut`: counts one timeout. At three or more it returns `SetAside("timeout", detail)`. Below, it marks no evidence and returns `Failed("timeout: " + detail)`, with no metric row.
- `rejected`: ends the timeout row and the dropped row, and returns `Failed("rejected: " + message)`.
- `droppedTwice`: leaves the timeout row as it is, marks no evidence, and adds `recordedPath` to the dropped row. Below five the row is `Counted`. At five it sends the control conversion and reads it: `ControlConverted` ends the dropped row and the set-aside row; `ControlNotConverted` carries the paths in the order they were added. The reason is `"crashed the converter: docling-serve dropped the connection twice while converting this file: " + cause`.
- `noDetectedFormat`: marks no evidence and returns `Failed("no detected format is recorded for occurrence " + <id> + " under run " + <run>)`.

**The counts:**

- **A timeout row is not started again when it flips.** The fourth timeout in a row is set aside too.
- **What ends the timeout row** is a conversion, a failure blamed on the document, a failure blamed on the converter, and a rejection. **An answer from the cache ends it as well**, as it does today. Whether it should is ADR-184's open question, carried across unanswered (What this does not decide).
- **What leaves the timeout row** is a connection dropped twice, an occurrence with no recorded format, and a control conversion that converted. A control conversion starts the other two rows again, never this one.
- **The set-aside row** grows by one at each `setAside()`. At five, `Reached`, and the control conversion is sent only by `controlConversionAfterSetAsides()`. `ControlConverted` ends the set-aside row and the dropped row. `occurrenceCompleted()` ends the set-aside row unless the occurrence was marked as no evidence.
- **Marked as no evidence**, so leaving the set-aside row as it is: an answer from the cache, a timeout with no response below the flip, a connection dropped twice, and an occurrence with no recorded format. An occurrence that is set aside does not complete, so it neither ends nor marks. A reported timeout below the flip, a rejection, a conversion and a failure blamed on the document are evidence. This is ADR-184 §4's table, unchanged.
- **The dropped row** ends on an answer from a call made now (a conversion, a failure blamed on the document, a timeout the converter reported below the flip), on a rejection, and on a control conversion that converted for either row. It is left as it is by an answer from the cache, a timeout with no response, a failure blamed on the converter, a reported timeout that flipped, and an occurrence with no recorded format.
- **A mark does not outlive its occurrence.** `occurrenceStarted()` forgets a mark left by an occurrence that did not complete.

**The reading of the control conversion** is `ControlReading.of`, in that order: no answer, then not a conversion, then whether the text contains the sentence. A failure whose raw answer carries the sentence is `NOT_A_CONVERSION`. A line that carries the sentence among other words is `CONVERTED`, because `lines` joins the text items into one string and the test is `String.contains`.

### 4. What stays in `pipeline`, and how it uses the API

- **`ExtractionItemProcessor`** keeps `@Component @StepScope`, `ItemProcessor`, the progress line, resolving the path, the lookups of the format, hash and subtype under stage 1's run, collecting a dispatched answer from `PendingConversions` or placing the call itself (`convertNow`), the wait and the one more call after a dropped connection (ADR-175 §2), the shingles of a conversion, and every log line, word for word. It builds one `OccurrenceJudge` in its constructor, over `stageRuns.extraction()` and `confidenceFloor.value()`. Its two constructors become:

  ```java
  ExtractionItemProcessor(Ledger, ContentIdentity, DetectedFormats, DoclingExtractor, ExtractorIdentity,
          StageRuns, ExtractionMetrics, DegenerateOutputConfidenceFloor, Shingler, SidecarRecovery)
          // the test seam: PendingConversions.none(), new FailuresInARow(ControlConversion.never())
  @Autowired
  ExtractionItemProcessor(Ledger, ContentIdentity, DetectedFormats, DoclingExtractor, ExtractorIdentity,
          StageRuns, ExtractionMetrics, DegenerateOutputConfidenceFloor, Shingler, PendingConversions,
          SidecarRecovery, FailuresInARow)
  ```

  `process` calls `occurrenceStarted()` first. Each path calls the judge as the old code branched: no format, `noDetectedFormat`, and the log line `[extraction] <reason>`; an answer, `answered(…, pending.answeredFromCache(…))`, read before the answer is taken, as today; a timeout, `timedOut(…, timedOut.getMessage())`; an error status, `rejected(…, rejected.getMessage())` and the line `[extraction] occurrence <id> could not be read: <reason>`; a second drop, after the second wait, `droppedTwice(…, <recorded path>, again.getCause().getMessage())`. It acts on the decision: `Converted` writes the shingles of `DoclingDocumentTexts.lines(response.rawResponse())` under the run and returns `degenerate-output` or `null`; `Failed` returns `extraction-failed`; `SetAside` throws `ServiceScopeFailureException`; `DroppedTwice` with `ControlNotConverted` logs the reading's line where it has one, then `Stage 2 (extraction): these <n> files in a row each dropped the connection twice: <paths>`, and throws `DoclingKeepsDroppingConnectionsException(FailuresInARow.CONSECUTIVE_DROPPED_TWICE_COUNT)`; with `ControlConverted` it logs today's warning that the converter converted the control document; otherwise, and after that warning, it logs the `could not be read` line and returns `extraction-failed`. `CONSECUTIVE_DROPPED_TWICE_COUNT` leaves this class.
- **`ExtractionCircuitBreaker`** keeps both listener roles. Its constructors are `ExtractionCircuitBreaker()`, over `new FailuresInARow(ControlConversion.never())`, and `@Autowired ExtractionCircuitBreaker(FailuresInARow)`. `onSkipInProcess` calls `setAside()` and logs today's `[extraction] service-scope failure on …` warning with `inARow()`, before anything is sent, as today. On `Reached` it calls `controlConversionAfterSetAsides()`: `ControlConverted` logs today's warning; `ControlNotConverted` logs the reading's line where it has one, then today's error line, and throws `ExtractorStoppedAnsweringException(inARow, t)`. `afterProcess` calls `occurrenceCompleted()`. `CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT` leaves this class.
- **`DoclingControlConversion`** is today's `ControlConversion` class, renamed so that the name is the interface's, implementing it. `send()` waits for every dispatched call, then for `/health`, materialises the shipped PDF outside the corpus root, sends it through `convertUncached` as a PDF with no subtype, and returns the answer, or empty after today's `the control conversion did not come back` line. The resource stays at `io/algernon/vespera/pipeline/control-conversion.pdf`. It keeps a static helper that logs `NOT_A_CONVERSION`'s and `LACKS_THE_SENTENCE`'s lines word for word, for the processor and the breaker, which log it where `ControlConversion.converts()` logged it: after the send and before their own line. Its counter of conversions goes: the counts are in one object, so a control conversion that converted ends the other row at once rather than at its next look, with the same outcome, because nothing reads that row between the two moments.
- **`ExtractionFaultRecorder`** keeps both listener roles, its constructor and its place in the listener order (ADR-139 §4). It builds an `ExtractionFaultResolution` from the `ExtractionFaults` and `Ledger` it is handed; `onSkipInProcess` holds each `ServiceScopeFailureException`; `afterStep` returns at once where `nothingHeld()`, asks for the run only otherwise (#319), and calls `resolve(run, completed)` inside its `TransactionTemplate`.
- **`ExtractionJobConfiguration`**: `extractionControlConversion` returns a step-scoped `DoclingControlConversion`; a new step-scoped bean `FailuresInARow extractionFailuresInARow(DoclingControlConversion)` is the one instance the processor and the breaker share; the `ExtractionRowEvidence` bean goes. `extractorIdentity` keeps `@Lazy`, `/version`, the `vespera-image` check and `DoclingRunsAnotherImageException`, then returns `ExtractorIdentity.composedOf(image, reported)`.
- **Deleted**: `ExtractionTimeoutStreak` and `ExtractionRowEvidence`. Their state is `FailuresInARow`'s.
- **Unchanged**: `ConversionDispatch`, `PendingConversions`, `SidecarRecovery`, `ExtractionOutcome`, the three exceptions, `ExtractionHealthCheckListener`, the step's shape, chunk size, skip limit, concurrency and listener order.

`extraction`'s `package-info` says *"no verdict is written here (that is `pipeline`'s stage-2 step)"*. That is no longer true, and is replaced by: stage 2's rules are here — what an answer earns, the counts of failures in a row and what follows from them, and the resolution of extraction faults, which writes the verdicts that resolve them — and `pipeline` holds the step that calls them and the text that names it.

### 5. Where the line is drawn, and what it does not reach

`ConversionDispatch`, `PendingConversions` and `SidecarRecovery` stay in `pipeline`, as #320 decided. ADR-181 said `ConversionDispatch` belongs with the judging code because it decides what stage 2 records. Three things that stay in `pipeline` can still change an outcome, and they are named here so that nobody reads §1 as having closed them:

- **Whether an answer came from the cache.** `PendingConversions.answeredFromCache` is handed to the judge as `fromCache`, and the counts read it (§3).
- **What a call is keyed and sent by.** The content hash, the format and the subtype are read from stage 1's records in `ConversionDispatch` and `ExtractionItemProcessor.convertNow`. The identity string and the cache key built from it are `extraction`'s (`ExtractorIdentity.composedOf`, `DoclingExtractor`, `ExtractionCache`).
- **The one more call after a dropped connection** (ADR-175 §2). Whether an occurrence that dropped once is judged on a second answer depends on `pipeline` placing that call.

Moving these would put the call itself into `extraction`, which #320 kept in `pipeline` as plumbing. A change to any of them still keeps stage 2's run id, as before this record.

### 6. ADR-040's rule holds

`extraction` still knows no stage: nothing in it names one, and every line that does stays in `pipeline`. A rule is a capability whether or not a stage calls it, which is the reading ADR-188 gave the same rule for `corpus`. `extraction` still declares `ledger` and `corpus` alone, takes on no Spring Batch type, and `ModuleBoundariesTest` gains no allowed dependency.

## Consequences

- **Stage 2's run id moves once**, through `extraction`'s implementation version, on the first build that ships this. Its recorded settings do not change, so `RunIdentityGoldenTest` passes unedited. The extractor identity string is byte-identical, so every cached conversion is still a hit, and the new stage-2 run replays over the extraction cache rather than the converter, except for answers the cache does not keep (ADR-183).
- **Every later stage's run id moves with it**, since each names stage 2's run upstream. #320 plans for this: no corpus run is started until ADR-188, ADR-189 and ADR-190 are all on main.
- **The same corpus gets the same verdicts, the same faults, the same control conversions and the same stops.** The rules and their order are moved, not rewritten, and §3 is what the probe measured.
- **A change to a stage-2 rule now moves stage 2's run id**, which ADR-181 and ADR-154 §3 recorded as missing.
- **The control conversion's answer and the counts are versioned under `extraction`**, which ADR-184 §3 had chosen against. The control PDF is not: it stays a `pipeline` resource, and a change to it alone keeps stage 2's run id.
- **Measured size**, which replaces #320's plan estimate of about −1,000 lines in `pipeline` for the whole wave. Counted on the implementer's diff as code lines, with javadoc, comments, blank lines and imports left out: the six `pipeline` classes the move touches go from 473 code lines to 313 — `ExtractionItemProcessor` 263 to 180, `ExtractionCircuitBreaker` 61 to 49, `ExtractionFaultRecorder` 46 to 31, `ControlConversion` 77 to `DoclingControlConversion`'s 53, and `ExtractionTimeoutStreak` (12) and `ExtractionRowEvidence` (14) deleted. `extraction` gains 237: `OccurrenceJudge` 88, `FailuresInARow` 75, `ExtractionFaultResolution` 28, `ControlReading` 21, `InARow` 11, `ControlConversion` 8 and `OccurrenceDecision` 6. The identity's composition adds 8 to `ExtractorIdentity` (7 to 15) and takes 5 from `ExtractionJobConfiguration` (164 to 159).

## Tests

Pinned by measurement first: the probe in §3, run against `main` at `b6040e6` and deleted. Each new test was then run against a throwaway port of §2 in the test tree, and each claim about an order or a row was run against mutated ports — a row that started again when it flipped, an answer from the cache that left the timeout row, a control conversion that ended the timeout row or failed to end the other row, a mark that outlived its occurrence, a stop that sorted its paths, a flipped timeout that ended the dropped row, a whole-line test for the sentence — and each mutation failed the test written for it.

- **`extraction.OccurrenceJudgeTest`** (new, over the test database): `ExtractionItemProcessorTest`'s eighteen tests, ported. Seventeen are here, as claims about the judge with the same answers and expectations; three of those (a conversion that is not chunked, the spreadsheet that clears the floor, and the occurrence with no recorded format) carry the judging claims of a test whose other claims stay in `pipeline`. The claim that nothing is chunked is held in both places: the judge's copy shows the judge chunks nothing, and only the processor's can catch chunking added to the processor. The eighteenth, `convertsAsTheFormatStageOneRecorded`, judges nothing and stays in `pipeline` whole. Three more pin moved rules no `pipeline` unit test reached: a rejection's reason, a dropped-twice reason, and which timeouts are measured.
- **`extraction.FailuresInARowTest`** (new, over the test database): the thresholds of 3, 5 and 5; every row of §3's counts, each built so that the other reading gives a different answer; the control conversion sent only when due; the stop naming its paths in the order they came; and a control conversion that converted for one row starting the other again.
- **`extraction.ControlReadingTest`** (new, pure): the sentence, and the four readings, including the two cases above.
- **`extraction.ExtractionFaultResolutionTest`** (new, over the test database): nothing written while held; fault rows and resolving verdicts on completion; fault rows and no verdict on a stop.
- **`extraction.ExtractorIdentityCompositionRuleTest`** (new, pure): today's composition written out, and independence of the order the sidecar reports in.
- **`pipeline.ExtractionItemProcessorTest`** (rewritten): the shell's half — the shingles of a conversion and that nothing is chunked, the shingles of a spreadsheet, the format and subtype sent, and an occurrence with no recorded format not sent, its reason naming stage 1's run and not stage 2's, while the next one is judged — over the new test-seam constructor.
- **`ExtractionCircuitBreakerTest`, `ExtractionConcurrencyTest`** (ADR-140's single-thread tests), **`ExtractionFaultInvocationTest`, `ServiceScopeRefusalInvocationTest`, `SeedScriptedExtractionBeans`**: each reads `CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT` off `FailuresInARow`, and `ExtractionCircuitBreakerTest`'s javadoc points at `OccurrenceJudgeTest`. ADR-184's Tests section foresaw that their construction might need adapting; what they claim is unchanged. **`CascadeSliceTest`** stops importing `ExtractionTimeoutStreak`. **`ExtractionStepTest`** and **`extraction.ExtractionFaultsTest`** have javadoc-only edits pointing at where the judging and the resolution now live.
- **`ExtractorIdentityCompositionTest`, `ExceptionNamingTest`, `RunIdentityGoldenTest`, `OperatorTextTest`, `ModuleBoundariesTest`, `ControlConversionInvocationTest`** and every stage-2 invocation test: unedited, and green against the port.

Until the types in §2 existed, the five new test classes and the edited ones did not compile. That was the handoff to the implementer.

## What this does not decide

- **Whether a cache hit should reset ADR-071's timeout streak** (ADR-184). It does today, and `FailuresInARowTest` pins that until someone decides otherwise.
- **The three outcomes §5 leaves in `pipeline`.**
- **A converter that answers every call with an HTTP error status** (ADR-184), unchanged.
- **Seed extraction**, whose processor shares none of these rules.
- **Stage 1's and 6b's rules**, which are ADR-188's and ADR-190's.
