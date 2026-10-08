package io.algernon.vespera.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the ledger records about a run and its steps: {@code run}, {@code run_upstream} and {@code
 * finished_step} (ADR-209 section 1).
 */
public class Runs {

    private final JdbcTemplate jdbcTemplate;

    public Runs(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The walk a run was recorded against, for a caller holding a run id and needing the occurrences
     * it was about.
     *
     * <p>Two callers, wanting different things from it. The relevance report resolves each label's path
     * into this walk so the answers can be joined to scores keyed by occurrence (ADR-097). Label
     * ingestion wants only the existence of the row: it records answers against paths and never
     * resolves one, so an empty result there means the file names a run this database does not hold.
     */
    public Optional<WalkId> walkOf(RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT walk_id FROM run WHERE id = ?",
                        (resultSet, rowNumber) -> new WalkId(resultSet.getLong("walk_id")),
                        runId.value())
                .stream()
                .findFirst();
    }

    /**
     * The stage name a run was minted under, as recorded, for a caller following {@link #upstreamRuns}
     * from a run and looking for the one of a stage it can name. {@code ledger} gives the text back and
     * decides nothing from it: what a stage is belongs to {@code pipeline} (ADR-040).
     */
    public Optional<String> stageOf(RunId runId) {
        return jdbcTemplate
                .queryForList("SELECT stage FROM run WHERE id = ?", String.class, runId.value())
                .stream()
                .findFirst();
    }

    /**
     * Mints the identity a stage's run is recorded under, and records what it was derived from
     * (ADR-048) — or carries on under the row already standing for that identity (ADR-115).
     *
     * <p><b>Mint or continue, never mint twice.</b> A run id is a total function of the four things
     * hashed into it, so a re-derived id names a row that agrees with the caller in all four. There is
     * nothing to update and nothing to reconcile: the row is already what this call would have
     * written. {@code stage} is the one column not among them -- it is recorded, not hashed -- so two
     * stages agreeing on all four would collide here rather than being told apart, which is precisely
     * the conflict the next paragraph refuses to swallow. Before ADR-115 the second insert could not happen, because a fresh walk id
     * on every invocation made every derived id fresh too; with a repeated observation discarded, it
     * happens the moment a corpus is re-walked unchanged.
     *
     * <p>Not {@code INSERT OR IGNORE}: that would swallow a genuine conflict as readily as this one,
     * and the conflict it must never swallow is two different runs colliding on one id.
     *
     * <p>Nothing in the census slice calls this: stage 0 writes no verdicts, so it mints no run.
     */
    public RunId startRun(
            String stage,
            String implementationVersion,
            String configConsumed,
            WalkId walkId,
            List<RunId> upstreamRunIds) {
        RunId runId = RunId.of(implementationVersion, configConsumed, walkId, upstreamRunIds);
        if (runExists(runId)) {
            return runId;
        }
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                runId.value(),
                stage,
                implementationVersion,
                configConsumed,
                walkId.value());
        for (RunId upstream : upstreamRunIds) {
            jdbcTemplate.update(
                    "INSERT INTO run_upstream (run_id, upstream_run_id) VALUES (?, ?)",
                    runId.value(),
                    upstream.value());
        }
        return runId;
    }

    /** Whether a row already stands under this identity. */
    private boolean runExists(RunId runId) {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE id = ?", Integer.class, runId.value());
        return rows != null && rows > 0;
    }

    /**
     * Records that {@code step}'s work under {@code runId} is entirely recorded (ADR-116, re-keying
     * {@link Walks#finishWalk}'s shape from a run to the step that actually did the work).
     *
     * <p>Called by the step itself, after its last row and never before: what separates a step whose
     * completion is recorded from one whose is not is the difference between work a later invocation
     * may skip and work it must do again. Several runs are shared by more than one step, which is
     * exactly why this is keyed by step rather than by run alone — a flag on the run would let the
     * first step to finish answer for every step behind it.
     *
     * <p>{@code OR IGNORE} rather than a bare insert: a step-completion listener runs at every step
     * boundary, including one a reader already recognised as finished and skipped, so this method has
     * to tolerate being told the same true thing twice. That is unlike {@link #startRun}, where a
     * second insert under one id could be hiding two different runs colliding — here, one row already
     * says everything a second one would, since {@code (run_id, step)} carries no other column.
     */
    public void finishStep(RunId runId, String step) {
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO finished_step (run_id, step) VALUES (?, ?)", runId.value(), step);
    }

    /**
     * Whether {@code step}'s work under {@code runId} is entirely recorded.
     *
     * <p>False for a step that has never run under this run, failed partway through, or has not been
     * written yet — the same honest answer for all three, which is what makes the discard-and-redo
     * half of ADR-115/ADR-116 safe: a step meeting {@code false} here redoes what it has not recorded.
     * Most steps discard their rows and redo the lot; stage 2 keeps what its committed chunks wrote and
     * reads only the rest (ADR-181).
     */
    public boolean stepFinished(RunId runId, String step) {
        Integer finished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                Integer.class,
                runId.value(),
                step);
        return finished != null && finished > 0;
    }

    /** The runs {@code runId} read, as a set rather than an order (ADR-048). */
    public List<RunId> upstreamRuns(RunId runId) {
        return jdbcTemplate.query(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ? ORDER BY upstream_run_id",
                (resultSet, rowNumber) -> new RunId(resultSet.getString("upstream_run_id")),
                runId.value());
    }
}
