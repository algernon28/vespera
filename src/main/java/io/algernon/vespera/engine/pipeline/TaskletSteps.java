package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.SeedChunks;
import io.algernon.vespera.engine.embedding.SeedVectors;
import io.algernon.vespera.engine.embedding.Topic;
import io.algernon.vespera.engine.embedding.TopicInference;
import io.algernon.vespera.engine.embedding.UnusableSeed;
import io.algernon.vespera.engine.embedding.UnusableSeeds;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.ExtractionCacheKeys;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.ListedPages;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/**
 * The one place a plain tasklet step is built (ADR-131): a name, the job repository, the transaction
 * manager, the holder of the closing lines a tasklet records (ADR-364 §4), and the tasklet to run. Every stage whose step is a single corpus-wide tasklet reaches
 * here rather than repeating the same three {@link StepBuilder} lines in its own configuration.
 *
 * <p>A <b>deep module</b> in the sense of the codebase-design vocabulary: a small interface — four
 * of the five parameters are what every call site already has to hand — behind the two facts a
 * caller would otherwise have to know, that a tasklet step is a {@code StepBuilder} tasklet and how
 * it is made to run outside a transaction. The <b>leverage</b> is that a new plain tasklet step is
 * one call; the <b>locality</b> is that the transaction attribute census needs is written once.
 *
 * <p>The seam is deliberately narrow. Chunk-oriented steps are not built here: extraction, seed
 * extraction and stage 4's signature step each have a reader, a processor or writer and fault
 * tolerance of their own, and pretending their shape is this one would turn a deep module into a
 * shallow one with a parameter for every difference.
 */
final class TaskletSteps {

    private TaskletSteps() {
    }

    /**
     * A tasklet step that participates in a transaction, which is Spring Batch's own default for a
     * tasklet step and the right shape for every stage whose unit of work is the whole step.
     */
    static Step taskletStep(
            String name,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RecordedClosingLines recordedClosingLines,
            Tasklet tasklet) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(new StepFailureClosingLine())
                .listener(recordedClosingLines)
                .build();
    }

    /**
     * As {@link #taskletStep(String, JobRepository, PlatformTransactionManager, Tasklet)}, with a
     * listener around the step. Spring Batch calls {@code beforeStep} before the step's transaction
     * opens, so what the listener writes there is committed on its own, ahead of the tasklet's and not
     * rolled back with it (ADR-182 section 2.3).
     */
    static Step taskletStep(
            String name,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RecordedClosingLines recordedClosingLines,
            Tasklet tasklet,
            StepExecutionListener listener) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(new StepFailureClosingLine())
                .listener(recordedClosingLines)
                .listener(listener)
                .build();
    }

    /**
     * Census's shape: a tasklet step that runs outside any transaction of its own, because a walk's
     * unit of durability is its checkpoint rather than the step (ADR-055). Declaring the step
     * {@code NOT_SUPPORTED} leaves {@code WalkRecorder} to commit at its own cadence, where a
     * step-wide transaction would hold every checkpoint until the whole corpus had been walked.
     */
    static Step taskletStepOutsideAnyTransaction(
            String name,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RecordedClosingLines recordedClosingLines,
            Tasklet tasklet) {
        DefaultTransactionAttribute outsideAnyTransaction =
                new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .transactionAttribute(outsideAnyTransaction)
                .listener(new StepFailureClosingLine())
                .listener(recordedClosingLines)
                .build();
    }

    /** What a tasklet does at two of the three points {@link #once} calls into, keeping its own text. */
    @FunctionalInterface
    interface StepAction {
        void run() throws Exception;
    }

    /**
     * The work a tasklet does under a run, once past its own gate and discard — {@code true} where it
     * counts as the step's own work being wholly done, {@code false} where it is not (ADR-157 §5).
     */
    @FunctionalInterface
    interface StepWork {
        boolean run() throws Exception;
    }

    /**
     * The skip-if-done shell nine tasklets share (ADR-157 §5, amending ADR-131): byte-level reduction,
     * content census, redundancy resolution, seed/corpus comparison, embedding scoring, relevance
     * scoring, clustering, arrangement and generation.
     *
     * <p>If {@code run}'s own work under {@code step} is already recorded, runs {@code alreadyRecorded}
     * and stops — every "already recorded" branch keeps its own sentence and its own logger, and {@link
     * ArrangementTasklet} keeps writing {@code arrangement.html} from that branch (ADR-154 §2).
     * Otherwise runs {@code discard}, then {@code work}, and records {@code step} finished under {@code
     * run} only where {@code work} answered {@code true} — three tasklets have a path that ends without
     * recording completion, and this is what keeps that path (ADR-116).
     *
     * <p>A function rather than a base class: clustering and arrangement check their gates, and reach
     * their own run, in a different order from the rest, so each tasklet keeps its own preamble and
     * calls this last.
     */
    static RepeatStatus once(
            Ledger ledger, RunId run, String step, StepAction alreadyRecorded, StepAction discard, StepWork work)
            throws Exception {
        if (ledger.runs().stepFinished(run, step)) {
            alreadyRecorded.run();
            return RepeatStatus.FINISHED;
        }
        discard.run();
        // the step's one transaction commits only when it ends, so its verdicts are counted as they are inserted
        if (ledger.verdicts().countingAtInsert(work::run)) {
            ledger.runs().finishStep(run, step);
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * Every usable seed's own content hash, read once from the key seed extraction recorded under the
     * measurement run (ADR-206 section 3), and how many chunks the chunker cut it into, so {@code RelevanceScoring}
     * never has to touch a file. Which seeds that leaves out is {@code embedding}'s rule (ADR-231 section 2a), not
     * this method's. Shared by the two steps that read the seeds' vectors back (stage 5d and 6a), each naming its
     * own statements, and its own counter by {@code counterOver}, so that the label stays literal where it is spelled.
     */
    static Map<OccurrenceId, SeedChunks> seedContentHashes(
            String stage,
            java.util.function.LongFunction<StageProgress> counterOver,
            SeedGate.SeedWalk seedWalk,
            RunId measurementRun,
            Ledger ledger,
            UnusableSeeds unusableSeeds,
            ExtractionCacheKeys cacheKeys,
            HybridChunker hybridChunker) {
        Set<OccurrenceId> allSeeds = TimedStatement.of(
                stage, "reading", "read", "the seed walk's occurrences", () -> {
                    Set<OccurrenceId> ids = new HashSet<>();
                    for (OccurrenceId id : ledger.occurrences().occurrencesOf(seedWalk.walkId())) {
                        ids.add(id);
                    }
                    return ids;
                });
        Set<OccurrenceId> unusable = TimedStatement.of(
                        stage, "reading", "read", "the unusable seeds", () -> unusableSeeds.forRun(measurementRun))
                .stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);

        // Asked a page of seeds to a statement, the keys and then their chunk counts (ADR-245), and counted as
        // each page is read, so that the counter moves while the reads are made (ADR-246).
        StageProgress read = counterOver.apply(allSeeds.size());
        Map<OccurrenceId, SeedChunks> contentHashes = new LinkedHashMap<>();
        ListedPages.each(allSeeds, page -> {
            Map<OccurrenceId, String> keys = cacheKeys.requireForOccurrences(page, measurementRun);
            Map<String, Integer> chunkCounts =
                    hybridChunker.chunkCounts(new HashSet<>(keys.values()), ChunkingRule.DEFAULT);
            for (OccurrenceId seedOccurrenceId : page) {
                String contentHash = keys.get(seedOccurrenceId);
                contentHashes.put(
                        seedOccurrenceId, new SeedChunks(contentHash, chunkCounts.getOrDefault(contentHash, 0)));
                read.itemDone();
            }
        });
        return contentHashes;
    }

    /**
     * The seeds' vectors by each seed's path in the seed walk, for ADR-399's topics: no embedding call and no read of
     * its own beyond the paths. A seed with no resident vector has no passage.
     */
    static Map<String, SeedVectors> seedVectorsByPath(
            Ledger ledger,
            Map<OccurrenceId, SeedChunks> seedContentHashes,
            Map<OccurrenceId, List<float[]>> residentSeedVectors) {
        Map<OccurrenceId, OccurrencePath> paths = ledger.occurrences().pathsOf(residentSeedVectors.keySet());
        Map<String, SeedVectors> bySeedPath = new LinkedHashMap<>();
        for (Map.Entry<OccurrenceId, List<float[]>> seed : residentSeedVectors.entrySet()) {
            OccurrencePath path = paths.get(seed.getKey());
            SeedChunks chunks = seedContentHashes.get(seed.getKey());
            if (path != null && chunks != null) {
                bySeedPath.put(path.value(), new SeedVectors(chunks.contentHash(), seed.getValue()));
            }
        }
        return bySeedPath;
    }

    /** The topics the seeds' passages fall into, found as the scoring step finds them (ADR-399). */
    static List<Topic> topicsFound(StageRuns stageRuns, Map<String, SeedVectors> bySeedPath) {
        return TopicInference.over(bySeedPath, stageRuns.topicNeighbours(), stageRuns.topicResolution());
    }
}