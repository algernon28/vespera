package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.FailureCategory;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conversions a test scripts by file name where {@code SeedScriptedExtractionBeans}' one text will not do. That text is
 * every document's, so every shingle is boilerplate and stage 4 signs nothing: a test of what a preset does to shingles,
 * signatures or a pair of documents needs documents that differ (ADR-330 §9).
 */
final class TextsOfTheirOwn {

    private TextsOfTheirOwn() {
    }

    /** {@code count} words beginning {@code stem}, each a word no other stem shares. */
    static String words(String stem, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(word -> stem + word).collect(Collectors.joining(" "));
    }

    /** A successful conversion whose one text item is {@code text}. */
    static DoclingResponse converted(String text) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null,
                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + text + "\",\"label\":\"text\"}]}}}");
    }

    /** The answer of a converter reporting it ran out of time on the file. */
    static DoclingResponse timedOut() {
        return new DoclingResponse(ConversionStatus.FAILURE,
                List.of(new DoclingError("document_backend", "docling", "ran out of time", FailureCategory.TIMEOUT, null)),
                0d, null, "{}");
    }
}
