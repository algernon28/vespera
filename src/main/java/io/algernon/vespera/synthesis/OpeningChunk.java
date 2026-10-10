package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Optional;

/**
 * Where the gathering asks for the chunk one document opens with, on the precedent of {@link DocumentTitle}
 * (ADR-190 section 3, ADR-226): {@code synthesis} owns the callback and {@code pipeline} implements it, since
 * the leading chunk is {@code extraction}'s to read and {@code synthesis} may not name it.
 */
@FunctionalInterface
public interface OpeningChunk {

    /** The text {@code occurrence} opens with, or empty where nothing was ever chunked from it. */
    Optional<OpeningText> of(OccurrenceId occurrence);
}
