package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What {@code pipeline} reads as "extracted text" out of a Docling response's raw payload (ADR-073):
 * the {@code document.json_content.texts[].text} items, joined — the same text handed to both #48's
 * metric writer and {@code similarity}'s shingler from one open-document pass.
 */
@Epic("Extraction")
@Feature("Stage 2's extracted text")
@Issue("50")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
class Stage2ExtractedTextTest {

    @Test
    @Story("Reading extracted text out of the raw response")
    @DisplayName("Every text item's text is joined, in the order the document reported them")
    void joinsEveryTextItemInOrder() {
        String rawResponse =
                """
                {"document":{"json_content":{"texts":[{"text":"Chapter One."},{"text":"It was a dark night."}]}}}
                """;

        claim(
                "both text items appear, joined so a downstream reader sees one document's worth of"
                        + " prose rather than needing to know the export's item shape",
                () -> assertThat(Stage2ExtractedText.of(rawResponse)).isEqualTo("Chapter One.\nIt was a dark night."));
    }

    @Test
    @Story("Reading extracted text out of the raw response")
    @DisplayName("An empty texts array yields empty text rather than an error")
    void emptyTextsArrayYieldsEmptyText() {
        String rawResponse = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

        claim(
                "a document with no reported text items — the fixture shape this repo's own tests"
                        + " already use for a placeholder response — reads back as empty text, not a failure",
                () -> assertThat(Stage2ExtractedText.of(rawResponse)).isEmpty());
    }

    @Test
    @Story("Reading extracted text out of the raw response")
    @DisplayName("A blank text item is dropped rather than contributing an empty line")
    void blankTextItemIsDropped() {
        String rawResponse =
                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"Real content.\"},{\"text\":\"   \"}]}}}";

        claim(
                "a whitespace-only text item — Docling reports some as structural placeholders — never"
                        + " contributes a blank line that would otherwise widen every downstream shingle"
                        + " window across it",
                () -> assertThat(Stage2ExtractedText.of(rawResponse)).isEqualTo("Real content."));
    }
}
