package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The words of the 6a gate's page, and of its JSON, once the arrangement is of topics (ADR-409 §7): the first
 * paragraph says what the names are and that they are not approved, and no sentence speaks of an exemplar, the topics
 * standing where the seed partitions stood and a group's order and its <em>Named after</em> being of relevance.
 *
 * <p>Drawn by {@link ArrangementReport} alone, with no topic, so that what is read is the page's own furniture; the
 * sections a topic and a subtopic add are held by {@link TopicNamesAreStoredAndShownInvocationTest} and {@link
 * SubtopicsAreNamedAndShownInvocationTest}.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class TheArrangementPageSpeaksOfTopicsTest {

    /** The page's first paragraph, and the first string of its JSON's {@code overview} section, whole. */
    private static final String THE_OPENING = "Every document that survived is here, placed under one topic and in the"
            + " group it formed with the documents nearest it. Nothing has been removed, merged or renamed to produce this"
            + " page. The names of the topics, and of the subtopics a crowded topic is divided into, were written by the"
            + " naming model from the documents' own words, except where a paragraph under a name says otherwise. They"
            + " are shown here to read: they are not part of what you approve, and they cannot be changed here. Every"
            + " group's name below was taken from a document you can open.";

    /** The first paragraph under <em>How to read this</em>, whole. */
    private static final String THE_ORDER = "Topics are in order of how much of the archive sits under them, largest"
            + " first, so the substantial part of what you have is at the top, and a crowded topic's subtopics are in the"
            + " same order within it. Within each topic, or within each of its subtopics where it is divided, groups"
            + " holding several documents come before groups holding one, and within those two tiers the groups whose"
            + " documents scored highest for relevance come first.";

    /** The second paragraph under <em>How to read this</em>, whole, its markup with it. */
    private static final String NAMED_AFTER = "<em>Named after</em> is the document each group's name was taken from"
            + " — the one in the group that scored highest for relevance, and a link straight to where it already sits."
            + " Open a few. If a name does not describe the group it heads, that is what this page is for, and grouping"
            + " the documents again with different settings is cheap compared with what comes next.";

    /** What the page and its JSON say where no document was placed. */
    private static final String NOTHING_PLACED = "No document was placed under a topic, so there is nothing to arrange.";

    private static final String AN_APPROVAL_NAME = "an-approval-name";

    private static final String THE_ARCHIVE = "/an/archive";

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-17T09:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page opens by saying the names were written by the naming model and are not approved")
    void thePageOpensSayingWhatTheNamesAre() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, AN_APPROVAL_NAME, THE_ARCHIVE);
        StringWriter json = new StringWriter();
        AnalysisReportJson report = ArrangementReport.openJson(json, AN_APPROVAL_NAME, THE_ARCHIVE, WRITTEN_AT);
        ArrangementReport.closeJson(report, 1, 1, 1);
        JsonNode overview = JSON.readTree(json.toString()).get("sections").get(0);

        claim("the page's first paragraph says every survivor is placed under one topic, that the names were written by"
                        + " the naming model save where a note says otherwise, and that they are shown to read, not"
                        + " approved and not changed here",
                () -> assertThat(page.toString()).contains("<p>" + THE_OPENING + "</p>\n"));
        claim("and the JSON's overview section opens with the same words",
                () -> assertThat(List.of(overview.get("id").asString(), overview.get("text").get(0).asString()))
                        .containsExactly("overview", THE_OPENING));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("The approval page says topic where it said exemplar, and says what orders a group and names it")
    void theWordsThatSaidExemplarSpeakOfTopicsAndRelevance() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, AN_APPROVAL_NAME, THE_ARCHIVE);
        ArrangementReport.close(page, 1);
        StringWriter json = new StringWriter();
        AnalysisReportJson report = ArrangementReport.openJson(json, AN_APPROVAL_NAME, THE_ARCHIVE, WRITTEN_AT);
        ArrangementReport.closeJson(report, 1, 1, 1);

        claim("under How to read this, the first paragraph says the topics, and a crowded topic's subtopics, go by how"
                        + " much sits under them, and a group's place within them by its documents' relevance",
                () -> assertThat(page.toString()).contains("<p>" + THE_ORDER + "</p>\n"));
        claim("the second says a group is named after the document in it that scored highest for relevance",
                () -> assertThat(page.toString()).contains("<p>" + NAMED_AFTER + "</p>\n"));
        claim("and no word of the page or of its JSON speaks of an exemplar",
                () -> assertThat(List.of(page.toString(), json.toString()))
                        .allSatisfy(written -> assertThat(written).doesNotContainIgnoringCase("exemplar")));
    }

    @Test
    @Story("The topics and their names are shown at the approval, to read and not to change")
    @DisplayName("Where no document was placed, the page and its JSON say none was placed under a topic")
    void anArrangementWithNothingPlacedSaysNoDocumentWasPlacedUnderATopic() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, AN_APPROVAL_NAME, THE_ARCHIVE);
        ArrangementReport.close(page, 0);
        StringWriter json = new StringWriter();
        AnalysisReportJson report = ArrangementReport.openJson(json, AN_APPROVAL_NAME, THE_ARCHIVE, WRITTEN_AT);
        ArrangementReport.closeJson(report, 0, 0, 0);
        JsonNode written = JSON.readTree(json.toString());

        claim("the page says no document was placed under a topic, so there is nothing to arrange",
                () -> assertThat(page.toString()).contains("<p>" + NOTHING_PLACED + "</p>\n"));
        claim("the JSON's last section says the same, titled Nothing to arrange",
                () -> assertThat(TopicNamesAreStoredAndShownInvocationTest.sectionsOf(written)).last()
                        .isEqualTo("nothing: Nothing to arrange " + List.of(NOTHING_PLACED) + " no table"));
        claim("and its headline counts topics, none, then groups and documents",
                () -> assertThat(TopicNamesAreStoredAndShownInvocationTest.headlineOf(written)).containsExactly(
                        "topics Topics 0", "clusters Groups 0", "documents Documents 0"));
    }
}
