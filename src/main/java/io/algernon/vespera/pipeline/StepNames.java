package io.algernon.vespera.pipeline;

/**
 * The job's fifteen persisted step names, in job order, each byte-identical to its value before this
 * record (ADR-157 §7, amending ADR-131's second reason for a per-stage configuration class).
 *
 * <p>Seven of these values are also a {@link StageModules} stage name — two persisted facts that
 * happen to be equal, {@code finished_step.step} and {@code run.stage}, each declared in its own
 * table and neither defined as the other. After this record, no {@code pipeline} source outside this
 * class and {@link StageModules} holds a string literal equal to a persisted stage or step name.
 */
final class StepNames {

    private StepNames() {}

    static final String CENSUS = "census";
    static final String BYTE_LEVEL_REDUCTION = "byte-level-reduction";
    static final String EXTRACTION = "extraction";
    static final String CONTENT_CENSUS = "content-census";
    static final String REDUNDANCY_SIGNATURE = "redundancy-signature";
    static final String CONTENT_REDUNDANCY = "content-redundancy";
    static final String SEED_EXTRACTION = "seed-extraction";
    static final String SEED_CORPUS_COMPARISON = "seed-corpus-comparison";
    static final String EMBEDDING_SCORING = "embedding-scoring";
    static final String RELEVANCE_SCORING = "relevance-scoring";
    static final String RELEVANCE_FLOOR = "relevance-floor";
    static final String CLUSTERING = "clustering";
    static final String RELEVANCE_REPORT = "relevance-report";
    static final String ARRANGEMENT = "arrangement";
    static final String GENERATION = "generation";
}
