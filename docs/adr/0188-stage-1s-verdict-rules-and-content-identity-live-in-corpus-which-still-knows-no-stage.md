# ADR-188 — Stage 1's verdict rules and content identity live in `corpus`, which still knows no stage

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md), as `ByteLevelReductionTasklet` applied it to stage 1. [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), in its location sentence only: *"The list is a code default, in one place: `pipeline`'s `OutOfScope`."* The list is still a code default in one place, and that place is now `corpus`'s `OutOfScope`. [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md), [ADR-168](0168-videos-are-out-of-scope-and-stage-1-recognises-one-by-its-container-signature.md) and [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md), only where each names `pipeline`'s `OutOfScope` as the place its rule is written: read each as `corpus`'s. Nothing else in the four is changed.
- **Answers**: [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md), the question it left open: *"An edit to `OutOfScope` alone would not re-mint stage 1. That is not decided here."* An edit to `OutOfScope` alone now re-mints stage 1, because `OutOfScope` is in `corpus` and stage 1's implementation version is `corpus`'s (Consequences).
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (stage 1's implementation version is `corpus`'s alone), [ADR-067](0067-content-identity-is-a-sha-256-hash-in-corpus-computed-within-size-matched-groups.md), [ADR-068](0068-broken-is-a-cross-format-floor-plus-per-format-structural-checks-no-new-dependency.md), [ADR-069](0069-a-duplicate-set-resolves-by-earliest-creation-time-then-path.md), [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) (the progress cadence and the per-item lines), [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md), [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (`extraction`'s one declared reach into `corpus`), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (a capability module is handed plain values rather than gaining a dependency), [ADR-171](0171-a-log-is-out-of-scope-told-from-its-timestamps-and-so-is-text-too-large-for-docling-to-convert-in-time.md) (§1 the log rule before the size rule, §3 the floor and its measurement, §4 the recorded settings that keep the size numbers in stage 1's identity), [ADR-178](0178-text-over-the-docling-ceiling-is-converted-in-parts-and-merged-into-one-answer.md).
- **Settles** [#406](https://github.com/algernon28/vespera/issues/406), under the decisions recorded on [#320](https://github.com/algernon28/vespera/issues/320) on 2026-10-04.

## Context

`ByteLevelReductionTasklet`'s javadoc gives the reason stage 1's rules sit in `pipeline`:

> Lives here rather than in `corpus` because `corpus` does not know what a stage is (ADR-040); `BrokenCheck`, `ContentHash` and `DuplicateResolution` are the capabilities, this tasklet is the stage that drives them in order.

The reason does not hold. What the tasklet holds beyond the step shell is not knowledge of a stage. It is the rules that decide stage 1's three verdicts:

- the order in which the out-of-scope rules apply — the kind of file, then a log, then the size — and the reason each one writes;
- what is counted into the format mix, and how;
- the content-identity pass: group the survivors by size, hash only within a group of two or more, resolve each content identity to one representative, verdict the rest `superseded-by`, and write the reason that names the representative.

None of that names a stage, and all of it changes what stage 1 decides. Because it sits in `pipeline`, a change to it does not move stage 1's run id: stage 1's implementation version is `corpus`'s alone (ADR-058, `StageModules.BYTE_LEVEL_REDUCTION`). A reworded out-of-scope reason, or a different order of the rules, ships under the run id of the code that wrote the old verdicts. Moved into `corpus`, every such change moves the run id, as ADR-058 intends.

The one place `pipeline` names a stage in this code is the operator text: the `[byte-level-reduction]` prefix on the per-item lines and the label on the two progress lines. That text stays in `pipeline`.

### What the move cannot take into `corpus`

`OutOfScope.sizeReason` (ADR-171, amended by ADR-178) reads three facts that belong to `extraction`: the text size ceiling (`DoclingClient.TEXT_SIZE_CEILING_BYTES`), the largest text converted in parts (`TextParts.LARGEST_TEXT_BYTES`), and whether a given file is converted in parts (`TextParts.convertedInParts`). `corpus` may depend on `ledger` alone, and `extraction` already depends on `corpus` (ADR-100), so `corpus` cannot ask `extraction`. Stage 2 also reads `convertedInParts`, and stage 1 and stage 2 must not disagree about which files are cut. So that rule stays in `extraction`, and `corpus` is handed its answer.

This is the same arrangement ADR-110 made for `synthesis`: a capability module is handed plain values by `pipeline`, and the module rule gains no exception. The two numbers are already in stage 1's recorded settings (ADR-171 §4: `textSizeCeilingBytes`, and `textParts` carries `upto=`), so a change to either still moves stage 1's run id.

## Decision

### 1. Both of stage 1's passes move into `corpus`, as they are

Five things move from `pipeline` to `io.algernon.vespera.corpus`, with their behaviour unchanged:

1. **The broken and out-of-scope pass**, `ByteLevelReductionTasklet.verdictBrokenSurvivors` up to and excluding the writing of the page: drain the survivors, `BrokenCheck.check` each one, record its detected format before any verdict, count it into the mix, apply the out-of-scope rules in their present order, and write `broken` or `out-of-scope`.
2. **`OutOfScope`**, whole: `reasonFor`, `logReason`, `sizeReason` and `structuredKind`, with every reason word for word.
3. **The format-mix tally**: `countInTheMix`, `leadingBytesOf`, the timestamp bands and the count of text files with fewer than ten non-blank lines.
4. **The content-identity pass**, `resolveDuplicates`, `resolveGroupSharingASize` and `verdictSuperseded`: re-read the survivors, group by size, hash within groups of two or more, record each hash, resolve each content identity through `DuplicateResolution`, and record and verdict every superseded occurrence with the reason `superseded by the representative at <path>`.
5. **`drain`**, which both passes use.

**Moved as is.** Both passes still drain the survivor reader into a `List`, which departs from ADR-060. Fixing that is [#405](https://github.com/algernon28/vespera/issues/405)'s job, not this record's.

### 2. The API `corpus` exposes

All of these are new, public and in `io.algernon.vespera.corpus`, except two that stay package-private: `OutOfScope`, and `SurvivorDrain`, which holds `drain` for both passes. `SurvivorDrain.drain` takes Spring Batch's `ItemStreamReader<OccurrenceId>` because that is what `Ledger.survivors` returns; it is a library type, not a module, so `corpus` still names only `ledger`. None carries a Spring annotation. `pipeline` builds both passes inside `execute`, from the `Ledger`, `ContentIdentity` and `DetectedFormats` it already holds. So `ByteLevelReductionTasklet`'s constructor does not change, and neither do the tests that call it.

```java
/** Stage 1's first pass: every survivor is checked, and the broken and out-of-scope are verdicted. */
public final class BrokenOrOutOfScope {
    public BrokenOrOutOfScope(Ledger ledger, DetectedFormats detectedFormats, TextSizeLimits limits);

    /**
     * Checks every survivor of runId, in the order Ledger#survivors gives them, and returns the tally.
     * logFloor is null where no log rule applies (ADR-171 §3).
     */
    public FormatMix verdictSurvivors(RunId runId, Path canonicalRoot, Double logFloor, CheckingProgress progress)
            throws Exception;

    /** What one survivor came to; given to CheckingProgress after its verdict, if any, is written. */
    public sealed interface Outcome {
        record Broken(String reason) implements Outcome {}
        record LeftOut(String reason) implements Outcome {}
        record Kept() implements Outcome {}
    }
}

/** What the first pass tells its caller as it goes. */
public interface CheckingProgress {
    /** After each survivor is checked and its verdict, if any, written. */
    void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome);

    /** A text file whose lines could not be read to see whether it is a log, so it is not one. */
    void timestampsUnreadable(OccurrenceId occurrence, Exception cause);
}

/** Stage 1's second pass: content identity over what the first pass left. */
public final class ContentIdentityResolution {
    public ContentIdentityResolution(Ledger ledger, ContentIdentity contentIdentity);

    /** Re-reads the survivors of runId, so the first pass's verdicts exclude what they removed. */
    public void resolve(RunId runId, Path canonicalRoot, HashingProgress progress) throws Exception;
}

/** What the second pass tells its caller as it goes. */
public interface HashingProgress {
    /** Called exactly once, before the first hash, zero included: how many file occurrences will be hashed. */
    void toHash(long occurrences);

    /** After each file occurrence is hashed and its hash recorded. */
    void hashed(OccurrenceId occurrence, String sha256);
}

/** extraction's limits on text, handed in by pipeline, because corpus cannot ask extraction. */
public record TextSizeLimits(long ceilingBytes, long largestBytes, InParts inParts) {
    @FunctionalInterface
    public interface InParts {
        /** Whether this text file, of this size, is converted in parts. */
        boolean convertedInParts(Path file, Optional<DetectedSubtype> subtype, long sizeBytes);
    }
}

/** What the first pass found across the corpus, for the page pipeline writes (ADR-095). */
public record FormatMix(
        Map<DetectedFormat, Integer> byFormat,
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
        Map<String, Integer> unrecognisedLeadingBytes,
        int outOfScope,
        int logs,
        int tooLarge,
        int[] byTimestampBand,
        int fewerThanTenLines) {
    /** The timestamp bands, one per tenth of the share. */
    public static final int TIMESTAMP_BANDS = 10;
}

/** Package-private, moved from pipeline with its reasons unchanged. */
final class OutOfScope {
    static Optional<String> reasonFor(DetectedFormat format, Optional<DetectedSubtype> subtype);
    static Optional<String> logReason(TimestampedLines.Count count, Double floor);
    static Optional<String> sizeReason(Path file, long sizeBytes, Optional<DetectedSubtype> subtype, TextSizeLimits limits);
}
```

The behaviour each of these keeps, stated so that nothing is left to choose:

- **`sizeReason`** applies its three rules in the order it does today. Over `limits.largestBytes()`, any text gets the largest-size reason. At or under `limits.ceilingBytes()`, there is no reason. Over the ceiling, HTML, CSV and AsciiDoc get the structured reason. Any other text gets no reason when `limits.inParts()` says it is converted in parts, and gets the UTF-16 or UTF-32 reason when it says not. Each number in a reason is the one the limits carry, grouped with `,`. `corpus` holds no number of its own for any of this.
- **The order within one survivor** is unchanged: check, record the detected format, count into the mix, decide, write the verdict, then call `CheckingProgress.checked`. The log rule comes before the size rule (ADR-171 §1). `tooLarge` counts a `PLAIN_TEXT` survivor left out for any reason other than a log, as today.
- **`timestampsUnreadable`** is called where the tasklet now logs a warning, with the exception it caught (`IOException` or `RuntimeException`). The survivor is then treated as no log, and the size rule still applies to it.
- **`toHash`** receives the number of survivors in size groups of two or more. A file whose size is unique is never hashed (ADR-057, ADR-067), so it is not counted. `hashed` is called after `ContentIdentity.recordHash`, once for each file occurrence hashed.
- **`FormatMix`** is the tally `FormatMixReport.Mix` is built from today, minus the log floor, which `pipeline` holds. `FormatMixReport.BANDS` is defined as `FormatMix.TIMESTAMP_BANDS`, so the two cannot disagree. `FormatMixReport` gains a `grouped(long)` of its own, because `OutOfScope` is no longer visible from `pipeline`.
- **`corpus` logs nothing per item.** Every line an operator reads from stage 1 is written by `pipeline`, as it is today.

### 3. What stays in `pipeline`

`ByteLevelReductionTasklet` keeps the step shell and the stage's operator text, and nothing that decides a verdict:

- resolving the canonical root, minting the run through `RunMint` with the unchanged `ConfigConsumed`, `TaskletSteps.once`, the discard of an unfinished attempt, and the start and end lines;
- reading the log floor off the profile (`logFloorOf`). `corpus` may not name `profile`, so it is handed the number;
- **the adapter onto `TextSizeLimits`**: `new TextSizeLimits(DoclingClient.TEXT_SIZE_CEILING_BYTES, TextParts.LARGEST_TEXT_BYTES, (file, subtype, size) -> TextParts.convertedInParts(file, DetectedFormat.PLAIN_TEXT, subtype.orElse(null), size))`. This is wiring, not a rule. Both numbers and the predicate belong to `extraction`. If the adapter were wired wrongly, `ByteLevelReductionTaskletTest`'s boundary tests at 16,000,000 and 64,000,000 bytes and its UTF-16 test would fail;
- **the `CheckingProgress` it implements**: it logs `[byte-level-reduction] checked {} for damage -> {}`, with `broken: <reason>`, `out of scope: <reason>` or `kept`, then calls `StageProgress.itemDone()` on a `StageProgress` it opened as `"Stage 1 (byte-level reduction, broken check)"` over `ledger.survivorCount(runId)`. On `timestampsUnreadable` it logs today's warning word for word;
- **the `HashingProgress` it implements**: on `toHash`, it opens `StageProgress.over("Stage 1 (byte-level reduction, content hash)", occurrences)`. On `hashed`, it logs `[byte-level-reduction] hashed {} -> {}` and calls `itemDone()`;
- writing `format-mix.html` from `FormatMixReport.render(new FormatMixReport.Mix(... from FormatMix ..., logFloor))`, and saving the floor's measurement to the profile (ADR-171 §3);
- `FormatMixReport` itself, which is a page for the operator.

The tasklet's javadoc sentence quoted above is replaced by one that says `corpus` holds the rules and `pipeline` holds the step and its text.

### 4. ADR-040 is amended in how it was applied, not in its rule

The rule stands: `corpus` depends on `ledger` alone, and knows no stage. What changes is the reading that a stage's verdict rules must live in `pipeline` because a stage drives them. A rule is a capability whether or not a stage calls it. `pipeline` names the stage, orders the steps and writes the text that names them. `corpus`'s `package-info` and its `allowedDependencies = "ledger"` are unchanged, and `ModuleBoundariesTest` gains no allowed dependency.

## Consequences

- **Stage 1's run id moves once**, through `corpus`'s implementation version, on the first build that ships this. Its recorded settings do not change, so `RunIdentityGoldenTest` passes unedited. The ledger keeps every stage-1 verdict already written, under the old run id, and a later stage reads only the run this invocation arrives at (ADR-154, ADR-156).
- **Every later stage's run id moves with it.** Each one names its upstream run, back to stage 1 (ADR-048), and stages 3 to 6b also name `pipeline`, which this change touches. #320 plans for this: no corpus run is started until ADR-188, ADR-189 and ADR-190 are all on main, so the runs are paid for once. The arrangement is minted again, and `arrangementApproved` must name the new one.
- **The same corpus gets the same verdicts and representatives.** The rules and their order are moved, not rewritten.
- **A change to a stage-1 rule now moves stage 1's run id.** That was not true of `OutOfScope` or of the reasons while they sat in `pipeline`. This answers the question ADR-167 left open: an edit to `OutOfScope` alone re-mints stage 1.
- **The size rule's numbers and its in-parts test are still `extraction`'s.** A change to them moves stage 1 through its recorded settings, as ADR-171 §4 arranged, and not through its implementation version. That is unchanged.

## Tests

Pinned by measurement first. A throwaway probe ran today's `ByteLevelReductionTasklet` over three byte-identical copies and two content identities that share a size, with creation times set by hand. It hashed every member of a size group and no file of a unique size. Each content identity resolved to its earliest-created member, ties going to the lowest path, and every superseded occurrence's reason named the representative's path. The tests below expect exactly that.

- **`corpus.OutOfScopeTest`** (new, pure): the kinds of file that are out of scope, and the older Word file that is not; the log reason, and no reason under an unset floor or under ten non-blank lines; all three size rules and their order, against today's numbers and against small ones handed in, which shows that `corpus` holds no number of its own.
- **`corpus.BrokenOrOutOfScopeTest`** (new, over the test database): `broken` with `BrokenCheck`'s reason; every kind out of scope with its reason; a format recorded for every file occurrence checked, removed ones included; the log rule before the size rule; the size rule read through the limits handed in; the tally; and `CheckingProgress` called once for each survivor, in order, with its outcome.
- **`corpus.ContentIdentityResolutionTest`** (new, over the test database): the representative and the reason; a hash for every member of a size group and none for a unique size; two content identities that share a size each resolving on their own; a file occurrence the first pass removed taking no part; and `HashingProgress`'s count and calls.
- **`pipeline.ByteLevelReductionTaskletTest`, `LogsAreOutOfScopeInvocationTest`** and every test that builds the tasklet: unedited, and still green. They pin the step end to end, the page included.
- **`RunIdentityGoldenTest`, `OperatorTextTest`, `ModuleBoundariesTest`**: unedited, and still green.

Until the types in §2 exist, the three new test classes do not compile. That is the handoff to the implementer.

## What this does not decide

- **Holding the survivor set in memory** ([#405](https://github.com/algernon28/vespera/issues/405)).
- **Whether the size rule should name the converter at all.** It is moved with its reasons word for word.
- **Stage 2's and 6b's rules**, which are ADR-189's and ADR-190's.
