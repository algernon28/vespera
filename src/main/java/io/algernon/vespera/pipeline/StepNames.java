package io.algernon.vespera.pipeline;

/**
 * The job's sixteen persisted step names, in job order, each byte-identical to its value before this
 * record (ADR-157 §7, amending ADR-131's second reason for a per-stage configuration class).
 *
 * <p>Seven of these values are also a {@link StageModules} stage name — two persisted facts that
 * happen to be equal, {@code finished_step.step} and {@code run.stage}, each declared in its own
 * table and neither defined as the other. After this record, no {@code pipeline} source outside this
 * class and {@link StageModules} holds a string literal naming a persisted stage or step. A module
 * name is not one: {@code "extraction"} in {@code package-info.java}'s {@code allowedDependencies}
 * names the module ({@code RunsAndNamesHaveOnePlaceTest}).
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
    static final String TRANSLATION = "translation";
    static final String RELEVANCE_SCORING = "relevance-scoring";
    static final String RELEVANCE_FLOOR = "relevance-floor";
    static final String CLUSTERING = "clustering";
    static final String RELEVANCE_REPORT = "relevance-report";
    static final String ARRANGEMENT = "arrangement";
    static final String GENERATION = "generation";

    private static final java.util.Map<String, String> DESCRIPTIONS = java.util.Map.ofEntries(
            java.util.Map.entry(CENSUS, "walks the archive and records every file it finds, with its size and times"),
            java.util.Map.entry(BYTE_LEVEL_REDUCTION, "removes files that are broken, exact copies, superseded or out of scope, judging their bytes alone"),
            java.util.Map.entry(EXTRACTION, "converts each file to text, removing those that cannot be converted or whose text is too poor to use"),
            java.util.Map.entry(CONTENT_CENSUS, "measures the converted text and writes the pages the confidence and boilerplate floors are read off"),
            java.util.Map.entry(REDUNDANCY_SIGNATURE, "computes a signature of each document's text, leaving out boilerplate, so that near-copies can be found"),
            java.util.Map.entry(CONTENT_REDUNDANCY, "finds documents that repeat or contain another and keeps the fuller of each set"),
            java.util.Map.entry(SEED_EXTRACTION, "converts the seed set, the documents known to be relevant, to text"),
            java.util.Map.entry(SEED_CORPUS_COMPARISON, "compares the seed set with the archive's documents and writes how far they differ"),
            java.util.Map.entry(EMBEDDING_SCORING, "turns each document's text into vectors with the embedding model"),
            java.util.Map.entry(TRANSLATION, "finds documents that are translations of one another and keeps one, in the language preferred first"),
            java.util.Map.entry(RELEVANCE_SCORING, "scores each document by how close it lies to the seed set"),
            java.util.Map.entry(RELEVANCE_FLOOR, "removes the documents scoring below the relevance threshold"),
            java.util.Map.entry(CLUSTERING, "gathers the documents under each seed into groups of documents alike"),
            java.util.Map.entry(RELEVANCE_REPORT, "writes the page of documents to judge that the relevance threshold is chosen from"),
            java.util.Map.entry(ARRANGEMENT, "names and orders the groups and writes the arrangement page to approve"),
            java.util.Map.entry(GENERATION, "has the local model write over each group and writes the deliverable, with a copy of each document it lists"));

    /** What the step does, in the operator's words (ADR-275 section 4); refuses a name this class does not hold. */
    static String description(String step) {
        String description = DESCRIPTIONS.get(step);
        if (description == null) {
            throw new IllegalArgumentException("not a step of the job: " + step);
        }
        return description;
    }
}
