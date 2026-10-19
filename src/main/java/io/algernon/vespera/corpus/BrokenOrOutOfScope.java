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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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

    /** The version of the documentation generators' marks stage 1 knows (ADR-277 section 4), recorded in its configuration consumed. */
    public static final String GENERATOR_MARKS = Code.MARKS_VERSION;

    /** The version of the list of build files (ADR-284 section 1), recorded in stage 1's configuration consumed. */
    public static final String BUILD_FILES = BuildFiles.VERSION;

    /** The version of the list of generators' companion files (ADR-284 section 5), recorded likewise. */
    public static final String GENERATOR_COMPANIONS = GeneratorCompanions.VERSION;

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

        record Code(String reason) implements Outcome {}

        record Kept() implements Outcome {}
    }

    /**
     * Checks every survivor of {@code runId}, in the order {@code Verdicts.survivors} gives them, one at a
     * time as the pages are read and never the set at once (ADR-200), and returns the tally.
     * {@code logFloor} is {@code null} where no log rule applies (ADR-171 section 3). {@code nameEndings} are
     * the profile's {@code outOfScopeNameEndings} (ADR-256): lower case, each once, empty where the key is unset or unreadable. A file whose
     * bytes matched no signature and whose name ends with one is left out after the kinds {@link OutOfScope}
     * names and before the log and size rules, so it is not read for timestamps and is counted neither as a
     * log nor as too large. {@code codeEndings} are the profile's {@code codeNameEndings} (ADR-277): lower case,
     * each once, empty where the key is unset or unreadable. Code is verdicted after what is out of scope, an
     * ending both lists hold being out of scope, and before the log and size rules, so it is not read for timestamps.
     */
    public FormatMix verdictSurvivors(
            RunId runId,
            Path canonicalRoot,
            Double logFloor,
            List<String> nameEndings,
            List<String> codeEndings,
            CheckingProgress progress)
            throws Exception {
        return verdictSurvivors(runId, canonicalRoot, logFloor, nameEndings, codeEndings, progress, new MarkedPages());
    }

    /**
     * As above, and notes in {@code markedPages} the generator of every page kept as code for its mark, in
     * memory and never in a verdict's reason (ADR-284 section 5).
     */
    public FormatMix verdictSurvivors(
            RunId runId,
            Path canonicalRoot,
            Double logFloor,
            List<String> nameEndings,
            List<String> codeEndings,
            CheckingProgress progress,
            MarkedPages markedPages)
            throws Exception {
        Map<String, Integer> codeEndingCounts = new TreeMap<>();
        Map<String, Integer> codeGeneratorCounts = new TreeMap<>();
        Map<String, Integer> textEndingCounts = new TreeMap<>();
        int code = 0;
        int codeByEnding = 0;
        int codeByMark = 0;
        Map<DetectedFormat, Integer> byFormat = new LinkedHashMap<>();
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype = new LinkedHashMap<>();
        Map<String, Integer> unrecognisedLeadingBytes = new LinkedHashMap<>();
        int[] byTimestampBand = new int[FormatMix.TIMESTAMP_BANDS];
        int fewerThanTenLines = 0;
        int outOfScope = 0;
        int logs = 0;
        int tooLarge = 0;
        int leftOutByName = 0;
        for (OccurrenceId occurrenceId : ledger.verdicts().survivors(runId)) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            BrokenCheck.Result result = BrokenCheck.check(canonicalRoot.resolve(facts.path().value()));
            // Recorded before the verdict, and for every occurrence rather than for the survivors: a
            // mix report that dropped the removed files would under-count exactly the formats that
            // fail most (ADR-095).
            detectedFormats.record(occurrenceId, runId, result.format(), result.subtype().orElse(null));
            countInTheMix(byFormat, bySubtype, unrecognisedLeadingBytes, result, canonicalRoot.resolve(facts.path().value()));
            if (result.format() == DetectedFormat.PLAIN_TEXT || result.format() == DetectedFormat.UNRECOGNISED) {
                textEndingCounts.merge(endingOf(canonicalRoot.resolve(facts.path().value())), 1, Integer::sum);
            }
            Optional<String> leftOut = Optional.empty();
            Optional<Code.Recognised> recognised = Optional.empty();
            boolean isLog = false;
            // Not every text file left out is left out for its size: an email is left out for its kind (ADR-252).
            boolean forItsSize = false;
            // Nor is it a log or too large when the profile's list of name endings leaves it out (ADR-256).
            boolean forItsName = false;
            if (!result.broken()) {
                leftOut = OutOfScope.reasonFor(result.format(), result.subtype());
                if (leftOut.isEmpty()) {
                    leftOut = OutOfScope.nameReason(
                            canonicalRoot.resolve(facts.path().value()), result.format(), nameEndings);
                    forItsName = leftOut.isPresent();
                }
                if (leftOut.isEmpty()) {
                    recognised = codeOf(canonicalRoot.resolve(facts.path().value()), result.format(), codeEndings, occurrenceId, progress);
                }
                if (leftOut.isEmpty() && recognised.isEmpty() && result.format() == DetectedFormat.PLAIN_TEXT) {
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
                        forItsSize = leftOut.isPresent();
                    }
                }
            }
            if (result.readFailed()) {
                progress.couldNotRead(occurrenceId, result.reason());
            }
            if (result.broken()) {
                ledger.verdicts().verdict(occurrenceId, runId, VerdictKind.BROKEN, result.reason());
            } else if (recognised.isPresent()) {
                ledger.verdicts().verdict(occurrenceId, runId, VerdictKind.CODE, recognised.get().reason());
                code++;
                if (recognised.get().ending().isPresent()) {
                    codeByEnding++;
                    codeEndingCounts.merge(recognised.get().ending().get(), 1, Integer::sum);
                } else {
                    codeByMark++;
                    markedPages.add(occurrenceId, recognised.get().generator().orElseThrow());
                    codeGeneratorCounts.merge(recognised.get().generator().orElseThrow(), 1, Integer::sum);
                }
            } else if (leftOut.isPresent()) {
                ledger.verdicts().verdict(occurrenceId, runId, VerdictKind.OUT_OF_SCOPE, leftOut.get());
                outOfScope++;
                if (isLog) {
                    logs++;
                } else if (forItsName) {
                    leftOutByName++;
                } else if (forItsSize) {
                    tooLarge++;
                }
            }
            progress.checked(
                    occurrenceId,
                    result.broken()
                            ? new Outcome.Broken(result.reason())
                            : recognised.isPresent()
                                    ? new Outcome.Code(recognised.get().reason())
                                    : leftOut.<Outcome>map(Outcome.LeftOut::new).orElseGet(Outcome.Kept::new));
        }
        return new FormatMix(
                byFormat,
                bySubtype,
                unrecognisedLeadingBytes,
                outOfScope,
                logs,
                tooLarge,
                leftOutByName,
                byTimestampBand,
                fewerThanTenLines,
                code,
                codeByEnding,
                codeByMark,
                codeEndingCounts,
                codeGeneratorCounts,
                textEndingCounts);
    }

    /**
     * Why the text file {@code file} is code, or empty where it is not or cannot be read: a file that cannot be
     * read is not code, and is said to {@code progress}.
     */
    private static Optional<Code.Recognised> codeOf(
            Path file, DetectedFormat format, List<String> codeEndings, OccurrenceId occurrenceId, CheckingProgress progress) {
        try {
            return Code.reasonFor(file, format, codeEndings);
        } catch (IOException e) {
            progress.codeUnreadable(occurrenceId, e);
            return Optional.empty();
        }
    }

    /** The ending of a file's name from its last dot, where that dot is not its first character, in lower case. */
    private static String endingOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : FormatMix.NO_ENDING;
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
