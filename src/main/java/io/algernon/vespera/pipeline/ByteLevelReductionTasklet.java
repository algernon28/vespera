package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenOrOutOfScope;
import io.algernon.vespera.corpus.CheckingProgress;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.ContentIdentityResolution;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.FormatMix;
import io.algernon.vespera.corpus.HashingProgress;
import io.algernon.vespera.corpus.TextSizeLimits;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.TextParts;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stage 1: byte-level reduction. Two passes over one run, in order:
 *
 * <ol>
 *   <li>Every survivor of census is checked against {@code BrokenCheck}; a mechanically-corrupt
 *       occurrence is verdicted {@code broken} (ADR-068) — the cheapest filter in the cascade, so
 *       nothing broken ever reaches extraction. An intact one of a kind {@code corpus} names as out of
 *       scope is verdicted {@code out-of-scope} in the same pass (ADR-146), so it is never hashed either.
 *   <li>Every occurrence still surviving (re-read from the ledger, so {@code broken} occurrences are
 *       excluded automatically) is grouped by size, hashed within any group of two or more, and
 *       every content-identity group resolves to one representative — the rest verdicted
 *       {@code superseded-by} (ADR-067, ADR-069).
 * </ol>
 *
 * <p>{@code corpus} holds the rules for both passes, so a change to one moves this stage's run id
 * (ADR-058); {@code pipeline} holds the step and its text: the run, the discard of an unfinished
 * attempt, the operator's lines, the format-mix page and the log floor's measurement (ADR-188).
 */
@Component
@StepScope
public class ByteLevelReductionTasklet implements Tasklet {

    /**
     * What stage 1 consumes (ADR-171 §4, ADR-178 §3): the size ceiling and the rule for converting text
     * over it in parts, both of which live in {@code extraction}, and the log floor, which is the
     * profile's. Stage 1's implementation version is {@code corpus}'s alone (ADR-058), so a change to any
     * of them has to be visible here to mint a new run. The floor is {@code null} where the key is unset
     * or unreadable.
     */
    record ConfigConsumed(long textSizeCeilingBytes, String textParts, Double logTimestampShareFloor) {}

    /** The page stage 1 leaves beside the database: what its detection found across the corpus (ADR-095). */
    static final String FORMAT_MIX_FILE_NAME = "format-mix.html";

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

    /**
     * The first pass, and the page and the profile measurement it earns. {@code corpus} holds the rules
     * and the tally; what is written here is the operator's text, the page, and the floor's measurement.
     */
    private void verdictBrokenSurvivors(RunId runId, Path canonicalRoot, Double logFloor) throws Exception {
        long survivors = TimedStatement.of(
                "Stage 1 (byte-level reduction)",
                "counting",
                "counted",
                "the survivors the broken check goes through",
                () -> ledger.survivorCount(runId));
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction, broken check)", survivors);
        FormatMix mix = new BrokenOrOutOfScope(ledger, detectedFormats, textSizeLimits())
                .verdictSurvivors(runId, canonicalRoot, logFloor, new CheckingProgress() {
                    @Override
                    public void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome) {
                        log.info(
                                "[byte-level-reduction] checked {} for damage -> {}",
                                occurrence.value(),
                                switch (outcome) {
                                    case BrokenOrOutOfScope.Outcome.Broken broken -> "broken: " + broken.reason();
                                    case BrokenOrOutOfScope.Outcome.LeftOut leftOut -> "out of scope: " + leftOut.reason();
                                    case BrokenOrOutOfScope.Outcome.Kept kept -> "kept";
                                });
                        progress.itemDone();
                    }

                    @Override
                    public void timestampsUnreadable(OccurrenceId occurrence, Exception cause) {
                        log.warn(
                                "[byte-level-reduction] could not read {} to see whether it is a log, so it is not one: {}",
                                occurrence.value(),
                                cause.toString());
                    }
                });
        writeFormatMix(new FormatMixReport.Mix(
                mix.byFormat(),
                mix.bySubtype(),
                mix.unrecognisedLeadingBytes(),
                mix.outOfScope(),
                mix.logs(),
                mix.tooLarge(),
                mix.byTimestampBand(),
                mix.fewerThanTenLines(),
                logFloor));
        // The floor's measurement is this page (ADR-171 §3), pointed at the way stage 3 points its own.
        profileStore.save(profileStore
                .load()
                .withLogTimestampShareFloorMeasurement(new Measurement(
                        workingDirectory.resolve(FORMAT_MIX_FILE_NAME).toString(), Clock.systemUTC().instant())));
    }

    /**
     * The second pass. {@code corpus} resolves content identity; this opens the progress counters over the
     * counts it reports and writes each hashed line. Three loops are counted (ADR-192): the sizes read, over
     * the survivors the pass will read ({@code Ledger.survivorCount}, timed from the call to {@code resolve} to
     * its first callback); the hashes, over the files sharing a
     * size; and the duplicates recorded, a running count opened when the hashing is announced, since how
     * many files are copies is known only afterwards.
     */
    private void resolveDuplicates(RunId runId, Path canonicalRoot) throws Exception {
        // The resolution counts its survivors before it asks the ledger for anything else and hands the answer
        // to toSize, so the time from here to toSize is that count's (ADR-199 section 2).
        TimedStatement.Started counting = TimedStatement.begin(
                "Stage 1 (byte-level reduction)", "counting", "counted", "the survivors whose sizes it reads");
        new ContentIdentityResolution(ledger, contentIdentity).resolve(runId, canonicalRoot, new HashingProgress() {
            private StageProgress sizes;
            private StageProgress progress;
            private StageProgress duplicates;

            @Override
            public void toSize(long survivors) {
                counting.end();
                sizes = StageProgress.over("Stage 1 (byte-level reduction, sizes read)", survivors);
            }

            @Override
            public void sized() {
                sizes.itemDone();
            }

            @Override
            public void toHash(long occurrences) {
                progress = StageProgress.over("Stage 1 (byte-level reduction, content hash)", occurrences);
                duplicates = StageProgress.running("Stage 1 (byte-level reduction, duplicates recorded)");
            }

            @Override
            public void hashed(OccurrenceId occurrence, String sha256) {
                log.info("[byte-level-reduction] hashed {} -> {}", occurrence.value(), sha256);
                progress.itemDone();
            }

            @Override
            public void supersededRecorded() {
                duplicates.itemDone();
            }
        });
    }

    /**
     * {@code extraction}'s limits on text, handed to {@code corpus}, which cannot ask for them (ADR-188).
     * This is wiring and not a rule: both numbers and the in-parts test are {@code extraction}'s.
     */
    private static TextSizeLimits textSizeLimits() {
        return new TextSizeLimits(
                DoclingClient.TEXT_SIZE_CEILING_BYTES,
                TextParts.LARGEST_TEXT_BYTES,
                (file, subtype, size) ->
                        TextParts.convertedInParts(file, DetectedFormat.PLAIN_TEXT, subtype.orElse(null), size));
    }

    /** An Answered floor is the floor; an unset or unreadable one means no log rule (ADR-171 section 3). */
    private static Double logFloorOf(Profile profile) {
        return profile.logTimestampShareFloor().reading() instanceof NumericValue.Answered answered
                ? answered.number()
                : null;
    }

    private static String configConsumed(Double logFloor) {
        return JSON.writeValueAsString(
                new ConfigConsumed(DoclingClient.TEXT_SIZE_CEILING_BYTES, TextParts.RULE, logFloor));
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
}
