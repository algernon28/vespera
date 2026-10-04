package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Optional;

/** Where the lead document's Docling title is asked for (ADR-106). {@code pipeline} implements it (ADR-190). */
@FunctionalInterface
public interface DocumentTitle {

    /** The occurrence's Docling-labelled title, or empty where its cached conversion carries none. */
    Optional<String> of(OccurrenceId occurrence);
}
