package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Checks {@code docling-serve}'s health once, immediately before stage 2's step processes its first
 * occurrence (ADR-071) — not at job start, since census and stage 1 never touch the sidecar, and not
 * before every call, since a dead sidecar already fails the call itself and the skip/breaker machinery
 * already handles that.
 *
 * <p>Also carries stage 2's own step start/end logging (ADR-093): the natural place, since it already
 * runs at both boundaries Spring Batch offers a step-scoped listener. The end line says how the step
 * ended, because {@link #afterStep} runs after a failed step too: "finished" only when it completed,
 * and otherwise that it failed, with the same counts and what failed it (#311).
 */
@Component
class ExtractionHealthCheckListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExtractionHealthCheckListener.class);

    private final DoclingClient doclingClient;

    ExtractionHealthCheckListener(DoclingClient doclingClient) {
        this.doclingClient = doclingClient;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Stage 2 (extraction) starting: checking docling-serve health");
        try {
            doclingClient.checkHealth();
        } catch (RuntimeException healthCheckFailed) {
            log.error("Stage 2 (extraction) sidecar health check failed: {}", healthCheckFailed.toString());
            throw healthCheckFailed;
        }
        log.info("Stage 2 (extraction) sidecar is healthy");
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            // Spring Batch calls this from a finally, so a step that failed arrives here too (#311), and
            // "finished" beside its error would tell the operator it completed. The same exit-code test
            // RunCompletion makes, so this line says failed only when the step did not complete, and a
            // step that did not complete records no completion; the next invocation redoes this run's
            // work under the same id (ADR-115, ADR-116).
            log.error(
                    "Stage 2 (extraction) failed and is not recorded as finished (read={}, written={},"
                            + " skipped={}, filtered={}): {}. Fix what that names -- if docling-serve stopped"
                            + " answering, bring it back -- and run the same command again.",
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount(),
                    stepExecution.getFilterCount(),
                    StepFailure.named(stepExecution));
            return stepExecution.getExitStatus();
        }
        log.info(
                "Stage 2 (extraction) finished: read={}, written={}, skipped={}, filtered={}",
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getFilterCount());
        return stepExecution.getExitStatus();
    }
}
