package io.algernon.vespera.extraction;

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
 * The text and page count {@link ExtractedText} reads out of a Docling response's own
 * {@code rawResponse} (ADR-070) — the JSON/{@code DoclingDocument} export the client requested.
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
class ExtractedTextTest {

    @Test
    @Story("Reading extracted text")
    @DisplayName("The document's text items are joined in order into one string")
    void textItemsAreJoinedInOrder() {
        String raw =
                """
                {"document": {"json_content": {"texts": [{"text": "first"}, {"text": "second"}]}}}
                """;

        ExtractedText extracted = ExtractedText.from(raw);

        claim(
                "the two text items read back as one string, in the order they appeared",
                () -> assertThat(extracted.text()).isEqualTo("first second"));
    }

    @Test
    @Story("Reading extracted text")
    @DisplayName("A response with no document content at all reads as empty text and no page count")
    void aResponseCarryingNoDocumentReadsAsEmpty() {
        ExtractedText extracted = ExtractedText.from("{}");

        claim("no document content is empty text, not an error", () -> assertThat(extracted.text()).isEmpty());
        claim(
                "no document content means no page count either, the same unpaginated reading",
                () -> assertThat(extracted.pageCount()).isNull());
    }

    @Test
    @Story("Reading page count")
    @DisplayName("A paginated document's page count is the size of its pages object")
    void pageCountIsTheSizeOfThePagesObject() {
        String raw =
                """
                {"document": {"json_content": {"texts": [], "pages": {"1": {}, "2": {}, "3": {}}}}}
                """;

        ExtractedText extracted = ExtractedText.from(raw);

        claim("three page entries reads back as a page count of 3", () -> assertThat(extracted.pageCount()).isEqualTo(3));
    }

    @Test
    @Story("Reading page count")
    @DisplayName("An unpaginated document's response carries no pages object and no page count")
    void anUnpaginatedDocumentHasNoPageCount() {
        String raw =
                """
                {"document": {"json_content": {"texts": [{"text": "plain text, no pages"}]}}}
                """;

        ExtractedText extracted = ExtractedText.from(raw);

        claim(
                "a .docx/.txt response never reports pages, so the count is null rather than zero",
                () -> assertThat(extracted.pageCount()).isNull());
    }
}
