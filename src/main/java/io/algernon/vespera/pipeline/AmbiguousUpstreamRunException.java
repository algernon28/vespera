package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RecordedRun;
import io.algernon.vespera.ledger.WalkId;
import java.util.List;

/**
 * A stage asked which run of a stage before it to read, and this walk holds more than one (ADR-099).
 *
 * <p>This is not a corrupt database. The glossary defines a run as minted when the configuration
 * changes, and verdicts accumulate rather than replacing one another, so a stage run twice over one
 * walk at two configurations leaves two rows that are both legitimate, both correct and both
 * permanent. Recomputing an id would know which one it meant by construction; looking one up does not,
 * and nothing in the data says which the operator meant — not the configuration, and not recency
 * either, since an operator may have run the second as an experiment and want the first.
 *
 * <p>Refusing is therefore a refusal to guess on the operator's behalf in a situation their own
 * glossary calls normal. It stops the run rather than choosing, and it names every candidate with the
 * configuration it consumed, because the configurations are the only thing that tells the reader why
 * there are two.
 *
 * <p>It states plainly that no option chooses between them rather than implying one exists: no profile
 * key and no command option supplies the answer, so this is a fault rather than a gate (ADR-080). The
 * means of choosing arrives with walk reuse, which is a separate decision.
 */
class AmbiguousUpstreamRunException extends RuntimeException {

    AmbiguousUpstreamRunException(String stage, WalkId walkId, List<RecordedRun> candidates) {
        super("walk %d holds %d runs of stage \"%s\", so it is not recorded which one is meant: %s."
                .formatted(walkId.value(), candidates.size(), stage, UpstreamRuns.render(candidates))
                + " No option selects between them, so this run stops rather than guessing."
                + " Run a fresh walk, or choose the run to continue from once that is possible.");
    }
}
