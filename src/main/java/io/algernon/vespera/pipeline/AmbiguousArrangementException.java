package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import java.util.List;

/**
 * The approval in the profile names more than one arrangement of this corpus (ADR-107, ADR-099).
 *
 * <p>The run stops rather than choosing. Every other outcome here is worse: taking the first would
 * generate over an arrangement nobody approved, and doing it without saying so; asking the operator
 * to disambiguate at this point would be a gate inside a gate.
 *
 * <p>Named for the fault rather than for the query that noticed it (ADR-076), and the message names
 * both candidates, because the operator's way out is to copy more of whichever id they meant.
 */
class AmbiguousArrangementException extends RuntimeException {

    AmbiguousArrangementException(String approval, List<RunId> candidates) {
        super("the approved arrangement \"%s\" names %d arrangements of this corpus: %s. Copy more of the one you read."
                .formatted(
                        approval,
                        candidates.size(),
                        candidates.stream().map(RunId::value).reduce((a, b) -> a + ", " + b).orElse("")));
    }
}
