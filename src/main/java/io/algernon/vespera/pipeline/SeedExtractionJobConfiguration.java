package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Optional;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's first step (ADR-083): the seed set, extracted and chunked.
 *
 * <p>Chunk-oriented rather than a tasklet, like stage 2's: the work is per-document — one conversion,
 * one chunking — which is exactly what a reader/processor/writer is for.
 *
 * <p>The step is always in the job, and the <em>reader</em> is what the gate closes. A step
 * conditionally absent from the job would make the stage order depend on the profile, so
 * {@code CensusInvocationTest}'s order assertion would say something different on every fixture; an
 * empty reader instead means the pass runs, reads nothing, mints nothing, and the job succeeds —
 * ADR-047's "the pipeline never blocks, it terminates and resumes on re-invocation".
 */
@Configuration
public class SeedExtractionJobConfiguration {

    /** The step's name, and stage 5's first entry in the job's stage order. */
    static final String STEP_NAME = "seed-extraction";

    /**
     * The step's chunk size, matching stage 2's for the same reason: each item is up to a 5-minute
     * HTTP call, so a small chunk bounds how much already-cached work a rolled-back chunk redoes. A
     * seed folder is a few dozen documents, so this is not a throughput number.
     */
    static final int CHUNK_SIZE = 10;

    @Bean
    Step seedExtractionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            OccurrenceReader seedReader,
            ObjectProvider<SeedExtractionItemProcessor> seedExtractionItemProcessor,
            SeedExtractionItemWriter seedExtractionItemWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<OccurrenceId, SeedExtractionOutcome>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(seedReader)
                .processor(item -> seedExtractionItemProcessor.getObject().process(item))
                .writer(seedExtractionItemWriter)
                .listener(seedExtractionItemWriter)
                .build();
    }

    /**
     * Every occurrence of the seed folder's finished walk — or nothing at all, while the gate is shut.
     *
     * <p>{@link Ledger#occurrencesOf} rather than {@code survivors}: no verdict is ever written against
     * a seed occurrence, so there is no survivor set to read, and filtering seeds through the corpus's
     * removals would drop a seed that happens to duplicate a corpus file.
     */
    @Bean
    @StepScope
    OccurrenceReader seedReader(Ledger ledger, SeedGate seedGate) {
        Optional<SeedGate.SeedWalk> seedWalk = seedGate.seedWalk();
        if (seedWalk.isEmpty()) {
            return OccurrenceReader.yieldingNothing();
        }
        return new OccurrenceReader(ledger.occurrencesOf(seedWalk.get().walkId()));
    }
}
