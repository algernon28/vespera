package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a Docling response's extracted text is (ADR-145): its text items and its tables' cells, in the
 * reading order Docling's own {@code body} tree gives them, each read once. Every reader of extracted
 * text -- the metrics and the no-text floor, the shingler, the chunker, the seed side -- reads this one
 * slice, so what one of them sees is what all of them see.
 *
 * <p>The fixtures are hand-written in the shape a real {@code /v1/convert/file} response takes: a
 * {@code body} whose children are references into {@code texts}, {@code tables} and {@code groups}, and
 * a table whose rich cells point at groups of text items that are also listed in {@code texts}.
 */
@Epic("Extraction")
@Feature("Stage 2's extracted text")
@Issue("275")
@Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
class DoclingDocumentTextsTest {

    /** Docling's own label for a table, which each of a table's rows is read under. */
    private static final String TABLE = "table";

    /** A paragraph, then a two-row table, then another paragraph, all in body order. */
    private static final String A_TABLE_BETWEEN_TWO_PARAGRAPHS =
            """
            {"document":{"json_content":{
              "body":{"children":[{"$ref":"#/texts/0"},{"$ref":"#/tables/0"},{"$ref":"#/texts/1"}]},
              "texts":[
                {"text":"The fields are listed below.","label":"text"},
                {"text":"Every field is mandatory.","label":"text"}],
              "tables":[{"children":[],"data":{"table_cells":[
                {"text":"Field","start_row_offset_idx":0,"start_col_offset_idx":0},
                {"text":"Length","start_row_offset_idx":0,"start_col_offset_idx":1},
                {"text":"TID","start_row_offset_idx":1,"start_col_offset_idx":0},
                {"text":"8","start_row_offset_idx":1,"start_col_offset_idx":1}]}}]}}}
            """;

    @Test
    @Story("A table is part of the document's text")
    @DisplayName("A table's cells are read as text, one line per row, where the table sits in the document")
    void readsATableRowByRowInReadingOrder() {
        List<DocumentText> texts = DoclingDocumentTexts.parse(A_TABLE_BETWEEN_TWO_PARAGRAPHS);

        claim(
                "the table is read between the paragraph before it and the paragraph after it, one line per"
                        + " row with its cells in column order, rather than left out of the text altogether",
                () -> assertThat(texts)
                        .containsExactly(
                                new DocumentText("The fields are listed below.", "text"),
                                new DocumentText("Field; Length", TABLE),
                                new DocumentText("TID; 8", TABLE),
                                new DocumentText("Every field is mandatory.", "text")));
    }

    @Test
    @Story("A table is part of the document's text")
    @DisplayName("A spreadsheet, which converts to tables and nothing else, has text")
    void aSpreadsheetHasText() {
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
                "a document whose whole content is a table reads as that table's text, which is what keeps"
                        + " the no-text floor from removing every spreadsheet as empty",
                () -> assertThat(DoclingDocumentTexts.lines(spreadsheet)).isEqualTo("Merchant\nACME Srl"));
    }

    @Test
    @Story("A table is part of the document's text")
    @DisplayName("A cell spanning several columns is read once, and an empty cell adds nothing")
    void readsASpanningCellOnceAndSkipsEmptyCells() {
        String spanned =
                """
                {"document":{"json_content":{
                  "body":{"children":[{"$ref":"#/tables/0"}]},
                  "texts":[],
                  "tables":[{"children":[],"data":{"table_cells":[
                    {"text":"Issue List","start_row_offset_idx":0,"start_col_offset_idx":0,"col_span":3},
                    {"text":"1","start_row_offset_idx":1,"start_col_offset_idx":0},
                    {"text":"  ","start_row_offset_idx":1,"start_col_offset_idx":1},
                    {"text":"open","start_row_offset_idx":1,"start_col_offset_idx":2}]}}]}}}
                """;

        claim(
                "the heading cell that spans three columns is read once, not once per column it covers,"
                        + " and the blank cell in the next row leaves no empty field behind",
                () -> assertThat(DoclingDocumentTexts.lines(spanned)).isEqualTo("Issue List\n1; open"));
    }

    /**
     * A {@code .docx} table whose cells hold paragraphs: Docling lists those paragraphs in {@code texts}
     * too, under a group the cell points at, and the cell's own text already carries them. Measured on
     * the GesPOS corpus, 3,060 of 3,061 such items were found in their cell's text.
     */
    @Test
    @Story("A table is part of the document's text")
    @DisplayName("A paragraph inside a table cell is read once, as part of its row, not a second time on its own")
    void readsAParagraphInsideACellOnce() {
        String richCell =
                """
                {"document":{"json_content":{
                  "body":{"children":[{"$ref":"#/tables/0"}]},
                  "groups":[{"children":[{"$ref":"#/texts/0"}]}],
                  "texts":[{"text":"Sent every night.","label":"text"}],
                  "tables":[{"children":[{"$ref":"#/groups/0"}],"data":{"table_cells":[
                    {"text":"Schedule","start_row_offset_idx":0,"start_col_offset_idx":0},
                    {"text":"Sent every night.","start_row_offset_idx":0,"start_col_offset_idx":1,
                     "ref":{"$ref":"#/groups/0"}}]}}]}}}
                """;

        claim(
                "the paragraph appears once, inside its row, so a table in a Word document does not count its"
                        + " own contents twice",
                () -> assertThat(DoclingDocumentTexts.lines(richCell)).isEqualTo("Schedule; Sent every night."));
    }

    @Test
    @Story("A table is part of the document's text")
    @DisplayName("Text nested in a group is read where the group sits, and page headers and footers come after the body")
    void readsNestedTextInPlaceAndFurnitureAfterTheBody() {
        String nested =
                """
                {"document":{"json_content":{
                  "body":{"children":[{"$ref":"#/groups/0"},{"$ref":"#/texts/2"}]},
                  "furniture":{"children":[{"$ref":"#/texts/0"}]},
                  "groups":[{"children":[{"$ref":"#/texts/1"}]}],
                  "texts":[
                    {"text":"Company confidential","label":"page_header"},
                    {"text":"a list item","label":"list_item"},
                    {"text":"a closing paragraph","label":"text"}]}}}
                """;

        claim(
                "the list item inside the group comes first, where the group sits in the body, and the page"
                        + " header comes last with the rest of the page furniture, still under its own label so"
                        + " the chunker can leave it out",
                () -> assertThat(DoclingDocumentTexts.parse(nested))
                        .containsExactly(
                                new DocumentText("a list item", "list_item"),
                                new DocumentText("a closing paragraph", "text"),
                                new DocumentText("Company confidential", "page_header")));
    }

    @Test
    @Story("A response with no reading order")
    @DisplayName("A response with no body tree is read from its text items in the order they are listed")
    void readsTheTextItemsInListOrderWhereThereIsNoBody() {
        String withoutBody =
                """
                {"document":{"json_content":{"texts":[{"text":"Chapter One."},{"text":"  "},{"text":"It was dark."}]}}}
                """;

        claim(
                "without a body there is no reading order to follow, so the text items are read as they are"
                        + " listed, blank ones dropped and a missing label read as Docling's generic one",
                () -> assertThat(DoclingDocumentTexts.parse(withoutBody))
                        .containsExactly(new DocumentText("Chapter One.", "text"), new DocumentText("It was dark.", "text")));
        claim(
                "and a response with no document at all is no text rather than an error",
                () -> assertThat(DoclingDocumentTexts.lines("{}")).isEmpty());
    }
}
