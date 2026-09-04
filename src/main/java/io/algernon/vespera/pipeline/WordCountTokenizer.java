package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.Tokenizer;
import io.algernon.vespera.extraction.TokenizerIdentity;

/**
 * The single wired-in {@link Tokenizer} #49 asks for: no embedding model is chosen yet
 * ({@code docs/architecture.md} marks it "not yet chosen"), so {@code pipeline} supplies a
 * deterministic stand-in — one token per whitespace-separated word — rather than {@code extraction}
 * depending on {@code embedding} to know what a real tokenizer would count. {@link #IDENTITY} keys
 * every chunk-cache row minted under it, so a later embedding-model choice mints new rows instead of
 * overwriting these.
 */
class WordCountTokenizer implements Tokenizer {

    static final TokenizerIdentity IDENTITY = new TokenizerIdentity("word-count-v1");

    @Override
    public int countTokens(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    @Override
    public TokenizerIdentity identity() {
        return IDENTITY;
    }
}
