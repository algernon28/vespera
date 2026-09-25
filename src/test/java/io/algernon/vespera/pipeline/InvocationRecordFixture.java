package io.algernon.vespera.pipeline;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * One invocation, for a test that drives the stages by hand rather than through the job (ADR-154 §1).
 *
 * <p>A stage names as its upstream the run the same invocation minted or continued, and the invocation
 * holds that record in its job execution's context. A test that calls a tasklet or builds a run bean
 * directly has no job running, so this gives it a step of one: a real {@link ChunkContext} over a real
 * {@link JobExecution}, built from {@code spring-batch-core}'s own types. {@code spring-batch-test}'s
 * factory would do the same, but it is not in the pom, and adding it would want a decision (ADR-046).
 *
 * <p>Stage 1 records its run into the context it is handed, as it does in the job, and every later run
 * bean is handed {@link #recordOf} that same step. So what a test's later stage names is what that
 * invocation's stage 1 actually recorded. Nothing here reads the run back from the ledger, and nothing
 * picks the run written last, which is the recency rule ADR-154 rejects.
 */
final class InvocationRecordFixture {

    /** The job every invocation runs under (the name is not read by anything these tests reach). */
    private static final String JOB = "vespera";

    private InvocationRecordFixture() {
    }

    /** A step of a fresh invocation, carrying an empty record of the runs that invocation arrived at. */
    static ChunkContext aStepOfAFreshInvocation() {
        JobExecution invocation = new JobExecution(1L, new JobInstance(1L, JOB), new JobParameters());
        return new ChunkContext(new StepContext(new StepExecution(1L, "a step", invocation)));
    }

    /** The record of runs held by the invocation {@code step} belongs to. */
    static ExecutionContext recordOf(ChunkContext step) {
        return step.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
    }
}
