package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabel;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 5's last step (ADR-088, #110): everything a person needs in order to choose a relevance
 * threshold, and no choice of its own.
 *
 * <p>Two files beside the database and the profile, never inside the corpus (ADR-054): a page
 * showing how the scores are spread and which sixty documents to judge, and a label file with one
 * blank answer per document. The profile's threshold key is pointed at the page, and left unset —
 * ADR-088 is explicit that nothing writes the value, because a threshold guessed by the engine is
 * exactly what the profile's "authored by a person" rule exists to prevent.
 *
 * <p><b>It never declines to proceed.</b> Whatever shape the distribution turns out to have, this
 * step writes the page and finishes: the go/no-go ADR-028 asked for is a human reading of it, and a
 * mechanical shape test would be an unmeasured threshold of its own.
 *
 * <p>Gated exactly as the scoring step before it is, and for the same reason: with no model named,
 * no seed folder, or no usable seed, no score exists to be spread across bands, so there is nothing
 * to put to a person.
 */
@Component
@StepScope
class RelevanceReportTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceReportTasklet.class);

    /**
     * How much of a document's opening is shown on the page. Long enough to recognise what a document
     * is, short enough that sixty of them stay readable in one sitting.
     */
    private static final int TEXT_OPENING_CHARACTERS = 400;

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final RelevanceDistribution relevanceDistribution;
    private final RelevanceLabels relevanceLabels;
    private final RelevanceFloor relevanceFloor;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;
    private final ProfileStore profileStore;
    private final Path root;
    private final Path workingDirectory;

    RelevanceReportTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            ObjectProvider<ScoringRun> scoringRun,
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
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.scoringRun = scoringRun;
        this.relevanceDistribution = relevanceDistribution;
        this.relevanceLabels = relevanceLabels;
        this.relevanceFloor = relevanceFloor;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.profileStore = profileStore;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Both gates are checked before anything resolves a run, and the seed gate is not optional here
     * (#141).
     *
     * <p>{@link ScoringRun} resolves {@link SeedMeasurementRun}, which refuses to exist while the seed
     * gate is shut. So a model named with no seed folder -- ADR-098's invocation 2 for an operator who
     * never took step zero -- threw out of this step and failed the whole job, in a state every other
     * step in stage 5 reports as gated and exits 0 on. A mistyped folder arrived the same way: {@link
     * SeedGate} swallows the resolution failure deliberately, because census already recorded it and
     * carried on (ADR-064), and this step turned that back into a failed invocation two steps later.
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            LOG.info("stage 5's relevance-report step is gated: no embedding model is named. Nothing was"
                    + " put to a person.");
            return RepeatStatus.FINISHED;
        }
        if (seedGate.seedWalk().isEmpty()) {
            LOG.info("stage 5's relevance-report step is gated: no seed folder is named, or stage 4's"
                    + " gate is shut, or the seed walk has not finished. Nothing was put to a person.");
            return RepeatStatus.FINISHED;
        }

        ScoringRun scoring = scoringRun.getObject();

        RelevanceDistribution.Distribution distribution;
        try {
            distribution = relevanceDistribution.measure(scoring.runId());
        } catch (java.util.NoSuchElementException nothingScored) {
            LOG.info("stage 5's relevance-report step is gated: no survivor carries a relevance score"
                    + " under {}, so there is no spread to report. Nothing was put to a person.",
                    scoring.runId().value());
            return RepeatStatus.FINISHED;
        }

        Path canonicalRoot = Walk.canonicalRoot(root);
        List<RelevanceLabellingReport.Preview> previews = new ArrayList<>();
        List<RelevanceLabelFile.Entry> entries = new ArrayList<>();
        for (RelevanceDistribution.Sampled sampled : distribution.sample()) {
            String path = pathOf(sampled.occurrenceId());
            String seedPath = pathOf(sampled.winningSeedOccurrenceId());
            String opening = textOpeningOf(canonicalRoot, sampled.occurrenceId());
            previews.add(new RelevanceLabellingReport.Preview(sampled.occurrenceId(), path, seedPath, opening));
            entries.add(new RelevanceLabelFile.Entry(path, sampled, seedPath));
        }

        // The answers already given, re-banded against this run's own scores. That is ADR-088's
        // headline consequence made executable: a label is a fact about a document, so a re-score under
        // a new model re-reads what a person already answered rather than asking them again.
        Map<OccurrenceId, Boolean> answers =
                seedSet().map(seedSet -> answersInThisWalk(seedSet, scoring.runId())).orElseGet(Map::of);

        write(
                RelevanceLabellingReport.FILE_NAME,
                RelevanceLabellingReport.render(
                        distribution,
                        previews,
                        relevanceDistribution.spreadOf(scoring.runId(), answers),
                        ignoredFloor()));
        write(
                RelevanceLabelFile.FILE_NAME,
                RelevanceLabelFile.render(
                        scoring.runId().value(),
                        relevanceDistribution.anyEmbedderIdentity().orElse(modelName.get()),
                        entries));
        pointTheThresholdKeyAtThePage();

        LOG.info(
                "Stage 5 (relevance report) finished under scoring run {}: {} scored document(s) spread"
                        + " over {} bands, {} put to a person",
                scoring.runId().value(),
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
        Optional<String> currentIdentity = relevanceDistribution.embedderIdentityFor(modelName.get());
        if (currentIdentity.isEmpty()) {
            return Optional.empty();
        }
        if (relevanceFloor.stateFor(currentIdentity.get())
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
        for (RelevanceLabel label : relevanceLabels.forSeedSet(seedSet)) {
            ledger.occurrenceId(walk.get(), label.path())
                    .ifPresent(occurrence -> answers.putIfAbsent(occurrence, label.relevant()));
        }
        return answers;
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
     * The start of a document's extracted text, taken from its first chunk.
     *
     * <p>Read back through the extraction cache, so this costs no Docling call: gate 3's step already
     * converted every survivor, and a cache hit is what {@link DoclingExtractor#convert} returns.
     * Chunking is how {@code pipeline} reaches the text at all — the parse that turns a stored
     * response into text items belongs to {@code extraction} and is not this module's to call.
     */
    private String textOpeningOf(Path canonicalRoot, OccurrenceId occurrenceId) {
        Optional<OccurrenceFacts> facts = ledger.factsFor(occurrenceId);
        if (facts.isEmpty()) {
            return "(no text was extracted)";
        }
        Path file = canonicalRoot.resolve(facts.get().path().value());
        String contentHash = extractor.contentHashFor(file);
        DoclingResponse response = SeedConversions.convert(extractor, file, contentHash, extractorIdentity);
        List<Chunk> chunks = hybridChunker.chunk(response.rawResponse(), contentHash, ChunkingRule.DEFAULT);
        if (chunks.isEmpty()) {
            return "(no text was extracted)";
        }
        String opening = chunks.getFirst().text().strip();
        return opening.length() <= TEXT_OPENING_CHARACTERS
                ? opening
                : opening.substring(0, TEXT_OPENING_CHARACTERS) + "...";
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
