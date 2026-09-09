package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final ObjectProvider<ScoringRun> scoringRun;
    private final RelevanceDistribution relevanceDistribution;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;
    private final ProfileStore profileStore;
    private final Path root;
    private final Path workingDirectory;

    RelevanceReportTasklet(
            EmbeddingModelGate embeddingModelGate,
            ObjectProvider<ScoringRun> scoringRun,
            RelevanceDistribution relevanceDistribution,
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            ProfileStore profileStore,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.embeddingModelGate = embeddingModelGate;
        this.scoringRun = scoringRun;
        this.relevanceDistribution = relevanceDistribution;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.profileStore = profileStore;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            LOG.info("stage 5's relevance-report step is gated: no embedding model is named. Nothing was"
                    + " put to a person.");
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

        write(RelevanceLabellingReport.FILE_NAME, RelevanceLabellingReport.render(distribution, previews));
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
        DoclingResponse response = extractor.convert(file, contentHash, extractorIdentity);
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
