package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * ADR-058's table — which modules a stage's implementation version spans, and the stage name a run
 * is minted under — as one enum, in cascade order (ADR-157 §1).
 *
 * <p>An enum rather than a record with static constants, so a test or a guard can enumerate the whole
 * table through {@link #values()}. Every {@code STAGE} and {@code *_MODULE} constant the seven run
 * classes and {@link ByteLevelReductionTasklet} used to carry lived here instead; {@code
 * RunIdentityGoldenTest} pins every row as literal text, and is not edited by this record.
 */
enum StageModules {
    BYTE_LEVEL_REDUCTION("byte-level-reduction", List.of("corpus")),
    EXTRACTION("extraction", List.of("extraction", "similarity")),
    CONTENT_CENSUS("content-census", List.of("similarity", "extraction", "pipeline")),
    CONTENT_REDUNDANCY("content-redundancy", List.of("similarity", "extraction", "pipeline")),
    SEED_MEASUREMENT("seed-measurement", List.of("embedding", "extraction", "pipeline")),
    EMBEDDING_SCORING("embedding-scoring", List.of("embedding", "extraction", "pipeline")),
    ARRANGEMENT("arrangement", List.of("synthesis", "extraction", "embedding", "pipeline")),
    GENERATION("generation", List.of("synthesis", "extraction", "embedding", "pipeline"));

    private final String stage;
    private final List<String> modules;

    StageModules(String stage, List<String> modules) {
        this.stage = stage;
        this.modules = List.copyOf(modules);
    }

    /** The persisted {@code run.stage} value this constant mints under. */
    String stage() {
        return stage;
    }

    /**
     * The modules this stage's implementation version spans, in the order {@link
     * io.algernon.vespera.ledger.ImplementationVersions#of} is given them.
     */
    List<String> modules() {
        return modules;
    }
}
