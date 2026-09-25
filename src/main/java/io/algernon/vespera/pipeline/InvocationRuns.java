package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import java.util.Optional;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * The record of the runs this invocation minted or continued, one id per stage (ADR-154 §1).
 *
 * <p>Every stage that mints or continues a run records it here at the moment {@code Ledger.startRun}
 * returns -- including stage 1's inline mint in {@link ByteLevelReductionTasklet}. A later stage that
 * needs an upstream run's id, or a place that reports what this invocation arrived at ({@link
 * NextAction}, {@link ArrangementGate}), reads it from here rather than looking it up over the walk
 * (ADR-099's rule) or recomputing it. This is the "job-execution-context handoff" ADR-099 refused and
 * ADR-154 §1 now relies on: since ADR-115, every invocation starts at census and passes through every
 * step in order, so the run of a stage before it that this invocation minted or continued is always in
 * hand by the time a later stage asks for it (ADR-154, Context §3).
 *
 * <p>Backed by the job execution's own {@link ExecutionContext} rather than a bean of its own: one
 * {@link org.springframework.batch.core.job.JobExecution} is one invocation under a resourceless job
 * repository (ADR-036), with no restarts, so its execution context already lives and dies with exactly
 * the scope this record needs, and is already shared by reference across every step of the job.
 * Instantiated inline wherever it is needed -- the same shape {@link UpstreamRuns} already uses for
 * {@code Ledger} -- rather than registered as a Spring bean, so nothing has to be added to a test
 * slice's wiring for it to be reachable.
 *
 * <p>One instance per stage's run bean or tasklet, sharing the one {@link ExecutionContext} the
 * constructor is handed. No id is ever replaced within an invocation: each stage mints or continues its
 * run at most once per job execution, so a key is written at most once.
 */
class InvocationRuns {

    /** Namespaced so this record's keys cannot collide with anything else a step writes into the context. */
    private static final String KEY_PREFIX = "vespera.invocation-run.";

    private final ExecutionContext executionContext;

    InvocationRuns(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    /** Records {@code runId} as the run this invocation minted or continued for {@code stage}. */
    void record(String stage, RunId runId) {
        executionContext.putString(KEY_PREFIX + stage, runId.value());
    }

    /** The run this invocation minted or continued for {@code stage}, if it has run yet this invocation. */
    Optional<RunId> runOf(String stage) {
        return Optional.ofNullable(executionContext.getString(KEY_PREFIX + stage, null)).map(RunId::new);
    }
}
