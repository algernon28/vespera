package io.algernon.vespera.pipeline;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The plain text one Docling response's raw payload carries (ADR-073) — the shared source
 * {@code pipeline} hands, once per converted document, to both {@code extraction}'s metric writer and
 * {@code similarity}'s shingler, so the document is opened exactly once. Neither capability module owns
 * this: it reads the JSON/{@code DoclingDocument} export ADR-071's call shape requested, which is
 * {@code pipeline}'s own concern to compose from, not a dependency either module would need the other
 * for.
 *
 * <p>Reads only {@code document.json_content.texts[].text} — table cell text, picture captions and
 * layout are not part of "extracted text" here. This is this ticket's own reading of what "extracted
 * text" means for a shingler that only ever sees prose-like passages; #48's metric writer is free to
 * read a wider slice of the same export for its own columns.
 */
final class Stage2ExtractedText {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private Stage2ExtractedText() {}

    static String of(String rawResponse) {
        JsonNode texts =
                JSON_MAPPER.readTree(rawResponse).path("document").path("json_content").path("texts");
        if (!texts.isArray()) {
            return "";
        }
        return StreamSupport.stream(texts.spliterator(), false)
                .map(item -> item.path("text").asString(""))
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
