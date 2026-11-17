package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.GivenName;
import io.algernon.vespera.engine.synthesis.NameOrigin;
import io.algernon.vespera.engine.synthesis.NameTurnDown;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a topic's name fell back, the 6a gate's page says directly under its heading why the model's name was not
 * used, in the words ADR-409 §7's table gives each of ADR-407 §6's seven turn-downs.
 *
 * <p>Drawn by {@link ArrangementReport} alone, one topic for each turn-down {@link NameTurnDown} has, each named apart,
 * so that every pairing of a turn-down with its words is read under the heading of the topic that carries it: two
 * turn-downs' words swapped, or one turn-down given no words, fail it. The invocations of {@link
 * TopicNamesAreStoredAndShownInvocationTest} and {@link SubtopicsAreNamedAndShownInvocationTest} hold that a fallback
 * recorded reaches the page; this holds what the page says of each.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TheArrangementPageSaysWhyEachFallbackStandsTest {

    /** Each turn-down's words, as ADR-409 §7's table writes them for a topic. */
    private static final Map<NameTurnDown, String> WHY = new EnumMap<>(Map.of(
            NameTurnDown.NOTHING_SENT, "nothing of it fitted what the model is given to read, so the model was not asked",
            NameTurnDown.QUESTION_CUT, "the question was longer than the model could read at once",
            NameTurnDown.RAN_OUT_OF_ROOM, "the model's answer reached its length limit before it was finished",
            NameTurnDown.NOT_THE_SHAPE_ASKED, "the model's answer did not come back in the shape it was asked for",
            NameTurnDown.EMPTY, "the model's answer was empty",
            NameTurnDown.NOT_ONE_SHORT_LINE, "the model's answer was not one short line",
            NameTurnDown.SAME_AS_AN_EARLIER_NAME, "asked a second time, the model again gave another topic's name"));

    /** The one cluster each topic holds, which the page draws under the paragraph. */
    private static final List<ArrangementReport.Cluster> ONE_CLUSTER = List.of(
            new ArrangementReport.Cluster("A group", 1, "lead.docx", "file:///an/archive/lead.docx"));

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("Under a name that fell back, the approval page gives the reason the model's name was not used, in that reason's own words")
    void eachTurnDownIsSaidInItsOwnWords() throws IOException {
        StringBuilder page = new StringBuilder();
        for (NameTurnDown turnDown : NameTurnDown.values()) {
            ArrangementReport.topic(page, ArrangementReport.Topic.undivided(
                    new GivenName(nameStandingInFor(turnDown), NameOrigin.FALLBACK, Optional.of(turnDown)), ONE_CLUSTER));
        }
        String[] eachHeadingAndItsParagraph = Arrays.stream(NameTurnDown.values())
                .map(turnDown -> "<h2>" + nameStandingInFor(turnDown) + "</h2>\n<p>The naming model's name for this topic"
                        + " was not used: " + WHY.get(turnDown) + ". The name above stands in for it, taken from the seed"
                        + " feeding the topic the most passages, or from the topic's place where that seed gives none.</p>\n")
                .toArray(String[]::new);

        claim("under each of the seven topics' headings stands the paragraph saying the model's name was not used, and"
                        + " why in the words of the reason that topic's name fell back for, and of no other",
                () -> assertThat(page.toString()).contains(eachHeadingAndItsParagraph));
    }

    /** The fallback name the topic turned down for {@code turnDown} carries, which no other topic here carries. */
    private static String nameStandingInFor(NameTurnDown turnDown) {
        return "Standing in after " + turnDown.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
