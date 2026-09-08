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
 * runs at both boundaries Spring Batch offers a step-scoped listener.
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
        log.info(
                "Stage 2 (extraction) finished: read={}, written={}, skipped={}, filtered={}",
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getFilterCount());
        return stepExecution.getExitStatus();
    }
}
