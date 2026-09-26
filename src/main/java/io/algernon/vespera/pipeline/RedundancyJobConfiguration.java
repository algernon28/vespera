package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.similarity.RedundancySignatures;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 4's own Batch wiring (content redundancy), the two steps under one run kept together here.
 * Stage 4's resolution step is a plain tasklet and is built by {@link TaskletSteps} (ADR-131);
 * {@code redundancySignatureStep} is chunk-oriented and keeps its own construction.
 *
 * <p>Two steps under one run (the #75 hand-off spec's settlement of the map's job-shape fog):
 * {@code redundancySignatureStep} is chunk-oriented, the natural fit for a per-document
 * reader/processor/writer over stage-2 survivors; {@code redundancyResolutionStep} is a tasklet, since
 * candidate generation, exact scoring and verdict writing are corpus-wide work over rows the first step
 * already stored, the same shape stage 3's own corpus-wide pass uses.
 *
 * <p><b>The gate is checked in both steps, before either touches a run.</b> The signature step's reader
 * yields no items at all when the gate is closed, which is what keeps its writer — and the {@link
 * StageRuns} it depends on — from ever minting stage 4's run; the resolution tasklet checks the same
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
            RedundancySignatureItemWriter redundancySignatureItemWriter,
            Ledger ledger) {
        return new StepBuilder(StepNames.REDUNDANCY_SIGNATURE, jobRepository)
                .<OccurrenceId, OccurrenceId>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(redundancySignatureReader)
                .writer(redundancySignatureItemWriter)
                .listener(new SignatureStepBoundaryLog())
                .listener(new RunCompletion(ledger, StageModules.CONTENT_REDUNDANCY, StepNames.REDUNDANCY_SIGNATURE))
                .build();
    }

    @Bean
    Step redundancyResolutionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RedundancyResolutionTasklet redundancyResolutionTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.CONTENT_REDUNDANCY, jobRepository, transactionManager, redundancyResolutionTasklet);
    }

    /**
     * The survivors reader, scoped to whichever run {@link StageRuns#contentRedundancy} minted — or,
     * while the gate is closed, an {@link ItemStreamReader} that immediately reports no items at all,
     * so the step completes having read nothing and written nothing (ADR-080, ADR-047).
     */
    @Bean
    @StepScope
    OccurrenceReader redundancySignatureReader(
            Ledger ledger,
            RedundancyGate redundancyGate,
            StageRuns stageRuns,
            RedundancySignatures redundancySignatures) {
        // One read of the profile, decided on and explained from the same value (ADR-120): asking the
        // gate twice would let it shut on one reading of the file and word its sentence from another.
        NumericValue floor = redundancyGate.value();
        if (RedundancyGate.floorOf(floor).isEmpty()) {
            logGateClosed(LoggerFactory.getLogger(RedundancyJobConfiguration.class), floor);
            return OccurrenceReader.yieldingNothing();
        }
        RunId redundancyRun = stageRuns.contentRedundancy();
        // This step's own work under this run is already recorded, so it reads nothing (ADR-115,
        // ADR-116) -- content-redundancy, the step after it, is not asked: the two share a run but
        // each answers only for itself.
        if (ledger.stepFinished(redundancyRun, StepNames.REDUNDANCY_SIGNATURE)) {
            LoggerFactory.getLogger(RedundancyJobConfiguration.class)
                    .info("Stage 4a (redundancy signatures) was already recorded under run {}", redundancyRun.value());
            return OccurrenceReader.yieldingNothing();
        }
        // Not finished: an invocation that stopped partway may have left rows behind under this same run id, and both
        // signature tables carry run_id in their primary key, so a second write would collide on the
        // first document it re-signed. Discarded here rather than in the writer, because the writer is
        // constructed whether or not this reader yields anything -- doing it there emptied the table on
        // an invocation that then correctly wrote nothing (ADR-115's discard half, ADR-116).
        redundancySignatures.discardForRun(redundancyRun);
        return new OccurrenceReader(ledger.survivors(redundancyRun));
    }

    /**
     * The gate's log line, said once per invocation by the reader above.
     *
     * <p>Stage 4 is two steps and this gate stops both, but it is one gate with one action, so {@link
     * RedundancyResolutionTasklet} says nothing and this is the whole of what an operator reads about
     * it (#135). <b>It has two reasons and says which</b> (ADR-120): nobody answered the key, or
     * somebody answered it in a way no number can be read from. Before ADR-120 the second could not
     * reach here, because reading the value threw; now that it arrives as a shut gate, a sentence
     * saying "unset" would be telling an operator who wrote something that they wrote nothing.
     *
     * <p>The reader is where it belongs, because stage 4a is the first step the gate stops and a value
     * is wanted before the work rather than after half of it.
     */
    static void logGateClosed(Logger log, NumericValue floor) {
        String whyItIsShut = floor.reading() instanceof NumericValue.Unreadable unreadable
                ? "stage 4 (content redundancy) is gated: the profile key boilerplateDocumentFrequencyFloor"
                        + " reads \"" + unreadable.text() + "\", which is not a number, so nothing could be"
                        + " done with it."
                : "stage 4 (content redundancy) is gated: the profile key boilerplateDocumentFrequencyFloor"
                        + " is unset.";
        log.info(
                "{} Read similarity's shingle_document_frequency and shingle_corpus_size tables"
                        + " (stage 3's measurement) to choose a value, set it in profile.yaml, and re-invoke."
                        + " No stage-4 run was minted.",
                whyItIsShut);
    }

    /**
     * Stage 4a's step start/end lines (ADR-093), kept out of the writer on purpose: a listener is
     * resolved at the step's boundaries whatever the gate says, and the writer reaching {@link
     * StageRuns} directly means a step-scoped writer used as a listener would mint stage 4's run on
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
