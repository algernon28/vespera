package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.TopicsValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel links no class of the jar (ADR-285 §3), so it holds ADR-435 §2's rule for a name made plain in a
 * copy of its own, {@code TopicEntries}; this holds the two copies to one answer, so that the window never lets through
 * a list the jar then refuses, nor refuses one the jar would take. A test may name both.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("898")
@Link(name = "ADR-435", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class TheWindowAndTheJarMakeANamePlainAlikeTest {

    /**
     * Names that differ in case, in accents written whole or as a letter and a combining mark, in the blanks around and
     * between their words, in letters with no plain form, and in a script with no case.
     */
    private static final List<String> NAMES = List.of(
            "Pagamenti", "  PAGAMENTI ", "pagamenti\t",
            "Qualità dell'acqua", "Qualità dell'acqua", "QUALITA DELL'ACQUA",
            "Conti correnti", "Conti  correnti",
            "Ærø", "Straße", "İstanbul", "Łódź",
            "税务", "한국어", "Ελληνικά", "ελληνικα");

    @Test
    @Story("Two names are one topic where they differ only in case, accents or the spaces around them")
    @DisplayName("The window and the jar make every name of a table of cases, accents, blanks and scripts plain alike")
    void eachNameIsMadePlainAlike() {
        List<String> byTheWindow = NAMES.stream().map(TopicEntries::identityOf).toList();
        List<String> byTheJar = NAMES.stream().map(TopicsValue::identityOf).toList();

        claim("for each of the " + NAMES.size() + " names, the window's plain form is the jar's",
                () -> assertThat(byTheWindow).containsExactlyElementsOf(byTheJar));
    }
}
