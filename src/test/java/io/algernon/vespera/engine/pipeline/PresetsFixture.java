package io.algernon.vespera.engine.pipeline;

import org.springframework.mock.env.MockEnvironment;

/**
 * The presets as the application ships them (ADR-330 §9), for a test that builds {@link StageRuns} or {@link Presets}
 * itself and has no context to read {@code application.yaml} through. Written out, as {@code TheShippedPresetsTest}
 * holds them of the file: a test handed these is handed the numbers runs were minted under before they were presets, so
 * that no run it mints names one.
 */
final class PresetsFixture {

    private PresetsFixture() {}

    /** An environment naming the sixteen, for a test to name another over one of them. */
    static MockEnvironment shippedEnvironment() {
        return new MockEnvironment()
                .withProperty("vespera.clustering.resolution", "1.0")
                .withProperty("vespera.clustering.neighbours", "15")
                .withProperty("vespera.docling.ocr-preset", "rapidocr")
                .withProperty("vespera.docling.timeouts-in-a-row", "3")
                .withProperty("vespera.redundancy.shingle-words", "5")
                .withProperty("vespera.redundancy.minhash.permutations", "128")
                .withProperty("vespera.redundancy.minhash.bands", "16")
                .withProperty("vespera.redundancy.minhash.rows-per-band", "8")
                .withProperty("vespera.redundancy.minhash.seed", "6840335614489015382")
                .withProperty("vespera.redundancy.near-duplicate-jaccard", "0.80")
                .withProperty("vespera.redundancy.containment-index", "0.95")
                .withProperty("vespera.redundancy.rare-shingle-sample", "32")
                .withProperty("vespera.redundancy.rare-shingle-hits", "24")
                .withProperty("vespera.relevance.top-similarities", "3")
                .withProperty("vespera.relevance.sample.bands", "5")
                .withProperty("vespera.relevance.sample.per-band", "12");
    }

    /** The presets resolved from what ships. */
    static Presets shipped() {
        return new Presets(shippedEnvironment());
    }
}
