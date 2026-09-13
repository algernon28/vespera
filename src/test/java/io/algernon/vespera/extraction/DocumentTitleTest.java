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
 * A document's own title, as the converter labelled it (ADR-106, #175).
 *
 * <p>Read here rather than where it is used, because it lives inside the converter's response and
 * this module is the only one that parses those. It is handed out as a plain string, which is what
 * lets the terminal stages name a group after a document without naming this module.
 *
 * <p><b>Absent is the common case, not an error.</b> A plain text file and a scan that read as
 * undifferentiated body text carry no title item at all, and the caller has its own answer for that.
 */
@Epic("Extraction")
@Feature("A document's own title")
@Issue("175")
@Link(name = "ADR-106", url = Adr.A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE, type = "adr")
class DocumentTitleTest {

    /** The title the fixture below carries, and the one that has to come back. */
    private static final String TITLE = "2019 Site Safety Audit";

    @Test
    @Story("A document's title is read from what the converter labelled as one")
    @DisplayName("A document carrying a title reads back that title")
    void readsTheTitleItem() {
        String response = responseWith("""
                {"text": "2019 Site Safety Audit", "label": "title"},
                {"text": "Findings", "label": "section_header"},
                {"text": "The east stair was obstructed.", "label": "paragraph"}
                """);

        claim(
                "the title comes back, and it is the item the converter itself labelled a title rather"
                        + " than the first line of the document -- a scanned cover page and a running"
                        + " header are both first lines and neither is a title",
                () -> assertThat(DocumentTitle.of(response)).contains(TITLE));
    }

    @Test
    @Story("A document's title is read from what the converter labelled as one")
    @DisplayName("A document with headings but no title has no title")
    void hasNoTitleWhereNoneWasLabelled() {
        String response = responseWith("""
                {"text": "Findings", "label": "section_header"},
                {"text": "The east stair was obstructed.", "label": "paragraph"}
                """);

        claim(
                "a document whose first heading is a section heading has no title, rather than that"
                        + " heading being promoted into one: a section heading names a part of a"
                        + " document and would name the group after that part",
                () -> assertThat(DocumentTitle.of(response)).isEmpty());
    }

    @Test
    @Story("A document's title is read from what the converter labelled as one")
    @DisplayName("A document with no structure at all has no title")
    void hasNoTitleWhereThereIsNoStructure() {
        claim(
                "a document the converter found no structure in has no title, which is the ordinary case"
                        + " for a plain text file rather than a failure of any kind",
                () -> assertThat(DocumentTitle.of(responseWith(""))).isEmpty());
    }

    @Test
    @Story("A document's title is read from what the converter labelled as one")
    @DisplayName("A document carrying two titles is named by the first")
    void takesTheFirstOfTwoTitles() {
        String response = responseWith("""
                {"text": "2019 Site Safety Audit", "label": "title"},
                {"text": "Appendix A", "label": "title"}
                """);

        claim(
                "the first title wins, because document order is the only order there is here and a"
                        + " second title is an appendix or a bound-in second document rather than a"
                        + " better name for the first",
                () -> assertThat(DocumentTitle.of(response)).contains(TITLE));
    }

    /** A converter response carrying {@code items} as its structural text, and nothing else modelled. */
    private static String responseWith(String items) {
        return "{\"document\": {\"json_content\": {\"texts\": [" + items + "]}}}";
    }
}
