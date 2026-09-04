package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.Tokenizer;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 2's own Batch wiring (extraction), kept apart from {@link CensusJobConfiguration} and
 * {@link Stage1JobConfiguration} for the same reason those are already separate: each stage
 * contributes its own step bean rather than growing one shared configuration class.
 *
 * <p>The first chunk-oriented step in the job (ADR: "stage 2 runs as a chunk-oriented Spring Batch
 * step (not a tasklet), so that its fault-tolerance mechanics — skip, circuit breaker — have the
 * machinery they need"), unlike stage 1's tasklet.
 */
@Configuration
public class Stage2JobConfiguration {

    /**
     * The step's chunk size — an implementer's default, not a spec-fixed number (unlike the timeout
     * and streak counts ADR-071 pins). Kept small because each item is up to a 5-minute HTTP call:
     * a small chunk bounds how much already-cached-and-therefore-cheap work a rolled-back chunk would
     * redo, and keeps verdict commits frequent.
     */
    static final int CHUNK_SIZE = 10;

    /**
     * Spring Batch's own cumulative skip limit — a generous backstop against a slowly-degrading
     * sidecar over a very long run, deliberately not the circuit breaker (ADR-071's own distinction).
     * An implementer's default: large enough that it is not what fires during a healthy run, however
     * long, since {@link Stage2CircuitBreaker}'s consecutive-streak count is the mechanism that
     * actually protects a run against a dead sidecar.
     */
    static final long SKIP_LIMIT = 10_000;

    @Bean
    Step stage2Step(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<OccurrenceId> stage2Reader,
            Stage2ItemProcessor stage2ItemProcessor,
            Stage2ItemWriter stage2ItemWriter,
            Stage2CircuitBreaker stage2CircuitBreaker,
            Stage2HealthCheckListener stage2HealthCheckListener) {
        return new StepBuilder(Stage2Run.STAGE, jobRepository)
                .<OccurrenceId, Stage2Outcome>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(stage2Reader)
                .processor(stage2ItemProcessor)
                .writer(stage2ItemWriter)
                .faultTolerant()
                .skip(ServiceScopeFailure.class)
                .skipLimit(SKIP_LIMIT)
                .listener(stage2CircuitBreaker)
                .listener(stage2HealthCheckListener)
                .build();
    }

    /**
     * The survivors reader (ADR-060), scoped to whichever run {@link Stage2Run} minted — itself scoped
     * to whichever walk the {@code root} job parameter names, never hard-coded to the corpus walk.
     */
    @Bean
    @StepScope
    ItemStreamReader<OccurrenceId> stage2Reader(Ledger ledger, Stage2Run stage2Run) {
        return ledger.survivors(stage2Run.runId());
    }

    /**
     * The engine identity {@code configConsumed} records (ADR-012: "the serving runtime is config, not
     * code"). Composed from the configured base URL, the only engine-selection knob this slice has: no
     * pipeline-selection profile key exists yet, and {@link io.algernon.vespera.extraction.DoclingClient}
     * always requests the same export format, so a different base URL is currently the only way this
     * design lets an operator point stage 2 at a differently-configured Docling.
     */
    @Bean
    ExtractorIdentity extractorIdentity(@Value("${vespera.docling.base-url}") String baseUrl) {
        return new ExtractorIdentity("docling-serve;base-url=" + baseUrl);
    }

    /**
     * The tokenizer {@link io.algernon.vespera.extraction.HybridChunker} budgets chunks against
     * (#49): a single wired-in value, per the ticket's own scope limit — no embedding-model or
     * tokenizer-selection logic belongs here yet.
     */
    @Bean
    Tokenizer tokenizer() {
        return new WordCountTokenizer();
    }

    /**
     * {@code pipeline}'s reading of the profile's tier-2 key (#48, ADR-070): {@code extraction} may
     * depend only on {@code ledger}, so this is read here, not there, and handed down as a plain
     * value.
     */
    @Bean
    DegenerateOutputConfidenceFloor degenerateOutputConfidenceFloor(ProfileStore profileStore) {
        Profile profile = profileStore.load();
        String value = profile.degenerateOutputConfidenceFloor().value();
        return new DegenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor().isSet()
                ? Double.valueOf(value)
                : null);
    }
}
