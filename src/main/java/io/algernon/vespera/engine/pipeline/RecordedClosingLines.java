package io.algernon.vespera.engine.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Holds the closing lines a tasklet writes from inside its work, and logs them once the step has ended (ADR-364 §4,
 * amending ADR-289's second paragraph).
 *
 * <p>Spring Batch's {@code AbstractStep} logs its own {@code executed in} line, and for a thrown exception its error and
 * stack trace, after the tasklet returns and before any {@code afterStep}. A line logged inside the tasklet would
 * therefore not be the last. A tasklet {@link #record records} the line instead, and this listener, registered by
 * {@link TaskletSteps} after {@link StepFailureClosingLine} so that Spring Batch, running {@code afterStep} in reverse
 * order of registration, runs it first, logs each on the operator logger at ERROR.
 *
 * <p>The lines are held in memory, in a map keyed by the step execution's id, and never in its
 * {@code ExecutionContext}, which {@code TaskletStep} puts back as it was where a commit fails after the tasklet
 * returned. Neither listener implements {@code Ordered} or carries {@code @Order}: Spring Batch sorts listeners that do
 * before it reverses them, which would undo the order.
 */
public final class RecordedClosingLines implements StepExecutionListener {

    private final ConcurrentHashMap<Long, List<String>> recorded = new ConcurrentHashMap<>();

    /** Records {@code line}, its words whole, to be logged after the step of {@code stepExecution}, in order. */
    public void record(StepExecution stepExecution, String line) {
        recorded.computeIfAbsent(stepExecution.getId(), id -> new ArrayList<>()).add(line);
    }

    /** The lines recorded for {@code stepExecution} and not yet logged, in the order recorded. */
    public List<String> recordedFor(StepExecution stepExecution) {
        List<String> lines = recorded.get(stepExecution.getId());
        return lines == null ? List.of() : List.copyOf(lines);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        List<String> lines = recorded.remove(stepExecution.getId());
        if (lines != null) {
            for (String line : lines) {
                OperatorLines.error(line);
            }
        }
        return stepExecution.getExitStatus();
    }
}
