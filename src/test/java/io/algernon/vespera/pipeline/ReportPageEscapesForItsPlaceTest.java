package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A hover text is written into a double-quoted {@code title}, so it is escaped for an attribute, both quotes included;
 * text between tags keeps the three-character escape (ADR-275 §3).
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-275", url = Adr.EVERY_DIV_SAYS_ON_HOVER_WHAT_IT_SHOWS_AND_EVERY_STEP_WHAT_IT_DOES, type = "adr")
class ReportPageEscapesForItsPlaceTest {

    private static final String EVERY_SPECIAL = "a & b < c > d \" e ' f";

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("A value written into an attribute has all five special characters escaped, and text between tags only the first three")
    void anAttributeEscapesBothQuotesAndTextDoesNot() {
        claim(
                "an attribute's value has & < > \" and ' escaped",
                () -> assertThat(ReportPage.escapeAttribute(EVERY_SPECIAL))
                        .isEqualTo("a &amp; b &lt; c &gt; d &quot; e &#39; f"));
        claim(
                "text between tags has & < and > escaped and both quotes left as they are",
                () -> assertThat(ReportPage.escape(EVERY_SPECIAL)).isEqualTo("a &amp; b &lt; c &gt; d \" e ' f"));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("A table card refuses to be written without a hover text, and writes the one it has escaped in its title")
    void aTableCardNeedsAHoverText() {
        claim(
                "a table card with no hover text, or a blank one, is refused",
                () -> {
                    assertThatThrownBy(() -> ReportPage.tableCard(null, "", "")).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> ReportPage.tableCard(" ", "", "")).isInstanceOf(IllegalArgumentException.class);
                });
        claim(
                "and one with a hover text carries it, escaped, as its title",
                () -> assertThat(ReportPage.tableCard("the working directory's steps", "", ""))
                        .startsWith("<div class=\"table-card\" title=\"the working directory&#39;s steps\">"));
    }
}
