package io.algernon.vespera.pipeline;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * The steps of this invocation that ended at a shut gate (ADR-265 section 3): having done nothing for want of
 * a value, their own or one an earlier step's gate required. The progress page reads it to tell such a step
 * from one that finished. It decides nothing: the gates are where they are, and each calls {@link #record}
 * after it has decided.
 *
 * <p>Backed by the job execution's own {@link ExecutionContext}, as {@link InvocationRuns} is.
 */
final class ShutGates {

    /** Namespaced so these keys cannot collide with anything else a step writes into the context. */
    private static final String KEY_PREFIX = "vespera.shut-gate.";

    private final ExecutionContext executionContext;

    ShutGates(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    /** The record of the job that the step running under {@code chunkContext} belongs to. */
    static ShutGates in(ChunkContext chunkContext) {
        return new ShutGates(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());
    }

    /** Records that {@code step} ended at a shut gate. */
    void record(String step) {
        executionContext.putString(KEY_PREFIX + step, step);
    }

    /** Whether {@code step} recorded that it ended at a shut gate. */
    boolean shutAt(String step) {
        return executionContext.containsKey(KEY_PREFIX + step);
    }
}
