package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/**
 * The one place a plain tasklet step is built (ADR-131): a name, the job repository, the transaction
 * manager, and the tasklet to run. Every stage whose step is a single corpus-wide tasklet reaches
 * here rather than repeating the same three {@link StepBuilder} lines in its own configuration.
 *
 * <p>A <b>deep module</b> in the sense of the codebase-design vocabulary: a small interface — three
 * of the four parameters are what every call site already has to hand — behind the two facts a
 * caller would otherwise have to know, that a tasklet step is a {@code StepBuilder} tasklet and how
 * it is made to run outside a transaction. The <b>leverage</b> is that a new plain tasklet step is
 * one call; the <b>locality</b> is that the transaction attribute census needs is written once.
 *
 * <p>The seam is deliberately narrow. Chunk-oriented steps are not built here: extraction, seed
 * extraction and stage 4's signature step each have a reader, a processor or writer and fault
 * tolerance of their own, and pretending their shape is this one would turn a deep module into a
 * shallow one with a parameter for every difference.
 */
final class TaskletSteps {

    private TaskletSteps() {
    }

    /**
     * A tasklet step that participates in a transaction, which is Spring Batch's own default for a
     * tasklet step and the right shape for every stage whose unit of work is the whole step.
     */
    static Step taskletStep(
            String name,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet tasklet) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /**
     * Census's shape: a tasklet step that runs outside any transaction of its own, because a walk's
     * unit of durability is its checkpoint rather than the step (ADR-055). Declaring the step
     * {@code NOT_SUPPORTED} leaves {@code WalkRecorder} to commit at its own cadence, where a
     * step-wide transaction would hold every checkpoint until the whole corpus had been walked.
     */
    static Step taskletStepOutsideAnyTransaction(
            String name,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet tasklet) {
        DefaultTransactionAttribute outsideAnyTransaction =
                new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .transactionAttribute(outsideAnyTransaction)
                .build();
    }
}