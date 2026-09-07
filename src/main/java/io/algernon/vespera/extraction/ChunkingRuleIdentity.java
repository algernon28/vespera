package io.algernon.vespera.extraction;

/**
 * The identity of the rule a chunk-cache row's boundaries were cut under (ADR-044, ADR-091). ADR-044
 * required the cache key carry "tokenizer identity"; ADR-091 kept the slot and changed its occupant,
 * because there is no tokenizer here — what the key has to carry is whatever determines a boundary,
 * and that is the budgeting rule.
 *
 * <p>Never composed by hand: {@link ChunkingRule#identity()} derives it from the budget it actually
 * enforces, so an identity naming one budget over boundaries cut to another cannot be minted. It
 * refuses a blank value, the discipline {@link ExtractorIdentity} and
 * {@link io.algernon.vespera.ledger.RunId} already apply to their own.
 */
public record ChunkingRuleIdentity(String value) {

    public ChunkingRuleIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a chunking rule identity is never blank");
        }
    }
}
