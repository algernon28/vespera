package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.corpus.ContentHash;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.DuplicateResolution;
import io.algernon.vespera.corpus.DuplicateResolution.Candidate;
import io.algernon.vespera.corpus.TimestampedLines;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stage 1: byte-level reduction. Two passes over one run, in order:
 *
 * <ol>
 *   <li>Every survivor of census is checked against {@link BrokenCheck}; a mechanically-corrupt
 *       occurrence is verdicted {@code broken} (ADR-068) — the cheapest filter in the cascade, so
 *       nothing broken ever reaches extraction. An intact one of a kind {@link OutOfScope} names is
 *       verdicted {@code out-of-scope} in the same pass (ADR-146), so it is never hashed either.
 *   <li>Every occurrence still surviving (re-read from the ledger, so {@code broken} occurrences are
 *       excluded automatically) is grouped by size, hashed within any group of two or more, and
 *       every content-identity group resolves to one representative — the rest verdicted
 *       {@code superseded-by} (ADR-067, ADR-069).
 * </ol>
 *
 * <p>Lives here rather than in {@code corpus} because {@code corpus} does not know what a stage is
 * (ADR-040); {@link BrokenCheck}, {@link ContentHash} and {@link DuplicateResolution} are the
 * capabilities, this tasklet is the stage that drives them in order.
 */
@Component
@StepScope
public class ByteLevelReductionTasklet implements Tasklet {

    /**
     * What stage 1 consumes (ADR-171 §4): the size ceiling, which lives in {@code extraction}, and the
     * log floor, which is the profile's. Stage 1's implementation version is {@code corpus}'s alone
     * (ADR-058), so a change to either has to be visible here to mint a new run. The floor is {@code
     * null} where the key is unset or unreadable.
     */
    record ConfigConsumed(long textSizeCeilingBytes, Double logTimestampShareFloor) {}

    /** The page stage 1 leaves beside the database: what its detection found across the corpus (ADR-095). */
    static final String FORMAT_MIX_FILE_NAME = "format-mix.html";

    /** How many leading bytes group the unrecognised branch, so one kind arriving in bulk is visible. */
    private static final int LEADING_BYTES_REPORTED = 4;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Logger log = LoggerFactory.getLogger(ByteLevelReductionTasklet.class);

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DetectedFormats detectedFormats;
    private final Path workingDirectory;
    private final ImplementationVersions implementationVersions;
    private final ProfileStore profileStore;
    private final Path root;

    public ByteLevelReductionTasklet(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            ImplementationVersions implementationVersions,
            ProfileStore profileStore,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.detectedFormats = detectedFormats;
        this.implementationVersions = implementationVersions;
        this.profileStore = profileStore;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Path canonicalRoot = Walk.canonicalRoot(root);
        // Stage 1 has no upstream of its own to read (ADR-157 §2), so it mints through RunMint built
        // inline from this tasklet's own Ledger and ImplementationVersions and from the job execution's
        // own context -- everything after it reads this invocation's own byte-level-reduction run from
        // InvocationRuns rather than looking it up over the walk.
        RunMint runMint = new RunMint(
                ledger,
                implementationVersions,
                new InvocationRuns(
                        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()));
        var walk = runMint.finishedWalk(canonicalRoot, "stage 1");
        Profile profile = profileStore.load();
        Double logFloor = logFloorOf(profile);
        RunId runId = runMint.mint(
                StageModules.BYTE_LEVEL_REDUCTION,
                configConsumed(logFloor),
                walk,
                Optional.empty());

        return TaskletSteps.once(
                ledger,
                runId,
                StepNames.BYTE_LEVEL_REDUCTION,
                // This step's own work under this run is already written, so there is nothing here to
                // do (ADR-115, ADR-116). This is what a content-derived identity was always for: the
                // same inputs name the same work, and work already done is recognised rather than
                // repeated.
                () -> log.info("Stage 1 (byte-level reduction) was already recorded under run {}", runId.value()),
                // Not finished: an invocation that stopped partway may have left rows behind under this
                // same run id. Discarding this step's own rows before working is ADR-115's other half
                // (ADR-116).
                () -> {
                    ledger.discardVerdicts(runId, VerdictKind.BROKEN, VerdictKind.OUT_OF_SCOPE, VerdictKind.SUPERSEDED_BY);
                    detectedFormats.discardForRun(runId);
                    contentIdentity.discardForRun(runId);
                },
                () -> {
                    log.info("Stage 1 (byte-level reduction) starting under run {}", runId.value());
                    verdictBrokenSurvivors(runId, canonicalRoot, logFloor);
                    resolveDuplicates(runId, canonicalRoot);
                    log.info("Stage 1 (byte-level reduction) finished under run {}", runId.value());
                    return true;
                });
    }

    private void verdictBrokenSurvivors(RunId runId, Path canonicalRoot, Double logFloor) throws Exception {
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction, broken check)", ledger.survivorCount(runId));
        Map<DetectedFormat, Integer> byFormat = new LinkedHashMap<>();
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype = new LinkedHashMap<>();
        Map<String, Integer> unrecognisedLeadingBytes = new LinkedHashMap<>();
        int[] byTimestampBand = new int[FormatMixReport.BANDS];
        int fewerThanTenLines = 0;
        int outOfScope = 0;
        int logs = 0;
        int tooLarge = 0;
        for (OccurrenceId occurrenceId : drain(ledger.survivors(runId))) {
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
                            countTimestamps(canonicalRoot.resolve(facts.path().value()), occurrenceId);
                    if (count.isPresent()) {
                        if (count.get().nonBlank() < TimestampedLines.MINIMUM_NON_BLANK_LINES) {
                            fewerThanTenLines++;
                        } else {
                            byTimestampBand[Math.min(FormatMixReport.BANDS - 1, count.get().wholePercent() / 10)]++;
                        }
                    }
                    leftOut = count.flatMap(counted -> OutOfScope.logReason(counted, logFloor));
                    isLog = leftOut.isPresent();
                    if (leftOut.isEmpty()) {
                        leftOut = OutOfScope.sizeReason(facts.sizeBytes());
                    }
                }
            }
            if (result.broken()) {
                ledger.verdict(occurrenceId, runId, VerdictKind.BROKEN, result.reason());
            } else if (leftOut.isPresent()) {
                ledger.verdict(occurrenceId, runId, VerdictKind.OUT_OF_SCOPE, leftOut.get());
                outOfScope++;
                if (isLog) {
                    logs++;
                } else if (result.format() == DetectedFormat.PLAIN_TEXT) {
                    tooLarge++;
                }
            }
            log.info(
                    "[byte-level-reduction] checked {} for damage -> {}",
                    occurrenceId.value(),
                    result.broken() ? "broken: " + result.reason() : leftOut.map(reason -> "out of scope: " + reason).orElse("kept"));
            progress.itemDone();
        }
        writeFormatMix(new FormatMixReport.Mix(
                byFormat,
                bySubtype,
                unrecognisedLeadingBytes,
                outOfScope,
                logs,
                tooLarge,
                byTimestampBand,
                fewerThanTenLines,
                logFloor));
        // The floor's measurement is this page (ADR-171 §3), pointed at the way stage 3 points its own.
        profileStore.save(profileStore
                .load()
                .withLogTimestampShareFloorMeasurement(new Measurement(
                        workingDirectory.resolve(FORMAT_MIX_FILE_NAME).toString(), Clock.systemUTC().instant())));
    }

    /** An Answered floor is the floor; an unset or unreadable one means no log rule (ADR-171 section 3). */
    private static Double logFloorOf(Profile profile) {
        return profile.logTimestampShareFloor().reading() instanceof NumericValue.Answered answered
                ? answered.number()
                : null;
    }

    private static String configConsumed(Double logFloor) {
        return JSON.writeValueAsString(new ConfigConsumed(DoclingClient.TEXT_SIZE_CEILING_BYTES, logFloor));
    }

    /** The count for a text file, or empty, with a warning, where it cannot be read: not a log, and not a failed step. */
    private Optional<TimestampedLines.Count> countTimestamps(Path file, OccurrenceId occurrenceId) {
        try {
            return Optional.of(TimestampedLines.count(file));
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "[byte-level-reduction] could not read {} to see whether it is a log, so it is not one: {}",
                    occurrenceId.value(),
                    e.toString());
            return Optional.empty();
        }
    }

    /**
     * Counts one examined occurrence into the mix. The leading bytes are re-read only for content
     * that matched nothing: that group is the one a later floor would be drawn from, and a total
     * with no shape to it says how much is unrecognised without saying what any of it is (ADR-095).
     */
    private void countInTheMix(
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

    private void writeFormatMix(FormatMixReport.Mix mix) {
        Path reportFile = workingDirectory.resolve(FORMAT_MIX_FILE_NAME);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(reportFile, FormatMixReport.render(mix), StandardCharsets.UTF_8);
            log.info("[byte-level-reduction] wrote the format mix report to {}", reportFile);
        } catch (IOException e) {
            throw new IllegalStateException("the format mix report could not be written to " + reportFile, e);
        }
    }

    /**
     * Re-reads the survivor set: {@code broken} occurrences verdicted above are excluded by the same
     * anti-join {@link Ledger#survivors} always runs, so this stage's own boundary needs no
     * application-level filtering.
     */
    private void resolveDuplicates(RunId runId, Path canonicalRoot) throws Exception {
        Map<Long, List<OccurrenceId>> bySize = new LinkedHashMap<>();
        for (OccurrenceId occurrenceId : drain(ledger.survivors(runId))) {
            long sizeBytes = factsFor(occurrenceId).sizeBytes();
            bySize.computeIfAbsent(sizeBytes, ignored -> new ArrayList<>()).add(occurrenceId);
        }

        // The hash pass's own denominator, and not the survivor count: a file whose size is unique to it
        // is never hashed at all (ADR-057), so counting it in would leave this pass reporting a fraction
        // of a total it will never reach.
        long toHash = bySize.values().stream()
                .filter(sameSize -> sameSize.size() >= 2)
                .mapToLong(List::size)
                .sum();
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction, content hash)", toHash);

        for (List<OccurrenceId> sameSize : bySize.values()) {
            if (sameSize.size() < 2) {
                continue;
            }
            resolveGroupSharingASize(runId, canonicalRoot, sameSize, progress);
        }
    }

    private void resolveGroupSharingASize(
            RunId runId, Path canonicalRoot, List<OccurrenceId> sameSize, StageProgress progress) throws Exception {
        Map<String, List<Candidate>> byHash = new HashMap<>();
        for (OccurrenceId occurrenceId : sameSize) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            String sha256 = ContentHash.sha256(canonicalRoot.resolve(facts.path().value()));
            contentIdentity.recordHash(occurrenceId, runId, sha256);
            byHash.computeIfAbsent(sha256, ignored -> new ArrayList<>())
                    .add(new Candidate(occurrenceId, facts.path(), facts.creationTime()));
            log.info("[byte-level-reduction] hashed {} -> {}", occurrenceId.value(), sha256);
            progress.itemDone();
        }

        for (List<Candidate> sameHash : byHash.values()) {
            if (sameHash.size() < 2) {
                continue;
            }
            verdictSuperseded(runId, DuplicateResolution.resolve(sameHash), sameHash);
        }
    }

    private void verdictSuperseded(RunId runId, DuplicateResolution.Resolution resolution, List<Candidate> group) {
        String representativePath = group.stream()
                .filter(candidate -> candidate.occurrenceId().equals(resolution.representative()))
                .findFirst()
                .orElseThrow()
                .path()
                .value();
        for (OccurrenceId superseded : resolution.superseded()) {
            contentIdentity.recordSupersededBy(superseded, runId, resolution.representative());
            ledger.verdict(
                    superseded,
                    runId,
                    VerdictKind.SUPERSEDED_BY,
                    "superseded by the representative at " + representativePath);
        }
    }

    private OccurrenceFacts factsFor(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
    }

    /** Reads a survivors reader to exhaustion, since both passes need the whole set, not one chunk. */
    private static List<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) throws Exception {
        List<OccurrenceId> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                read.add(id);
            }
        } finally {
            reader.close();
        }
        return read;
    }
}
