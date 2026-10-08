package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stage 1's first pass: every survivor is checked against {@link BrokenCheck}, and the broken and the
 * out-of-scope are verdicted (ADR-188, moved from {@code pipeline}; fed by
 * {@code Verdicts.survivors}, an {@code Iterable}, since ADR-209).
 *
 * <p>A mechanically-corrupt occurrence is verdicted {@code broken} (ADR-068), the cheapest filter in the
 * cascade, so nothing broken ever reaches extraction. An intact one of a kind {@link OutOfScope} names is
 * verdicted {@code out-of-scope} in the same pass (ADR-146), so it is never hashed either. The format of
 * every occurrence checked is recorded before any verdict (ADR-095), and counted into the mix.
 *
 * <p>This logs nothing per item: what an operator reads is written by the caller, through
 * {@link CheckingProgress}.
 */
public final class BrokenOrOutOfScope {

    /** How many leading bytes group the unrecognised branch, so one kind arriving in bulk is visible. */
    private static final int LEADING_BYTES_REPORTED = 4;

    private final Ledger ledger;
    private final DetectedFormats detectedFormats;
    private final TextSizeLimits limits;

    public BrokenOrOutOfScope(Ledger ledger, DetectedFormats detectedFormats, TextSizeLimits limits) {
        this.ledger = ledger;
        this.detectedFormats = detectedFormats;
        this.limits = limits;
    }

    /** What one survivor came to; given to {@link CheckingProgress} after its verdict, if any, is written. */
    public sealed interface Outcome {
        record Broken(String reason) implements Outcome {}

        record LeftOut(String reason) implements Outcome {}

        record Kept() implements Outcome {}
    }

    /**
     * Checks every survivor of {@code runId}, in the order {@code Verdicts.survivors} gives them, one at a
     * time as the pages are read and never the set at once (ADR-200), and returns the tally.
     * {@code logFloor} is {@code null} where no log rule applies (ADR-171 section 3).
     */
    public FormatMix verdictSurvivors(RunId runId, Path canonicalRoot, Double logFloor, CheckingProgress progress)
            throws Exception {
        Map<DetectedFormat, Integer> byFormat = new LinkedHashMap<>();
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype = new LinkedHashMap<>();
        Map<String, Integer> unrecognisedLeadingBytes = new LinkedHashMap<>();
        int[] byTimestampBand = new int[FormatMix.TIMESTAMP_BANDS];
        int fewerThanTenLines = 0;
        int outOfScope = 0;
        int logs = 0;
        int tooLarge = 0;
        for (OccurrenceId occurrenceId : ledger.verdicts().survivors(runId)) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            BrokenCheck.Result result = BrokenCheck.check(canonicalRoot.resolve(facts.path().value()));
            // Recorded before the verdict, and for every occurrence rather than for the survivors: a
            // mix report that dropped the removed files would under-count exactly the formats that
            // fail most (ADR-095).
            detectedFormats.record(occurrenceId, runId, result.format(), result.subtype().orElse(null));
            countInTheMix(byFormat, bySubtype, unrecognisedLeadingBytes, result, canonicalRoot.resolve(facts.path().value()));
            Optional<String> leftOut = Optional.empty();
            boolean isLog = false;
            if (!result.broken()) {
                leftOut = OutOfScope.reasonFor(result.format(), result.subtype());
                if (leftOut.isEmpty() && result.format() == DetectedFormat.PLAIN_TEXT) {
                    Optional<TimestampedLines.Count> count =
                            countTimestamps(canonicalRoot.resolve(facts.path().value()), occurrenceId, progress);
                    if (count.isPresent()) {
                        if (count.get().nonBlank() < TimestampedLines.MINIMUM_NON_BLANK_LINES) {
                            fewerThanTenLines++;
                        } else {
                            byTimestampBand[Math.min(FormatMix.TIMESTAMP_BANDS - 1, count.get().wholePercent() / 10)]++;
                        }
                    }
                    leftOut = count.flatMap(counted -> OutOfScope.logReason(counted, logFloor));
                    isLog = leftOut.isPresent();
                    if (leftOut.isEmpty()) {
                        leftOut = OutOfScope.sizeReason(
                                canonicalRoot.resolve(facts.path().value()), facts.sizeBytes(), result.subtype(), limits);
                    }
                }
            }
            if (result.broken()) {
                ledger.verdicts().verdict(occurrenceId, runId, VerdictKind.BROKEN, result.reason());
            } else if (leftOut.isPresent()) {
                ledger.verdicts().verdict(occurrenceId, runId, VerdictKind.OUT_OF_SCOPE, leftOut.get());
                outOfScope++;
                if (isLog) {
                    logs++;
                } else if (result.format() == DetectedFormat.PLAIN_TEXT) {
                    tooLarge++;
                }
            }
            progress.checked(
                    occurrenceId,
                    result.broken()
                            ? new Outcome.Broken(result.reason())
                            : leftOut.<Outcome>map(Outcome.LeftOut::new).orElseGet(Outcome.Kept::new));
        }
        return new FormatMix(
                byFormat,
                bySubtype,
                unrecognisedLeadingBytes,
                outOfScope,
                logs,
                tooLarge,
                byTimestampBand,
                fewerThanTenLines);
    }

    /** The count for a text file, or empty, said to {@code progress}, where it cannot be read: not a log, and not a failed step. */
    private static Optional<TimestampedLines.Count> countTimestamps(
            Path file, OccurrenceId occurrenceId, CheckingProgress progress) {
        try {
            return Optional.of(TimestampedLines.count(file));
        } catch (IOException | RuntimeException e) {
            progress.timestampsUnreadable(occurrenceId, e);
            return Optional.empty();
        }
    }

    /**
     * Counts one examined occurrence into the mix. The leading bytes are re-read only for content
     * that matched nothing: that group is the one a later floor would be drawn from, and a total
     * with no shape to it says how much is unrecognised without saying what any of it is (ADR-095).
     */
    private static void countInTheMix(
            Map<DetectedFormat, Integer> byFormat,
            Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
            Map<String, Integer> unrecognisedLeadingBytes,
            BrokenCheck.Result result,
            Path file) {
        byFormat.merge(result.format(), 1, Integer::sum);
        result.subtype()
                .ifPresent(subtype -> bySubtype
                        .computeIfAbsent(result.format(), ignored -> new LinkedHashMap<>())
                        .merge(subtype, 1, Integer::sum));
        if (result.format() == DetectedFormat.UNRECOGNISED) {
            unrecognisedLeadingBytes.merge(leadingBytesOf(file), 1, Integer::sum);
        }
    }

    /** The first bytes of {@code file} as hex, or a stand-in where they cannot be read back. */
    private static String leadingBytesOf(Path file) {
        try (var in = Files.newInputStream(file)) {
            byte[] leading = in.readNBytes(LEADING_BYTES_REPORTED);
            StringBuilder hex = new StringBuilder();
            for (byte b : leading) {
                hex.append(String.format("%02X ", b));
            }
            return hex.toString().trim();
        } catch (IOException e) {
            return "unreadable";
        }
    }

    private OccurrenceFacts factsFor(OccurrenceId occurrenceId) {
        return ledger.occurrences().factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
    }
}
