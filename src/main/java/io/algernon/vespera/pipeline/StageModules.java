package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * ADR-058's table — which modules a stage's implementation version spans, and the stage name a run
 * is minted under — as one enum, in cascade order (ADR-157 §1).
 *
 * <p>An enum rather than a record with static constants, so a test or a guard can enumerate the whole
 * table through {@link #values()}. {@link StageRuns} and {@link ByteLevelReductionTasklet} mint each
 * run under the stage name and modules of its constant here, through {@link RunMint}; {@code
 * RunIdentityGoldenTest} pins every row as literal text.
 *
 * <p>A stage lists {@code pipeline} only while a class of {@code pipeline} holds a rule that shapes
 * that stage's output (ADR-222 §1). None does (ADR-226), so no stage lists it; generation lists {@code
 * profile} instead, which owns the profile's shape and the rule for every key of it on the deliverable's index.
 */
enum StageModules {
    BYTE_LEVEL_REDUCTION("byte-level-reduction", List.of("corpus")),
    EXTRACTION("extraction", List.of("extraction", "similarity")),
    CONTENT_CENSUS("content-census", List.of("similarity", "extraction")),
    CONTENT_REDUNDANCY("content-redundancy", List.of("similarity", "extraction")),
    SEED_MEASUREMENT("seed-measurement", List.of("embedding", "extraction")),
    EMBEDDING_SCORING("embedding-scoring", List.of("embedding", "extraction")),
    ARRANGEMENT("arrangement", List.of("synthesis", "extraction", "embedding")),
    GENERATION("generation", List.of("synthesis", "extraction", "embedding", "profile"));

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
