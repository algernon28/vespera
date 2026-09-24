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
    @DisplayName("A table's cells are measured as text, so a spreadsheet is not measured as empty")
    @Issue("275")
    @Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
    void aTablesCellsAreMeasuredAsText() {
        String spreadsheet =
                """
                {"document":{"json_content":{
                  "body":{"children":[{"$ref":"#/tables/0"}]},
                  "texts":[],
                  "tables":[{"children":[],"data":{"table_cells":[
                    {"text":"Merchant","start_row_offset_idx":0,"start_col_offset_idx":0},
                    {"text":"ACME Srl","start_row_offset_idx":1,"start_col_offset_idx":0}]}}]}}}
                """;

        claim(
                "a response carrying no text items and one table measures as that table's text, which is the"
                        + " text the no-text floor reads -- so the floor judges a spreadsheet by its cells",
                () -> assertThat(ExtractedText.from(spreadsheet).text()).isEqualTo("Merchant ACME Srl"));
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
