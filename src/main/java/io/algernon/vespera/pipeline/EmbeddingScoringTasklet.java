package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.ChunkEmbedder;
import io.algernon.vespera.embedding.LocalOllamaModel;
import io.algernon.vespera.embedding.OllamaClient;
import io.algernon.vespera.embedding.UnusableSeed;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 5's third step (ADR-084, #107, slice 2): named alone by an operator naming a model, this is
 * where the corpus is re-chunked from its cached Docling response. Gate 3's own shape is unlike the
 * earlier gates in stage 5 — it sits <em>between</em> the measurement run and the scoring run rather
 * than in front of both, so an invocation against an unset model still walks, extracts the seeds,
 * measures the mismatch and writes that report, ending here having recorded everything it learned
 * (ADR-080's rule, applied a third time).
 *
 * <p>Once the model is named and stage 5's earlier gates are open, every corpus survivor <em>and</em>
 * every usable seed is re-chunked from {@code extraction_cache}, read through {@link
 * DoclingExtractor#cached} under the key stage 2 (or seed extraction) recorded for it (ADR-206): no file
 * is opened and nothing is converted, so there are zero Docling calls — and each chunk is embedded and
 * stored as a vector via {@link ChunkEmbedder}, under gate 3's own run row (ADR-084). Both sides go
 * through the same path with no instruction: a seed chunk against a corpus chunk is two documents, not
 * a query against a document.
 *
 * <p><b>It sends nothing under a model Ollama does not serve on this machine</b> (ADR-202). Once the
 * gates are open and before the measurement run or the scoring run is resolved, the embedding model's name
 * is put through {@link LocalOllamaModel#refusalOf}, once, and every chunk is sent under that name. A
 * refusal stops the step as generation's stop does (ADR-111): one line at error level, the step failed, no
 * exception thrown, and no run minted. Where a gate is shut nothing is sent, so nothing is asked of Ollama.
 */
@Component
@StepScope
class EmbeddingScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingScoringTasklet.class);

    /** Stage 5c's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 5c (embedding scoring)";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;
    private final ChunkEmbedder chunkEmbedder;
    private final UnusableSeeds unusableSeeds;
    private final OllamaClient ollamaClient;
    private final ExtractionCacheKeys cacheKeys;

    EmbeddingScoringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            ChunkEmbedder chunkEmbedder,
            UnusableSeeds unusableSeeds,
            OllamaClient ollamaClient,
            JdbcTemplate jdbcTemplate) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.chunkEmbedder = chunkEmbedder;
        this.unusableSeeds = unusableSeeds;
        this.ollamaClient = ollamaClient;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's scoring step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();
        SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();

        // Before either run is resolved, so a refusal mints none (ADR-202 section 2), and once, so every
        // chunk is sent under the name that was checked.
        Optional<String> refusal = LocalOllamaModel.refusalOf(modelName, ollamaClient);
        if (refusal.isPresent()) {
            stopTheStep(contribution, chunkContext, modelName, refusal.get());
            return RepeatStatus.FINISHED;
        }

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId scoring = stageRuns.embeddingScoring();

        // Nothing is discarded (ADR-157 §5): a vector is content under an instrument, not a judgement
        // under a run (ADR-085), so it is keyed outside the run and re-embedding it would cost a second
        // call to Ollama for no reason at all.
        return TaskletSteps.once(
                ledger,
                scoring,
                StepNames.EMBEDDING_SCORING,
                () -> LOG.info("Stage 5c (embedding scoring) was already recorded under run {}", scoring.value()),
                () -> {},
                () -> {
                    RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
                    // Counted, and then gone through a page at a time: no set of the corpus survivors is held
                    // (ADR-211 section 6). Nothing this step writes is a verdict, so no page still to come
                    // is changed by what is done with the ones before it.
                    long survivorCount = TimedStatement.of(
                            STAGE,
                            "counting",
                            "counted",
                            "the corpus survivors",
                            () -> ledger.verdicts().survivorCount(measurementRun));
                    Set<OccurrenceId> usableSeeds = usableSeedOccurrences(seedWalk, measurementRun);
                    LOG.info(
                            "Stage 5c (embedding scoring) starting under scoring run {}: re-chunking and"
                                    + " embedding {} corpus survivor(s) and {} usable seed(s)",
                            scoring.value(),
                            survivorCount,
                            usableSeeds.size());
                    StageProgress survivorsDone =
                            StageProgress.over("Stage 5c (embedding scoring, corpus survivors)", survivorCount);
                    StageProgress survivorChunks =
                            StageProgress.running("Stage 5c (embedding scoring, corpus survivor chunks)");
                    for (OccurrenceId occurrenceId : ledger.verdicts().survivors(measurementRun)) {
                        rechunkAndEmbed(extractionRun, occurrenceId, modelName, survivorChunks);
                        survivorsDone.itemDone();
                    }
                    StageProgress seedsDone =
                            StageProgress.over("Stage 5c (embedding scoring, seeds)", usableSeeds.size());
                    StageProgress seedChunks = StageProgress.running("Stage 5c (embedding scoring, seed chunks)");
                    for (OccurrenceId occurrenceId : usableSeeds) {
                        rechunkAndEmbed(measurementRun, occurrenceId, modelName, seedChunks);
                        seedsDone.itemDone();
                    }
                    LOG.info("Stage 5c (embedding scoring) finished under scoring run {}", scoring.value());
                    return true;
                });
    }

    /**
     * Stops the step on an embedding model Ollama does not serve on this machine (ADR-202), and says why.
     *
     * <p>It records the failure rather than throwing one, as generation's stop does (ADR-111): an
     * exception would put a stack trace where the one line belongs. The step's status is what carries the
     * non-zero exit; the exit description beside it reaches no table, since the job repository is
     * resourceless (ADR-036), and is kept so that a failed step does not leave its own record silent about
     * why.
     */
    private static void stopTheStep(
            StepContribution contribution, ChunkContext chunkContext, String modelName, String refusal) {
        LOG.error(
                "stage 5's scoring step stopped: the embedding model {} was refused, so nothing was embedded"
                        + " and no run was minted -- {}",
                modelName,
                refusal);
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        stepExecution.setStatus(BatchStatus.FAILED);
        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(refusal));
    }

    /**
     * Every seed occurrence the measurement run found usable — both sides of ADR-020's eventual
     * comparison are embedded through the same {@link #rechunkAndEmbed} path (ADR-084), so a seed's
     * vector exists exactly where a corpus chunk's does, keyed the same way.
     */
    private Set<OccurrenceId> usableSeedOccurrences(SeedGate.SeedWalk seedWalk, RunId measurementRun) {
        Set<OccurrenceId> allSeeds = TimedStatement.of(
                STAGE, "reading", "read", "the seed walk's occurrences", () -> {
                    Set<OccurrenceId> ids = new HashSet<>();
                    for (OccurrenceId id : ledger.occurrences().occurrencesOf(seedWalk.walkId())) {
                        ids.add(id);
                    }
                    return ids;
                });
        Set<OccurrenceId> unusable = TimedStatement.of(STAGE, "reading", "read", "the unusable seeds", () -> unusableSeeds.forRun(measurementRun))
                .stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);
        return allSeeds;
    }

    /**
     * Re-chunks one document from the conversion on record for it and embeds each chunk. The key the
     * conversion is kept under is read from {@code keyRun}, the run that recorded it: stage 2's for a corpus
     * survivor, the measurement run's for a seed (ADR-206 section 4). No file is opened, and nothing is
     * converted: a conversion missing under a recorded key stops the step.
     */
    private void rechunkAndEmbed(
            RunId keyRun, OccurrenceId occurrenceId, String modelName, StageProgress chunksEmbedded) {
        String contentHash = cacheKeys.requireForOccurrence(occurrenceId, keyRun);
        DoclingResponse response = extractor.cached(contentHash, extractorIdentity)
                .orElseThrow(() -> new IllegalStateException("no conversion is cached under key " + contentHash
                        + ", recorded for occurrence " + occurrenceId.value() + " under run " + keyRun.value()
                        + ", for extractor identity " + extractorIdentity.value()));
        List<Chunk> chunks = hybridChunker.chunk(response.rawResponse(), contentHash, ChunkingRule.DEFAULT);
        for (Chunk chunk : chunks) {
            chunkEmbedder.embed(
                    contentHash,
                    hybridChunker.identity(),
                    ChunkingRule.DEFAULT.identity().value(),
                    chunk.ordinal(),
                    chunk.text(),
                    modelName);
            chunksEmbedded.itemDone();
        }
    }
}
