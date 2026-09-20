package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RecordedRun;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Answers "what is the run of stage {@code S} over this walk?" by looking it up in the ledger
 * (ADR-099).
 *
 * <p>This is the one seam through which a stage learns the id of a run that already exists. It
 * replaces re-deriving that id from the stage's own known inputs, which grew by one call site per
 * stage added, required each earlier stage's {@code configConsumed} to be widened so a later stage
 * could copy its JSON shape, and drifted silently at the point of the mistake — a shape that no longer
 * matched what the earlier stage had hashed named a row that did not exist.
 *
 * <p>It lives in {@code pipeline} because ADR-040 forbids {@code ledger} from knowing what a stage is,
 * and {@code pipeline} is the composition root and the only module that may name one.
 *
 * <p><b>Exactly one row, or the run stops.</b> Zero and two-or-more are both faults:
 *
 * <ul>
 *   <li><b>Zero</b> is the condition the {@code run_upstream} foreign key catches today, arriving
 *       earlier and with a better message: the stage that should have run first has no recorded run
 *       over this walk.
 *   <li><b>Two or more</b> is the case lookup separates from recomputation. The glossary defines a
 *       run as minted when the configuration changes and says that where one walk holds two runs of
 *       one stage there is no upstream run until a person says which is meant. Nothing in the data
 *       says which one the operator meant — not the configuration, and not recency either — so this
 *       refuses rather than guessing.
 * </ul>
 *
 * <p>That refusal is a fault rather than a gate because there is no value an operator can supply to
 * resolve it: no profile key, no command option. A gate naming nothing would exit 0 on a job that
 * cannot continue (ADR-099, ADR-080).
 *
 * <p>One instance per stage's run bean, built from the {@code Ledger} that bean already holds. It
 * carries no state of its own beyond the ledger, so the many instances are one behaviour.
 */
class UpstreamRuns {

    private final Ledger ledger;

    UpstreamRuns(Ledger ledger) {
        this.ledger = ledger;
    }

    /**
     * The id of the one run of {@code stage} recorded against {@code walkId}.
     *
     * @throws NoUpstreamRunException if the stage has no run over this walk
     * @throws AmbiguousUpstreamRunException if the walk holds two or more runs of the stage
     */
    RunId runOf(String stage, WalkId walkId) {
        List<RecordedRun> recorded = ledger.runsOf(stage, walkId);
        if (recorded.isEmpty()) {
            throw new NoUpstreamRunException(stage, walkId);
        }
        if (recorded.size() > 1) {
            throw new AmbiguousUpstreamRunException(stage, walkId, recorded);
        }
        return recorded.getFirst().id();
    }

    /** How a refusal names a run: its id and the configuration it consumed, which tells the two apart. */
    static String render(RecordedRun run) {
        return "id=" + run.id().value() + " config_consumed=" + run.configConsumed();
    }

    /** Renders every candidate, in the order the ledger recorded them. */
    static String render(List<RecordedRun> runs) {
        return runs.stream().map(UpstreamRuns::render).collect(Collectors.joining("; "));
    }
}
