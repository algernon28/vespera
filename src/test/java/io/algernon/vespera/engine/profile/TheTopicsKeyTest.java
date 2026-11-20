package io.algernon.vespera.engine.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the profile's {@code topics} says (ADR-435 §1 to §3): one string of one name a line, read as unset, as the names
 * in order, or as refused for a blank entry or a duplicate, the line refusing it whole; and a name made plain, by which
 * two names are one topic.
 *
 * <p>The reading is the profile's own, so this is where it is held; that {@code vespera run} refuses with it at its start
 * is {@code AListOfTopicsWithADuplicateRefusesTheRunInvocationTest}'s.
 */
@Epic("Census")
@Feature("Profile")
@Issue("898")
@Link(name = "ADR-435", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class TheTopicsKeyTest {

    /** Who wrote each value here. */
    private static final String PROVENANCE = "set by this test";

    /** A name with an accent, written as the operator first wrote it. */
    private static final String WITH_AN_ACCENT = "Qualità";

    /** The same topic as {@link #WITH_AN_ACCENT}: another case, no accent, blanks around it. */
    private static final String THE_SAME_TOPIC_AGAIN = " qualita ";

    /**
     * A name after the duplicate, so that the duplicate's line keeps the blanks after it: the value's blanks at its end
     * are removed before it is split.
     */
    private static final String A_LAST_NAME = "Incassi";

    private static TopicsValue topics(String value) {
        return new TopicsValue(value, PROVENANCE, null);
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("A list of topics that is missing, blank, or line breaks alone says nothing, and the topics are found as before")
    void anUnsetOrBlankValueReadsUnset() {
        claim("a value of null reads unset", () -> assertThat(topics(null).reading())
                .isInstanceOf(TopicsValue.Unset.class));
        claim("a value of blanks reads unset", () -> assertThat(topics("   \t ").reading())
                .isInstanceOf(TopicsValue.Unset.class));
        claim("a value of line breaks alone reads unset", () -> assertThat(topics("\n\r\n\n").reading())
                .isInstanceOf(TopicsValue.Unset.class));
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("Each line of the list is a topic's name, in the order written, with the blanks around it removed and nothing else changed")
    void eachLineIsANameInOrderItsBlanksRemoved() {
        TopicsValue.Reading reading = topics("Pagamenti\r\n  Incassi e rimborsi  \nFornitori\n").reading();

        claim("the three lines are read as three names, in the order written, the blanks around the second removed and"
                        + " the blank between its words kept",
                () -> assertThat(reading).isEqualTo(new TopicsValue.Answered(
                        List.of("Pagamenti", "Incassi e rimborsi", "Fornitori"))));
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("A list of one name is a list: one topic")
    void oneNameIsAList() {
        claim("one line is read as one name",
                () -> assertThat(topics("Pagamenti").reading())
                        .isEqualTo(new TopicsValue.Answered(List.of("Pagamenti"))));
    }

    @Test
    @Story("Two names are one topic where they differ only in case, accents or the spaces around them")
    @DisplayName("A name is made plain by its case, its accents and the blanks around it, and the blanks between its words still count")
    void aNameIsMadePlainByCaseAccentsAndTheBlanksAroundIt() {
        claim("'  Qualità dell'Acqua ' is made plain as 'qualita dell'acqua'",
                () -> assertThat(TopicsValue.identityOf("  Qualità dell'Acqua ")).isEqualTo("qualita dell'acqua"));
        claim("a name and the same name with two blanks between its words are made plain apart",
                () -> assertThat(TopicsValue.identityOf("Conti  correnti"))
                        .isNotEqualTo(TopicsValue.identityOf("Conti correnti")));
        claim("so a list of the two is answered with both names",
                () -> assertThat(topics("Conti correnti\nConti  correnti").reading())
                        .isEqualTo(new TopicsValue.Answered(List.of("Conti correnti", "Conti  correnti"))));
    }

    @Test
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("A blank line among the names is refused, the refusal naming its line")
    void aBlankEntryIsRefusedNamingItsLine() {
        claim("a blank second line is refused in the record's line, whole",
                () -> assertThat(topics("Pagamenti\n   \nIncassi").reading()).isEqualTo(new TopicsValue.Refused(
                        "vespera run refused the topics in profile.yaml: line 2 is blank, and each line names one"
                                + " topic. Remove it.")));
    }

    @Test
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("A name that is an earlier name again but for case, accents or the spaces around it is refused, the refusal naming both lines")
    void aDuplicateIsRefusedNamingBothLines() {
        claim("the second line, the first's topic again, is refused in the record's line, whole, naming it as written"
                        + " and the earlier name",
                () -> assertThat(topics(WITH_AN_ACCENT + "\n" + THE_SAME_TOPIC_AGAIN + "\n" + A_LAST_NAME).reading())
                        .isEqualTo(new TopicsValue.Refused("vespera run refused the topics in profile.yaml: line 2, \""
                                + THE_SAME_TOPIC_AGAIN + "\", is the topic \"" + WITH_AN_ACCENT + "\" of line 1 again,"
                                + " two names being one topic where they differ only in case, accents or the spaces"
                                + " around them. Remove one of them.")));
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("The list of topics is a profile key standing after the folders not walked and before the Confluence values")
    void theKeyStandsAfterTheFoldersNotWalked() {
        List<String> components = Arrays.stream(Profile.class.getRecordComponents()).map(RecordComponent::getName)
                .toList();
        int topics = components.indexOf("topics");

        claim("the profile has a key named topics, of the kind that holds a list of names",
                () -> assertThat(Profile.class.getRecordComponents()[topics].getType()).isEqualTo(TopicsValue.class));
        claim("directly after foldersNotWalked and directly before confluenceBaseUrl",
                () -> assertThat(components.subList(topics - 1, topics + 2))
                        .containsExactly("foldersNotWalked", "topics", "confluenceBaseUrl"));
    }
}
