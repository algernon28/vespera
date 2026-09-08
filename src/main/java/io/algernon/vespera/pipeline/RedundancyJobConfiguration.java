package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 4's own Batch wiring (content redundancy), kept apart from {@link CensusJobConfiguration} and
 * the earlier stages' configuration classes for the same reason those are already separate: each stage
 * contributes its own step beans rather than growing one shared configuration class.
 *
 * <p>Two steps under one run (the #75 hand-off spec's settlement of the map's job-shape fog):
 * {@code redundancySignatureStep} is chunk-oriented, the natural fit for a per-document
 * reader/processor/writer over stage-2 survivors; {@code redundancyResolutionStep} is a tasklet, since
 * candidate generation, exact scoring and verdict writing are corpus-wide work over rows the first step
 * already stored, the same shape stage 3's own corpus-wide pass uses.
 *
 * <p><b>The gate is checked in both steps, before either touches a run.</b> The signature step's reader
 * yields no items at all when the gate is closed, which is what keeps its writer — and the {@link
 * RedundancyRun} it depends on — from ever being constructed; the resolution tasklet checks the same
 * gate directly. Neither behaves differently for "stage 4 is the last step today" versus "stage 4 has a
 * successor" — that distinction is a Spring Batch flow transition to build once stage 5 actually exists
 * (#75's own comment), not before.
 */
@Configuration
public class RedundancyJobConfiguration {

    /**
     * The signature step's chunk size — an implementer's default, not a spec-fixed number. Signature
     * computation is CPU-bound (128 multiply-shift-xor evaluations per shingle) rather than a slow
     * external call, so there is no per-item fault-tolerance concern the way stage 2's Docling calls
     * had, and a larger chunk than stage 2's is fine.
     */
    static final int CHUNK_SIZE = 50;

    @Bean
    Step redundancySignatureStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            OccurrenceReader redundancySignatureReader,
            RedundancySignatureItemWriter redundancySignatureItemWriter) {
        return new StepBuilder("redundancy-signature", jobRepository)
                .<OccurrenceId, OccurrenceId>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(redundancySignatureReader)
                .writer(redundancySignatureItemWriter)
                .listener(new SignatureStepBoundaryLog())
                .build();
    }

    @Bean
    Step redundancyResolutionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RedundancyResolutionTasklet redundancyResolutionTasklet) {
        return new StepBuilder(RedundancyRun.STAGE, jobRepository)
                .tasklet(redundancyResolutionTasklet, transactionManager)
                .build();
    }

    /**
     * The survivors reader, scoped to whichever run {@link RedundancyRun} minted for a run — or, while
     * the gate is closed, an {@link ItemStreamReader} that immediately reports no items at all, so the
     * step completes having read nothing and written nothing (ADR-080, ADR-047).
     */
    @Bean
    @StepScope
    OccurrenceReader redundancySignatureReader(
            Ledger ledger, RedundancyGate redundancyGate, ObjectProvider<RedundancyRun> redundancyRunProvider) {
        if (redundancyGate.floor().isEmpty()) {
            logGateClosed(LoggerFactory.getLogger(RedundancyJobConfiguration.class));
            return OccurrenceReader.yieldingNothing();
        }
        return new OccurrenceReader(ledger.survivors(redundancyRunProvider.getObject().runId()));
    }

    /**
     * The one place the gate's log line is worded, shared by the reader above and {@link
     * RedundancyResolutionTasklet} so an operator sees the same sentence regardless of which step logged
     * it first.
     */
    static void logGateClosed(Logger log) {
        log.info(
                "stage 4 (content redundancy) is gated: the profile key boilerplateDocumentFrequencyFloor is"
                        + " unset. Read similarity's shingle_document_frequency and shingle_corpus_size tables"
                        + " (stage 3's measurement) to choose a value, set it in profile.yaml, and re-invoke."
                        + " No stage-4 run was minted.");
    }

    /**
     * Stage 4a's step start/end lines (ADR-093), kept out of the writer on purpose: a listener is
     * resolved at the step's boundaries whatever the gate says, and the writer reaching {@link
     * RedundancyRun} directly means a step-scoped writer used as a listener would mint a run row on
     * every closed-gate invocation. This listener depends on nothing, so the counts it reports are all
     * it can say — the run id is on the reader's own lines, and the closed case says so above.
     */
    private static final class SignatureStepBoundaryLog implements StepExecutionListener {

        private static final Logger log = LoggerFactory.getLogger(SignatureStepBoundaryLog.class);

        @Override
        public void beforeStep(StepExecution stepExecution) {
            log.info("Stage 4a (redundancy signatures) starting");
        }

        @Override
        public ExitStatus afterStep(StepExecution stepExecution) {
            log.info(
                    "Stage 4a (redundancy signatures) finished: read={}, written={}",
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount());
            return stepExecution.getExitStatus();
        }
    }
}
