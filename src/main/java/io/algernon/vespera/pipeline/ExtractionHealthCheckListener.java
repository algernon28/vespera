package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingClient;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Checks {@code docling-serve}'s health once, immediately before stage 2's step processes its first
 * occurrence (ADR-071) — not at job start, since census and stage 1 never touch the sidecar, and not
 * before every call, since a dead sidecar already fails the call itself and the skip/breaker machinery
 * already handles that.
 */
@Component
class ExtractionHealthCheckListener implements StepExecutionListener {

    private final DoclingClient doclingClient;

    ExtractionHealthCheckListener(DoclingClient doclingClient) {
        this.doclingClient = doclingClient;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        doclingClient.checkHealth();
    }
}
