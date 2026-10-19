package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The generator of each page stage 1's first pass kept as code for a documentation generator's mark, handed in
 * memory to {@link CodeSets}, which finds a generated tree from it and never from a verdict's reason
 * (ADR-284 section 5). Only pages kept for a mark are held, and nothing else of the first pass.
 */
public final class MarkedPages {

    private final Map<OccurrenceId, String> generators = new HashMap<>();

    /** Notes that {@code occurrence} was kept as code for the mark of {@code generator}. */
    void add(OccurrenceId occurrence, String generator) {
        generators.put(occurrence, generator);
    }

    /** The generator whose mark {@code occurrence} carries, where the first pass kept it for one. */
    Optional<String> generatorOf(OccurrenceId occurrence) {
        return Optional.ofNullable(generators.get(occurrence));
    }
}
