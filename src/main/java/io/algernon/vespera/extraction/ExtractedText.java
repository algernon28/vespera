package io.algernon.vespera.extraction;

import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The text and page count read out of a Docling response's own {@code rawResponse} (ADR-070): the
 * JSON/{@code DoclingDocument} export the client requested. The text is {@link DoclingDocumentTexts}'
 * reading of it, text items and table rows alike (ADR-145), page headers and footers included: the metrics
 * count them, and the floor's judgement of the text outside them is {@link UsableText}'s (ADR-232); the page count is the size of its
 * {@code pages} object, present for a paginated document only.
 *
 * <p>Read directly off {@code rawResponse} rather than off a narrower Java shape (ADR-070's own
 * reason for keeping the whole response verbatim): this module models only what a verdict or metric
 * needs, and leaves the rest of Docling's export alone.
 */
final class ExtractedText {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final String text;
    private final Integer pageCount;

    private ExtractedText(String text, Integer pageCount) {
        this.text = text;
        this.pageCount = pageCount;
    }

    /** Parses {@code rawResponse}, tolerating a response that carries no {@code document} at all. */
    static ExtractedText from(String rawResponse) {
        JsonNode root = JSON_MAPPER.readTree(rawResponse);
        JsonNode content = root.path("document").path("json_content");
        // ADR-145: the one reading of extracted text, tables included, so the metrics measure every
        // item, as the shingler reads them; the chunker and tier 1 leave page headers and footers out (ADR-232).
        String joinedText = DoclingDocumentTexts.parse(rawResponse).stream()
                .map(DocumentText::text)
                .collect(Collectors.joining(" "))
                .strip();
        JsonNode pages = content.path("pages");
        Integer pageCount = pages.isObject() ? pages.size() : null;
        return new ExtractedText(joinedText, pageCount);
    }

    /** The document's extracted text, concatenated in document order. Never {@code null}, possibly empty. */
    String text() {
        return text;
    }

    /** How many pages Docling reported, or {@code null} for an unpaginated format's response. */
    Integer pageCount() {
        return pageCount;
    }
}
