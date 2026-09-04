package io.algernon.vespera.extraction;

/**
 * The identity of the tokenizer a chunk-cache row was produced under (ADR-029, ADR-044): the chunk
 * cache's key must carry it, so a future embedding-model bake-off can re-chunk each candidate under
 * its own tokenizer without invalidating another candidate's rows.
 *
 * <p>{@code extraction} never depends on {@code embedding} to derive this value — {@code pipeline},
 * the composition root, supplies it (today, a single wired-in value; ADR-034's bake-off is what
 * eventually makes it vary).
 */
public record TokenizerIdentity(String value) {

    public TokenizerIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a tokenizer identity is never blank");
        }
    }
}
