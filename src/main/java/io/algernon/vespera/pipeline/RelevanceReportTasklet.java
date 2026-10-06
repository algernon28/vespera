package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabel;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 5's last step (ADR-088, #110): everything a person needs in order to choose a relevance
 * threshold, and no choice of its own.
 *
 * <p>Two files beside the database and the profile, never inside the corpus (ADR-054): a page
 * showing how the scores are spread and which sixty documents to judge, and a label file with one
 * answer per document, blank unless an answer is already recorded for the seed set (ADR-169). The
 * profile's threshold key is pointed at the page, and left unset —
 * ADR-088 is explicit that nothing writes the value, because a threshold guessed by the engine is
 * exactly what the profile's "authored by a person" rule exists to prevent.
 *
 * <p><b>It never declines to proceed.</b> Whatever shape the distribution turns out to have, this
 * step writes the page and finishes: the go/no-go ADR-028 asked for is a human reading of it, and a
 * mechanical shape test would be an unmeasured threshold of its own.
 *
 * <p>Gated exactly as the scoring step before it is, and for the same reason: with no model named,
 * no seed folder, no usable seed, or a seed file that would not open, no score exists to be spread
 * across bands, so there is nothing to put to a person. Since ADR-160 that is literally the same
 * preamble, and not only the same outcome.
 *
 * <p><b>There is no {@code finishStep} call in this class.</b> ADR-118 names this step and {@code
 * relevance-floor} as the only two that read the answers a person gave, and takes them out of
 * ADR-116's list for the reason ADR-116's own governing clause gives: a completion record is safe only
 * where a run's identity names everything the step consumes. Answers are keyed by path and seed set
 * (ADR-097) and no run names them -- deliberately, since a run id that moved when someone answered the
 * page would re-score the corpus in reply to its own question. So this step keeps only the second of
 * ADR-116's two rules: it does its work again on every invocation, and rewriting a file is its own
 * discard.
 */
@Component
@StepScope
class RelevanceReportTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceReportTasklet.class);

    /** The report's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 5 (relevance report)";

    private final DocumentOpening documentOpening;
    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final RelevanceDistribution relevanceDistribution;
    private final RelevanceLabels relevanceLabels;
    private final RelevanceFloor relevanceFloor;
    private final Ledger ledger;
    private final ProfileStore profileStore;
    private final Path root;
    private final Path workingDirectory;

    RelevanceReportTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            RelevanceDistribution relevanceDistribution,
            RelevanceLabels relevanceLabels,
            RelevanceFloor relevanceFloor,
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            ProfileStore profileStore,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.documentOpening = new DocumentOpening(extractor, extractorIdentity, hybridChunker);
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.relevanceDistribution = relevanceDistribution;
        this.relevanceLabels = relevanceLabels;
        this.relevanceFloor = relevanceFloor;
        this.ledger = ledger;
        this.profileStore = profileStore;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Every gate the scoring half consults is checked before anything resolves a run, and neither seed
     * gate is optional here (#141, ADR-160).
     *
     * <p><b>Both seed-usability questions are asked here too, not left to the missing scores.</b> This
     * step used to consult only the model and the seed walk, on the reasoning that with no usable seed
     * no survivor carries a score and the step shuts on that itself. It did shut, but only after
     * resolving the scoring run (then {@code ScoringRun}) to learn which run had no scores, and that
     * resolution minted a scoring run and a seed measurement run behind ADR-083's gate, which ADR-080
     * forbids -- and a scoring run behind ADR-155's, over a seed set missing a file (#309). So with a
     * model named, this step now shuts on either fact in the sentence its siblings use, and mints
     * nothing. Its own no-scores line below is for the state it was written for: every gate open, and
     * nothing scored.
     *
     * <p>{@link StageRuns#embeddingScoring} resolves the seed-measurement run, which refuses to exist
     * while the seed gate is shut. So a model named with no seed folder -- ADR-098's invocation 2 for
     * an operator who never took step zero -- threw out of this step and failed the whole job, in a
     * state every other step in stage 5 reports as gated and exits 0 on. A mistyped folder arrived the
     * same way: {@link SeedGate} swallows the resolution failure deliberately, because census already
     * recorded it and carried on (ADR-064), and this step turned that back into a failed invocation two
     * steps later.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's relevance-report step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();

        RunId scoring = stageRuns.embeddingScoring();

        RelevanceDistribution.Distribution distribution;
        TimedStatement.Started readingTheScores = TimedStatement.begin(STAGE, "reading", "read", "the scores");
        try {
            distribution = relevanceDistribution.measure(scoring);
            readingTheScores.end();
        } catch (java.util.NoSuchElementException nothingScored) {
            // The read finished and found no score: that is the gate's answer, not a statement that failed.
            readingTheScores.end();
            LOG.info("stage 5's relevance-report step is gated: no survivor carries a relevance score"
                    + " under {}, so there is no spread to report. Nothing was put to a person.",
                    scoring.value());
            return RepeatStatus.FINISHED;
        }

        Path canonicalRoot = Walk.canonicalRoot(root);
        List<RelevanceLabellingReport.Preview> previews = new ArrayList<>();
        List<RelevanceLabelFile.Entry> entries = new ArrayList<>();
        StageProgress sampledOpened = StageProgress.over(
                "Stage 5 (relevance report, sampled survivors)", distribution.sample().size());
        for (RelevanceDistribution.Sampled sampled : distribution.sample()) {
            String path = pathOf(sampled.occurrenceId());
            String seedPath = pathOf(sampled.winningSeedOccurrenceId());
            String opening = textOpeningOf(canonicalRoot, sampled.occurrenceId());
            previews.add(new RelevanceLabellingReport.Preview(sampled.occurrenceId(), path, seedPath, opening));
            entries.add(new RelevanceLabelFile.Entry(path, sampled, seedPath));
            sampledOpened.itemDone();
        }

        // The answers already given, re-banded against this run's own scores. That is ADR-088's
        // headline consequence made executable: a label is a fact about a document, so a re-score under
        // a new model re-reads what a person already answered rather than asking them again.
        Optional<String> seedSet = seedSet();
        Map<OccurrenceId, Boolean> answers =
                seedSet.map(set -> answersInThisWalk(set, scoring)).orElseGet(Map::of);

        write(
                RelevanceLabellingReport.FILE_NAME,
                RelevanceLabellingReport.render(
                        distribution,
                        previews,
                        TimedStatement.of(
                                STAGE, "reading", "read",
                                "the scores against the answers",
                                () -> relevanceDistribution.spreadOf(scoring, answers)),
                        ignoredFloor().orElse(null)));
        write(
                RelevanceLabelFile.FILE_NAME,
                RelevanceLabelFile.render(
                        scoring.value(),
                        TimedStatement.of(
                                        STAGE, "reading", "read", "the embedder identities", relevanceDistribution::anyEmbedderIdentity)
                                .orElse(modelName),
                        // The preamble's seed-walk gate is open, and SeedGate opens it only for a seed
                        // folder the profile names and that canonicalises, so this is always present
                        // (ADR-169 §4).
                        seedSet.orElseThrow(),
                        entries,
                        answers,
                        modelAnswersInThisWalk(seedSet.orElseThrow(), scoring)));
        pointTheThresholdKeyAtThePage();

        LOG.info(
                "Stage 5 (relevance report) finished under scoring run {}: {} scored document(s) spread"
                        + " over {} bands, {} put to a person",
                scoring.value(),
                distribution.scoredDocumentCount(),
                distribution.bands().size(),
                distribution.sample().size());
        return RepeatStatus.FINISHED;
    }

    /**
     * The floor this run declined to apply, where there is one.
     *
     * <p>Only the calibrated-elsewhere state produces a notice. An unset floor needs no explanation —
     * the whole page is the explanation — and an applicable one was applied, so saying anything about
     * it here would describe a removal the reader can see in the counts.
     */
    private Optional<RelevanceLabellingReport.IgnoredFloor> ignoredFloor() {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> currentIdentity = TimedStatement.of(
                STAGE, "reading", "read", "the embedder identities", () -> relevanceDistribution.embedderIdentityFor(modelName.get()));
        if (currentIdentity.isEmpty()) {
            return Optional.empty();
        }
        if (relevanceFloor.stateFor(currentIdentity.get(), STAGE)
                instanceof RelevanceFloor.CalibratedElsewhere elsewhere) {
            return Optional.of(new RelevanceLabellingReport.IgnoredFloor(
                    elsewhere.value(), elsewhere.calibratedUnder(), elsewhere.currentIdentity()));
        }
        return Optional.empty();
    }

    /**
     * Every answer given about {@code seedSet}, keyed by the occurrence this run knows each document
     * as.
     *
     * <p><b>This is the resolution ADR-097 leaves to whoever joins labels to scores.</b> A label is
     * keyed by the path, because that is what survives the re-walk census performs every invocation;
     * the scores it is banded against are keyed by occurrence, because they were derived under this
     * run. Bridging the two here is what makes an answer given weeks ago count today, and it is the
     * only place an occurrence id is wanted at all.
     *
     * <p><b>A path this walk does not hold drops out, and the page reports it as unanswered.</b> That
     * is the document renamed or removed since the question was put, and it is the failure direction
     * ADR-097 chose: a rename costs a re-question, where matching on content would have discarded an
     * answer that was still true every time a document was re-scanned or re-exported.
     */
    private Map<OccurrenceId, Boolean> answersInThisWalk(String seedSet, RunId runId) {
        Optional<WalkId> walk = ledger.walkOf(runId);
        if (walk.isEmpty()) {
            return Map.of();
        }
        Map<OccurrenceId, Boolean> answers = new LinkedHashMap<>();
        List<RelevanceLabel> recorded =
                TimedStatement.of(STAGE, "reading", "read", "the recorded answers", () -> relevanceLabels.forSeedSet(seedSet));
        StageProgress matched = StageProgress.over("Stage 5 (relevance report, answers matched)", recorded.size());
        for (RelevanceLabel label : recorded) {
            ledger.occurrenceId(walk.get(), label.path())
                    .ifPresent(occurrence -> answers.putIfAbsent(occurrence, label.relevant()));
            matched.itemDone();
        }
        return answers;
    }

    /** Which of the answers in this walk a local model set, by the model's name (ADR-197 §3). */
    private Map<OccurrenceId, String> modelAnswersInThisWalk(String seedSet, RunId runId) {
        Optional<WalkId> walk = ledger.walkOf(runId);
        if (walk.isEmpty()) {
            return Map.of();
        }
        Map<OccurrenceId, String> byOccurrence = new LinkedHashMap<>();
        Map<String, String> modelAnswers =
                TimedStatement.of(STAGE, "reading", "read", "the answers a model gave", () -> relevanceLabels.modelAnswers(seedSet));
        modelAnswers.forEach((path, model) -> ledger.occurrenceId(walk.get(), new OccurrencePath(path))
                .ifPresent(occurrence -> byOccurrence.put(occurrence, model)));
        return byOccurrence;
    }

    /** The seed folder the answers are about, canonicalised the way every other reader of it is. */
    private Optional<String> seedSet() {
        Profile profile = profileStore.load();
        if (!profile.seedFolder().isSet()) {
            return Optional.empty();
        }
        return Optional.of(Walk.canonicalRoot(Path.of(profile.seedFolder().value())).toString());
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
                .map(OccurrenceFacts::path)
                .map(path -> path.value())
                .orElse("(path not recorded)");
    }

    /**
     * The start of a document's extracted text, taken from its first chunk (ADR-152).
     *
     * <p>Read back through the extraction cache only, never converted: gate 3's step already converted
     * every survivor, and a cache hit under the file's own current hash is what {@link
     * DoclingExtractor#cached} answers. A page for a person to read is not a place to spend a Docling
     * call, and on a survivor whose bytes changed since stage 2 that call would convert bytes no score
     * was computed from.
     *
     * <p><b>A file that cannot be opened is a fact about that document, not a fault in this run.</b>
     * Hashing the file is the one archive access this method makes, and only the {@link
     * UncheckedIOException} it can throw is tolerated — the survivor stays in the sample and on the
     * page, with {@link DocumentOpening#FILE_COULD_NOT_BE_OPENED_FALLBACK} standing in for its opening and
     * one warning naming it. A cache miss under the hash that was read is tolerated the same way, with
     * {@link DocumentOpening#NO_CONVERSION_ON_RECORD_FALLBACK} in its place. Neither ever reaches Docling.
     * The reading itself is {@link DocumentOpening}'s, shared with {@code vespera label --auto}.
     */
    private String textOpeningOf(Path canonicalRoot, OccurrenceId occurrenceId) {
        Optional<OccurrenceFacts> facts = ledger.factsFor(occurrenceId);
        if (facts.isEmpty()) {
            return "(no text was extracted)";
        }
        return documentOpening
                .of(canonicalRoot, facts.get().path().value(), "occurrence " + occurrenceId.value())
                .text();
    }

    /**
     * Points the threshold key at the page the number is read off, and answers nothing (ADR-075's
     * shape, ADR-088's rule).
     */
    private void pointTheThresholdKeyAtThePage() {
        profileStore.save(profileStore
                .load()
                .withRelevanceScoreFloorMeasurement(
                        new Measurement(RelevanceLabellingReport.FILE_NAME, Instant.now())));
    }

    private void write(String fileName, String content) {
        Path file = workingDirectory.resolve(fileName);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }
}
