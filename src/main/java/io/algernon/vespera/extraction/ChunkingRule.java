package io.algernon.vespera.extraction;

/**
 * How wide a chunk is allowed to get, in a unit measurable without a model (ADR-091): whitespace-
 * separated words. Nothing here counts tokens — the only component that tokenizes is the one that
 * embeds, and it enforces its own limit by rejecting what will not fit.
 *
 * <p>The budget is not protecting against overflow, so it does not need to be token-exact. It needs
 * to be <em>deterministic</em>, which is the property {@code chunk_cache} exists to persist, and it
 * needs to say what it was — hence {@link #identity()}, derived from the budget rather than supplied
 * beside it, so the name and the boundaries cannot drift apart.
 *
 * <p>{@link #DEFAULT} is a code default, ADR-082's precedent, and openly unmeasured: with a 32k
 * context window against 512 words the model's limit constrains nothing, and the two reasons that do
 * constrain it — one vector per chunk discriminates less the more it spans, and ADR-020's top-3 mean
 * needs more than three chunks to mean anything — need a real corpus. A budget varying by document
 * kind is a second rule, not a migration: it mints its own identity beside this one.
 */
public record ChunkingRule(int maxWords) {

    /** The packing algorithm's own version: a change to how words are placed moves boundaries too. */
    private static final String ALGORITHM_VERSION = "v1";

    /** The unit the budget is counted in, named in the identity so a stored row says what it is. */
    private static final String UNIT = "words";

    /** The budget every chunk is cut to unless a caller names another — arbitrary, and openly so. */
    public static final ChunkingRule DEFAULT = new ChunkingRule(512);

    public ChunkingRule {
        if (maxWords < 1) {
            throw new IllegalArgumentException("a chunking rule budgets at least one word, not " + maxWords);
        }
    }

    /** The identity a chunk-cache row is keyed under when its boundaries were cut by this rule. */
    public ChunkingRuleIdentity identity() {
        return new ChunkingRuleIdentity(UNIT + "-" + maxWords + "-" + ALGORITHM_VERSION);
    }

    /** How wide {@code text} is in this rule's own unit. */
    public int size(String text) {
        return text.isBlank() ? 0 : text.trim().split("\s+").length;
    }
}
