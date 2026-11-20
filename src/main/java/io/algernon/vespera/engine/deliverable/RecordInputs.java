package io.algernon.vespera.engine.deliverable;

import java.io.IOException;

/**
 * What composes the retrieval records beyond the arrangement (ADR-434 §4, §7), handed by {@code pipeline} as {@link
 * ArrangedSurvivors} is, so that {@code deliverable} names no other capability module: the embedder identity every
 * vector is under, the vector of a writing, and a member's passages with their vectors.
 */
public interface RecordInputs {

    /** The scoring run's embedder identity, composed as its value (ADR-228); every record carries it. */
    String embedderIdentity() throws IOException;

    /**
     * The vector of {@code writing} under {@link #embedderIdentity()}: read where it is kept, else embedded and kept
     * (ADR-434 §7). An {@link IllegalStateException} where Ollama reports another artefact than the scoring run names.
     */
    float[] vectorOf(String writing) throws IOException;

    /** Hands {@code passages} each of {@code member}'s passages in ordinal order, one member at a time. */
    void passagesOf(ListedSurvivor member, PassageConsumer passages) throws IOException;

    /** One passage: its ordinal among its member's chunks, its text and its stored vector. */
    @FunctionalInterface
    interface PassageConsumer {

        void accept(int ordinal, String text, float[] vector) throws IOException;
    }
}
