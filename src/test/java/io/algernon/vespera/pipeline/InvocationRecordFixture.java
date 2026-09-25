package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import java.nio.file.Path;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What an invocation holds once stage 1 has run, for a test that drives the stages by hand rather
 * than through the job (ADR-154 §1).
 *
 * <p>A stage names as its upstream the run the same invocation minted or continued, read from the job
 * execution's context. A test that constructs a run bean directly has no job execution, so it hands
 * the bean a context of its own. It is filled here the way stage 1 fills it: with the byte-level
 * reduction run stage 1 just minted over {@code root}'s walk and, where stage 2 has run too, the
 * extraction run it minted.
 *
 * <p>Each run is read back from the row its stage wrote rather than recomputed, so the claim a test
 * makes about what a later stage names is still a claim about the run that actually exists.
 */
final class InvocationRecordFixture {

    private InvocationRecordFixture() {
    }

    /** A fresh invocation's record, holding the byte-level reduction run last written over {@code root}. */
    static ExecutionContext afterStageOne(JdbcTemplate jdbcTemplate, Path root) {
        return after(jdbcTemplate, root, ByteLevelReductionTasklet.STAGE);
    }

    /** A fresh invocation's record, holding stage 1's run and then stage 2's, each the last written over {@code root}. */
    static ExecutionContext afterExtraction(JdbcTemplate jdbcTemplate, Path root) {
        return after(jdbcTemplate, root, ByteLevelReductionTasklet.STAGE, ExtractionRun.STAGE);
    }

    private static ExecutionContext after(JdbcTemplate jdbcTemplate, Path root, String... stages) {
        ExecutionContext invocation = new ExecutionContext();
        InvocationRuns runs = new InvocationRuns(invocation);
        for (String stage : stages) {
            runs.record(stage, new RunId(jdbcTemplate.queryForObject(
                    "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                            + " WHERE run.stage = ? AND w.root = ? ORDER BY run.rowid DESC LIMIT 1",
                    String.class,
                    stage,
                    Walk.canonicalRoot(root).toString())));
        }
        return invocation;
    }
}
