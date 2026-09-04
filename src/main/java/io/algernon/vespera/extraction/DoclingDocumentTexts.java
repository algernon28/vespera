package io.algernon.vespera.extraction;

import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the structural text items off a Docling response's own {@code document.json_content.texts}
 * (ADR-029) — the JSON/{@code DoclingDocument} export {@link DoclingClient} already requests for
 * exactly this reason. An item with blank text is dropped; a missing {@code label} reads as
 * {@code "text"}, Docling's own generic fallback label, rather than failing the whole document over
 * one under-described item.
 */
final class DoclingDocumentTexts {

    /** Docling's own fallback label for a text item that carries no more specific one. */
    private static final String DEFAULT_LABEL = "text";

    // Lenient like DoclingClient's own mapper: this reads a small slice of a payload most of which
    // this module does not model at all, and a field this class doesn't know about is not this
    // class's business to fail over.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private DoclingDocumentTexts() {}

    /** The document's structural text items, in document order, or empty when the export carries none. */
    static List<DocumentText> parse(String rawDoclingResponse) {
        ResponseEnvelope envelope = MAPPER.readValue(rawDoclingResponse, ResponseEnvelope.class);
        if (envelope.document() == null
                || envelope.document().jsonContent() == null
                || envelope.document().jsonContent().texts() == null) {
            return List.of();
        }
        return envelope.document().jsonContent().texts().stream()
                .filter(item -> item.text() != null && !item.text().isBlank())
                .map(item -> new DocumentText(item.text(), item.label() == null ? DEFAULT_LABEL : item.label()))
                .toList();
    }

    /** The subset of a {@code /v1/convert/file} response this class reads. */
    private record ResponseEnvelope(DocumentWrapper document) {}

    private record DocumentWrapper(JsonContent jsonContent) {}

    private record JsonContent(List<TextItem> texts) {}

    private record TextItem(String text, String label) {}
}
