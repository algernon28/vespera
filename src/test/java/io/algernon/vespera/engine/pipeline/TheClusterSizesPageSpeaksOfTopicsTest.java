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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cluster-sizes.html} and its JSON say topic where they said exemplar (ADR-409 §8): its rows are topics, so
 * every word that spoke of a document matched to an exemplar speaks of a document placed under a topic, and the JSON's
 * keys that named a partition or an exemplar name a topic, its format raised to 2 for them (ADR-362 §9), and to 3 for
 * the subtopic's column and the share's (ADR-416 §5).
 *
 * <p>Drawn by {@link ClusterSizeReport} alone, over one topic of two groups and over none.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("832")
@Issue("834")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheClusterSizesPageSpeaksOfTopicsTest {

    /** The page's first paragraph, and the first paragraph of its JSON's first section, whole. */
    private static final String THE_OPENING = "Each document was placed under one topic, and the documents placed under"
            + " the same topic were then grouped among themselves by how alike they are, within each of its subtopics"
            + " where a crowded topic is divided. Nobody said how many groups to look for: the groups are whatever the"
            + " documents turned out to form, and this page is what they turned out to be. Nothing here was removed,"
            + " merged or renamed. Each topic's row names it by the seed document its first passage is in, and that"
            + " passage's number, counted from 0.";

    /** What the page and its JSON say where no document was placed. */
    private static final String NOTHING_PLACED = "No document was placed under a topic, so there was nothing to group.";

    /** The first paragraph under <em>How to read this</em>, whole, its markup with it. */
    private static final String GROUPS_OF_ONE = "<em>Groups of one</em> is the number to look at first. A document"
            + " alone in its group is a real answer rather than a mistake: it means the document was placed under the"
            + " topic on its own merits and not because it belongs with the others. A topic whose documents are nearly"
            + " all alone is telling you something about the topic, and no amount of regrouping would change it.";

    /** The second, whole. */
    private static final String MIDDLE = "The <em>middle group</em> sits halfway up the sizes, which says more than an"
            + " average would: one group holding most of a topic's documents pulls an average upwards and leaves the"
            + " impression of evenly-sized groups that are not there.";

    /** The fourth's second sentence, whole. */
    private static final String STRONGEST = "If even it is low, the documents in that topic's groups were put together"
            + " for want of anything better and not because they resemble one another, and the groups will read as"
            + " lists of unrelated documents under one heading.";

    /** The fifth, whole. */
    private static final String ONE_DOCUMENT = "A topic with one document has no link at all, shown as"
            + " <code>&mdash;</code>: there is no pair for a number to describe.";

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-17T09:00:00Z");

    private static final List<ClusterSizeReport.Topic> ONE_TOPIC =
            List.of(ClusterSizeReport.Topic.of("seeds/one.docx", 3, List.of(2, 1), Optional.empty()));

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("The group sizes page and its JSON open by saying each document was placed under a topic")
    void thePageOpensSayingTheDocumentsWerePlacedUnderTopics() {
        String page = ClusterSizeReport.render(ONE_TOPIC);
        AnalysisReportJson.Section first = ClusterSizeReport.document(ONE_TOPIC, WRITTEN_AT).sections().getFirst();

        claim("the page's first paragraph says each document was placed under one topic and grouped within it, and how"
                        + " a topic's row names it",
                () -> assertThat(page).contains("<p>" + THE_OPENING + "</p>\n"));
        claim("and the JSON's first section, topics, opens with the same words",
                () -> assertThat(List.of(first.id(), first.text().getFirst())).containsExactly("topics", THE_OPENING));
    }

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("The group sizes page reads its numbers of a topic, and says exemplar nowhere, nor does its JSON")
    void theWordsThatSaidExemplarSpeakOfTopics(@TempDir Path folder) throws IOException {
        String page = ClusterSizeReport.render(ONE_TOPIC);
        AnalysisReportJson.Document document = ClusterSizeReport.document(ONE_TOPIC, WRITTEN_AT);
        Path json = folder.resolve("cluster-sizes.json");
        AnalysisReportJson.write(json, document);
        AnalysisReportJson.Table table = document.sections().getFirst().table();

        claim("the table's first column is headed Topic, before the subtopic, the documents and their share",
                () -> assertThat(page).contains(
                        "<tr><th>Topic</th><th>Subtopic</th><th>Documents</th><th>Share of documents</th>"));
        claim("under How to read this, groups of one, the middle group, the strongest link and a single document are"
                        + " each said of a topic",
                () -> assertThat(page).contains(
                        "<p>" + GROUPS_OF_ONE + "</p>\n",
                        "<p>" + MIDDLE + "</p>\n",
                        STRONGEST,
                        "<p>" + ONE_DOCUMENT + "</p>\n"));
        claim("the JSON's first column is topic, labelled Topic, of text, since a topic's key is no path",
                () -> assertThat(table.columns().getFirst())
                        .isEqualTo(new AnalysisReportJson.Column("topic", "Topic", "text")));
        claim("its headline's first figure is topics, labelled Topics grouped, counting the one topic",
                () -> assertThat(document.headline().getFirst())
                        .isEqualTo(new AnalysisReportJson.Figure("topics", "Topics grouped", 1L, "count")));
        claim("its sections are topics, how-to-read and what-this-page-cannot-see",
                () -> assertThat(document.sections().stream().map(AnalysisReportJson.Section::id).toList())
                        .containsExactly("topics", "how-to-read", "what-this-page-cannot-see"));
        claim("its format is 3: a key renamed and a column's meaning changed were a break, and a column inserted for"
                        + " the subtopic is another (ADR-362 section 9)",
                () -> assertThat(List.of(document.format(), ClusterSizeReport.FORMAT)).containsExactly(3, 3));
        claim("and no word of the page or of its JSON speaks of an exemplar",
                () -> assertThat(List.of(page, Files.readString(json, StandardCharsets.UTF_8)))
                        .allSatisfy(written -> assertThat(written).doesNotContainIgnoringCase("exemplar")));
    }

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("Where no document was placed, the group sizes page and its JSON say none was placed under a topic")
    void aPageOfNoTopicSaysNoDocumentWasPlacedUnderATopic() {
        String page = ClusterSizeReport.render(List.of());
        AnalysisReportJson.Document document = ClusterSizeReport.document(List.of(), WRITTEN_AT);

        claim("the page says no document was placed under a topic, so there was nothing to group",
                () -> assertThat(page).contains("<p>" + NOTHING_PLACED + "</p>\n"));
        claim("the JSON's one section, topics, holds the opening and that sentence, and no table",
                () -> assertThat(document.sections()).singleElement().satisfies(section -> {
                    assertThat(section.id()).isEqualTo("topics");
                    assertThat(section.text()).containsExactly(THE_OPENING, NOTHING_PLACED);
                    assertThat(section.table()).isNull();
                }));
        claim("and its headline counts no topic",
                () -> assertThat(document.headline().getFirst())
                        .isEqualTo(new AnalysisReportJson.Figure("topics", "Topics grouped", 0L, "count")));
    }
}
