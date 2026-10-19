package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenOrOutOfScope;
import io.algernon.vespera.corpus.CheckingProgress;
import io.algernon.vespera.corpus.CodeSets;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.ContentIdentityResolution;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.FormatMix;
import io.algernon.vespera.corpus.HashingProgress;
import io.algernon.vespera.corpus.MarkedFolders;
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
import io.algernon.vespera.profile.NameEndingsValue;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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
 * Stage 1: byte-level reduction. Three passes over one run, in order (ADR-284 section 6):
 *
 * <ol>
 *   <li>Every survivor of census is checked against {@code BrokenCheck}; a mechanically-corrupt
 *       occurrence is verdicted {@code broken} (ADR-068) — the cheapest filter in the cascade, so
 *       nothing broken ever reaches extraction. An intact one of a kind {@code corpus} names as out of
 *       scope is verdicted {@code out-of-scope} in the same pass (ADR-146), so it is never hashed either.
 *   <li>The set pass, {@code CodeSets}, finds the code sets and keeps their build files and the files of a
 *       generated tree as {@code code} too, before the format-mix page is written and before any hashing.
 *   <li>Every occurrence still surviving (re-read from the ledger, so {@code broken} occurrences are
 *       excluded automatically) is grouped by size, hashed within any group of two or more, and
 *       every content-identity group resolves to one representative — the rest verdicted
 *       {@code superseded-by} (ADR-067, ADR-069).
 * </ol>
 *
 * <p>{@code corpus} holds the rules for the passes, so a change to one moves this stage's run id
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
     * or unreadable. The name endings the profile's {@code outOfScopeNameEndings} lists (ADR-256 §6) are
     * recorded in their canonical form, lower case, each once, sorted, and written as {@code []} where the
     * key is unset or unreadable, as the floor's {@code null} is: stage 1 acts on them, and its version
     * does not name {@code profile}, so only this record can move its run id when they change. The endings of
     * {@code codeNameEndings} are recorded the same way (ADR-277 section 9), beside the version of the
     * documentation generators' marks built into {@code corpus}, and after them the versions of the lists of build
     * files and of generators' companion files (ADR-284 section 10).
     */
    record ConfigConsumed(
            long textSizeCeilingBytes,
            String textParts,
            Double logTimestampShareFloor,
            List<String> outOfScopeNameEndings,
            List<String> codeNameEndings,
            String generatorMarks,
            String buildFiles,
            String generatorCompanions) {}

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
        List<String> nameEndings = nameEndingsOf(profile.outOfScopeNameEndings());
        List<String> codeEndings = nameEndingsOf(profile.codeNameEndings());
        RunId runId = runMint.mint(
                StageModules.BYTE_LEVEL_REDUCTION,
                configConsumed(logFloor, nameEndings, codeEndings),
                walk,
                Optional.empty());

        try {
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
                        ledger.verdicts().discardVerdicts(
                                runId,
                                VerdictKind.BROKEN,
                                VerdictKind.OUT_OF_SCOPE,
                                VerdictKind.CODE,
                                VerdictKind.SUPERSEDED_BY);
                        detectedFormats.discardForRun(runId);
                        detectedFormats.codeSets().discardForRun(runId);
                        contentIdentity.discardForRun(runId);
                    },
                    () -> {
                        log.info("Stage 1 (byte-level reduction) starting under run {}", runId.value());
                        verdictBrokenSurvivors(runId, canonicalRoot, logFloor, nameEndings, codeEndings);
                        resolveDuplicates(runId, canonicalRoot);
                        log.info("Stage 1 (byte-level reduction) finished under run {}", runId.value());
                        return true;
                    });
        } catch (ArchiveGoneException archiveGone) {
            // Stage 1 has no closing line of its own, so the one an operator needs is written here, before
            // the exception leaves the step and its transaction rolls everything back (ADR-210 section 2).
            log.error(
                    "Stage 1 (byte-level reduction) failed and is not recorded as finished: {}. Reconnect the"
                            + " archive and run the same command again.",
                    archiveGone.getMessage());
            throw archiveGone;
        }
    }

    /**
     * The first pass, and the page and the profile measurement it earns. {@code corpus} holds the rules
     * and the tally; what is written here is the operator's text, the page, and the floor's measurement.
     */
    private void verdictBrokenSurvivors(
            RunId runId, Path canonicalRoot, Double logFloor, List<String> nameEndings, List<String> codeEndings)
            throws Exception {
        long survivors = TimedStatement.of(
                "Stage 1 (byte-level reduction)",
                "counting",
                "counted",
                "the survivors the broken check goes through",
                () -> ledger.verdicts().survivorCount(runId));
        ProgressPage.entered(survivors);
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction, broken check)", survivors);
        MarkedFolders markedFolders = new MarkedFolders();
        FormatMix mix = new BrokenOrOutOfScope(ledger, detectedFormats, textSizeLimits())
                .verdictSurvivors(runId, canonicalRoot, logFloor, nameEndings, codeEndings, new CheckingProgress() {
                    @Override
                    public void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome) {
                        log.info(
                                "[byte-level-reduction] checked {} for damage -> {}",
                                occurrence.value(),
                                switch (outcome) {
                                    case BrokenOrOutOfScope.Outcome.Broken broken -> "broken: " + broken.reason();
                                    case BrokenOrOutOfScope.Outcome.LeftOut leftOut -> "out of scope: " + leftOut.reason();
                                    case BrokenOrOutOfScope.Outcome.Code code -> "code: " + code.reason();
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

                    @Override
                    public void codeUnreadable(OccurrenceId occurrence, Exception cause) {
                        log.warn(
                                "[byte-level-reduction] could not read {} to see whether it is code, so it is not: {}",
                                occurrence.value(),
                                cause.toString());
                    }

                    @Override
                    public void couldNotRead(OccurrenceId occurrence, String reason) {
                        // Before the verdict is written: if the archive has gone, nothing is marked for it
                        // (ADR-210 section 2). Otherwise the file is marked broken, as it always was.
                        new CorpusRootCheck(canonicalRoot).requireListable();
                    }
                }, markedFolders);
        CodeSets.Tally codeSets = detectedFormats.codeSets().pass(ledger, runId, canonicalRoot, markedFolders, codeEndings, new CodeSets.Progress() {
            @Override
            public void kept(OccurrenceId occurrence, String reason) {
                log.info("[byte-level-reduction] kept {} with its code set -> code: {}", occurrence.value(), reason);
            }

            @Override
            public void unreadable(OccurrenceId occurrence, Exception cause) {
                log.warn(
                        "[byte-level-reduction] could not read {} to see whether it reads as text, so it is left as it was: {}",
                        occurrence.value(),
                        cause.toString());
            }

            @Override
            public void markUnreadable(OccurrenceId occurrence, Exception cause) {
                log.warn(
                        "[byte-level-reduction] could not read {} to see which generator's mark it carries, so it is kept with none: {}",
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
                mix.leftOutByName(),
                mix.byTimestampBand(),
                mix.fewerThanTenLines(),
                mix.code(),
                mix.codeByEnding(),
                mix.codeByMark(),
                mix.codeEndingCounts(),
                mix.codeGeneratorCounts(),
                mix.textEndingCounts(),
                logFloor,
                nameEndings,
                codeEndings,
                codeSets));
        // The floor's measurement is this page (ADR-171 §3), pointed at the way stage 3 points its own, and
        // so is the name endings' (ADR-256 §7).
        Measurement thePage =
                new Measurement(workingDirectory.resolve(FORMAT_MIX_FILE_NAME).toString(), Clock.systemUTC().instant());
        profileStore.save(profileStore
                .load()
                .withLogTimestampShareFloorMeasurement(thePage)
                .withOutOfScopeNameEndingsMeasurement(thePage)
                .withCodeNameEndingsMeasurement(thePage));
    }

    /**
     * The second pass. {@code corpus} resolves content identity; this opens the progress counters over the
     * counts it reports and writes each hashed line. Three loops are counted (ADR-192): the sizes read, over
     * the survivors the pass will read ({@code Verdicts.survivorCount}, timed from the call to {@code resolve} to
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
            public void notHashed(OccurrenceId occurrence, IOException cause) {
                // The question first: a disk that has gone stops the step and nothing is left half done
                // (ADR-210 section 2). Otherwise the file is left unhashed, with no verdict, for stage 2 to
                // read, and counted as gone through so the total is still reached.
                new CorpusRootCheck(canonicalRoot).requireListable();
                log.warn(
                        "[byte-level-reduction] could not read {} to hash it, so it is left unhashed for stage 2: {}",
                        occurrence.value(),
                        cause.toString());
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

    /** The answered endings, canonical; none where the key is unset or unreadable (ADR-256 §5, ADR-277 §2). */
    private static List<String> nameEndingsOf(NameEndingsValue key) {
        return key.reading() instanceof NameEndingsValue.Answered answered
                ? answered.endings()
                : List.of();
    }

    private static String configConsumed(Double logFloor, List<String> nameEndings, List<String> codeEndings) {
        return JSON.writeValueAsString(new ConfigConsumed(
                DoclingClient.TEXT_SIZE_CEILING_BYTES,
                TextParts.RULE,
                logFloor,
                nameEndings,
                codeEndings,
                BrokenOrOutOfScope.GENERATOR_MARKS,
                BrokenOrOutOfScope.BUILD_FILES,
                BrokenOrOutOfScope.GENERATOR_COMPANIONS));
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
