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
}
