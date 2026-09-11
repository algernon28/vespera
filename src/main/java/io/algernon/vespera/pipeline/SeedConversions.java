package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.corpus.DetectedFormat;
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
 * is the whole of ADR-100 applied to the one input that arrives outside the cascade.
 *
 * <p>Held in one place because the three passes that extract seeds — the seed step, the scoring
 * tasklet and the relevance report — would otherwise each carry the same five lines, and changing
 * how a seed's format is found would mean editing three files to keep one answer.
 */
final class SeedConversions {

    /**
     * Converts {@code file} as what its bytes say, under {@code extractorIdentity} and keyed on
     * {@code contentHash}.
     */
    static DoclingResponse convert(
            DoclingExtractor extractor, Path file, String contentHash, ExtractorIdentity extractorIdentity) {
        BrokenCheck.Result detected = BrokenCheck.check(file);
        return extractor.convert(
                file, contentHash, extractorIdentity, formatFor(detected), detected.subtype());
    }

    /**
     * What to convert a seed as, given what the check found.
     *
     * <p>{@code FLOOR_STOPPED} is the one answer that cannot travel onward. It means the cross-format
     * floor stopped the file before a byte was read — empty, or it would not open — and stage 2 is
     * right to refuse it, because there the floor has already blocked that occurrence and being
     * handed one is a wiring fault. **A seed folder has no floor in front of it**, so an empty file
     * sitting in it is an ordinary thing for an operator to have, and it must reach the same
     * unusable-seed path it reached before this rule existed rather than stopping the step: the seed
     * job is not {@code faultTolerant}, so a refusal here would abort stage 5 over one empty file.
     *
     * <p>It travels as {@code UNRECOGNISED} for that reason — the bytes said nothing, which is true,
     * and it converts to a failure, which is the answer.
     */
    private static DetectedFormat formatFor(BrokenCheck.Result detected) {
        return detected.format() == DetectedFormat.FLOOR_STOPPED
                ? DetectedFormat.UNRECOGNISED
                : detected.format();
    }

    private SeedConversions() {
    }
}
