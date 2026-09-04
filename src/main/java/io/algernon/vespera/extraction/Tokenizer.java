package io.algernon.vespera.extraction;

/**
 * Counts tokens the way one specific tokenizer would (ADR-029), so the chunker can respect a
 * token budget aligned to whichever embedding model {@code pipeline} eventually wires in, without
 * {@code extraction} depending on {@code embedding} to know what that model is.
 *
 * <p>{@code pipeline} supplies the implementation; {@code extraction} only calls through this seam.
 */
public interface Tokenizer {

    /** How many tokens this tokenizer would produce from {@code text}. */
    int countTokens(String text);

    /** The identity a chunk-cache row is keyed under when produced with this tokenizer. */
    TokenizerIdentity identity();
}
