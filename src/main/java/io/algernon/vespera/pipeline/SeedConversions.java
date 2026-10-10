package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import java.nio.file.Path;

/**
 * Converting one seed document (ADR-092, ADR-100).
 *
 * <p>A seed is not a walked occurrence: no walk recorded it, so no stage-1 run ever wrote down what
 * it is, and there is no {@code detected_format} row to read. The same byte-level detection runs
 * here instead, so a seed is converted as what its bytes say exactly as a corpus document is — which
 * is the whole of ADR-100 applied to the one input that arrives outside the cascade. What a file the
 * check stopped is sent as is {@link DoclingExtractor#convertSeed}'s to say (ADR-226).
 *
 * <p>Seed extraction is its one caller (ADR-206 section 4), because a seed is the one input with no earlier
 * stage to have recorded what it is. The embedding-scoring tasklet called it too, for every survivor and
 * every usable seed, which read each file again even where its conversion was on record; it now reads
 * the cached conversion under the recorded key and converts nothing. The relevance report called it
 * once; ADR-152 moved its preview onto the extraction cache alone.
 */
final class SeedConversions {

    /**
     * Converts {@code file} as what its bytes say, under {@code extractorIdentity} and keyed on
     * {@code contentHash}.
     */
    static DoclingResponse convert(
            DoclingExtractor extractor, Path file, String contentHash, ExtractorIdentity extractorIdentity) {
        BrokenCheck.Result detected = BrokenCheck.check(file);
        return extractor.convertSeed(
                file, contentHash, extractorIdentity, detected.format(), detected.subtype().orElse(null));
    }

    private SeedConversions() {
    }
}
