package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.RetainedEdgeSpread;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code cluster-sizes.html} keeps a row a topic and, under a divided topic, gives each of its subtopics a row of its
 * own, with the five numbers of its own clusters and the spread of its own graph; every row shows its share of the
 * documents grouped, and no share is a threshold (ADR-416 §5, §6).
 *
 * <p>Drawn by {@link ClusterSizeReport} alone. The fixture holds {@value #DOCUMENTS} documents: an undivided topic,
 * {@code seeds/a.txt} chunk 0, of two clusters of two; and a topic divided in two, {@code seeds/b.txt} chunk 4, whose
 * subtopic 1, of clusters of two and one, is given before its subtopic 0, of one cluster of one, as the clustering
 * step orders them, most documents first. The report draws the subtopics in the order it is given them.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("834")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class TheClusterSizesPageHasARowForEachSubtopicTest {

    /** The documents the two topics hold between them, the denominator of every share. */
    private static final int DOCUMENTS = 8;

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-18T09:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The schema {@code cluster-sizes.json} is published under (ADR-362 §9). */
    private static final Path SCHEMA = Path.of("docs", "report", "cluster-sizes.schema.json");

    /** The format of {@code cluster-sizes.json} once a column is inserted for the subtopic (ADR-416 §5). */
    private static final int FORMAT_THREE = 3;

    private static final ClusterSizeReport.Topic UNDIVIDED =
            ClusterSizeReport.Topic.of("seeds/a.txt", 0, List.of(2, 2), Optional.empty());

    private static final ClusterSizeReport.Topic DIVIDED = ClusterSizeReport.Topic.divided(
            "seeds/b.txt",
            4,
            List.of(ClusterSizeReport.Subtopic.of(1, List.of(2, 1), Optional.empty()),
                    ClusterSizeReport.Subtopic.of(0, List.of(1), Optional.empty())),
            Optional.empty());

    private static final List<ClusterSizeReport.Topic> TWO_TOPICS = List.of(UNDIVIDED, DIVIDED);

    /** A cell of a row with no link, as the page shows it. */
    private static final String NO_LINK = "<td class=\"count\">&mdash;</td>";

    /** The sixth paragraph under How to read this, whole. */
    private static final String SUBTOPICS = "A crowded topic is divided into subtopics before its documents are"
            + " grouped. Its row counts every group across its subtopics, and its links are those of the documents of"
            + " the whole topic; directly under it, each subtopic has a row of its own, numbered as the division"
            + " numbered it, counted from 0, which counts that subtopic's groups alone, its links being those of the"
            + " documents its groups were found among. A topic that is not divided has its own row alone.";

    /** The seventh, whole, its first words in {@code <em>}. */
    private static final String THE_SHARE = "<em>Share of documents</em> is how many of the documents grouped on this"
            + " page lie in the topic, or in the subtopic. No share is too large: a topic holding most of the documents"
            + " takes the grouping longer, and nothing else, and its share is here so that you can see where the"
            + " documents went, not as a warning.";

    /** The fifth, whole, which the two follow. */
    private static final String ONE_DOCUMENT = "A topic with one document has no link at all, shown as"
            + " <code>&mdash;</code>: there is no pair for a number to describe.";

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("A divided topic's row is followed by a row for each of its subtopics, each with its own numbers and its share of the documents")
    void aDividedTopicsRowIsFollowedByARowForEachSubtopic() {
        String page = ClusterSizeReport.render(TWO_TOPICS);

        claim("the table is headed by the topic, the subtopic, the documents and their share, then the sizes and links",
                () -> assertThat(page).contains("<tr><th>Topic</th><th>Subtopic</th><th>Documents</th>"
                        + "<th>Share of documents</th><th>Groups</th><th>Largest group</th><th>Middle group</th>"
                        + "<th>Groups of one</th><th>Weakest link</th><th>Middle link</th><th>Strongest link</th></tr>\n"));
        claim("the undivided topic has its row alone, its subtopic cell empty, holding 4 of the " + DOCUMENTS
                        + " documents, a half, in 2 groups; the divided topic's row counts every group across its"
                        + " subtopics, 4 documents in 3 groups, 2 of them of one; then subtopic 1's row, 3 documents,"
                        + " 37.5%, in 2 groups, and subtopic 0's, 1 document, 12.5%, in 1, each named by its topic's key",
                () -> assertThat(page).contains(
                        "<tr><td>seeds/a.txt chunk 0</td><td class=\"count\"></td><td class=\"count\">4</td>"
                                + "<td class=\"count\">50.0%</td><td class=\"count\">2</td><td class=\"count\">2</td>"
                                + "<td class=\"count\">2</td><td class=\"count\">0</td>"
                                + NO_LINK + NO_LINK + NO_LINK + "</tr>\n"
                                + "<tr><td>seeds/b.txt chunk 4</td><td class=\"count\"></td><td class=\"count\">4</td>"
                                + "<td class=\"count\">50.0%</td><td class=\"count\">3</td><td class=\"count\">2</td>"
                                + "<td class=\"count\">1</td><td class=\"count\">2</td>"
                                + NO_LINK + NO_LINK + NO_LINK + "</tr>\n"
                                + "<tr><td>seeds/b.txt chunk 4</td><td class=\"count\">1</td><td class=\"count\">3</td>"
                                + "<td class=\"count\">37.5%</td><td class=\"count\">2</td><td class=\"count\">2</td>"
                                + "<td class=\"count\">2</td><td class=\"count\">1</td>"
                                + NO_LINK + NO_LINK + NO_LINK + "</tr>\n"
                                + "<tr><td>seeds/b.txt chunk 4</td><td class=\"count\">0</td><td class=\"count\">1</td>"
                                + "<td class=\"count\">12.5%</td><td class=\"count\">1</td><td class=\"count\">1</td>"
                                + "<td class=\"count\">1</td><td class=\"count\">1</td>"
                                + NO_LINK + NO_LINK + NO_LINK + "</tr>\n"));
    }

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("Each row of the group sizes page shows the links of its own documents: the topic's of the whole topic, a subtopic's of its own")
    void eachRowShowsTheSpreadOfItsOwnGraph() {
        ClusterSizeReport.Topic divided = ClusterSizeReport.Topic.divided(
                "seeds/b.txt",
                4,
                List.of(ClusterSizeReport.Subtopic.of(1, List.of(2, 1), Optional.of(new RetainedEdgeSpread(0.7, 0.8, 0.9, 2))),
                        ClusterSizeReport.Subtopic.of(0, List.of(1), Optional.empty())),
                Optional.of(new RetainedEdgeSpread(0.4, 0.5, 0.6, 3)));

        String page = ClusterSizeReport.render(List.of(divided));

        claim("the topic's row shows the links of its whole graph, 0.40 to 0.60; subtopic 1's those of its own, 0.70 to"
                        + " 0.90; and subtopic 0, of one document, no link",
                () -> assertThat(page).contains(
                        "<td class=\"count\">0.40</td><td class=\"count\">0.50</td><td class=\"count\">0.60</td></tr>\n"
                                + "<tr><td>seeds/b.txt chunk 4</td><td class=\"count\">1</td>",
                        "<td class=\"count\">0.70</td><td class=\"count\">0.80</td><td class=\"count\">0.90</td></tr>\n"
                                + "<tr><td>seeds/b.txt chunk 4</td><td class=\"count\">0</td>",
                        NO_LINK + NO_LINK + NO_LINK + "</tr>\n</table>\n"));
    }

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("The group sizes page says what a subtopic's row and the share of documents are, and that no share is a warning")
    void theHowToReadParagraphsSayWhatASubtopicsRowAndTheShareAre() {
        String page = ClusterSizeReport.render(TWO_TOPICS);

        claim("after the paragraph on a topic of one document, one says what a divided topic's row and its subtopics'"
                        + " rows count, and the next what the share is, and that no share is too large",
                () -> assertThat(page).contains(
                        "<p>" + ONE_DOCUMENT + "</p>\n<p>" + SUBTOPICS + "</p>\n<p>" + THE_SHARE + "</p>\n"));
    }

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("The group sizes JSON holds a row for each subtopic, is of format 3, and follows its published schema")
    void theJsonIsOfFormatThreeAndFollowsItsSchema(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("cluster-sizes.json");
        AnalysisReportJson.write(file, ClusterSizeReport.document(TWO_TOPICS, WRITTEN_AT));
        JsonNode json = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        JsonNode schema = JSON.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        JsonNode topics = json.get("sections").get(0);

        claim("its first part's columns are the page's: the topic, the subtopic as a count, the documents, their share,"
                        + " the four sizes and the three links",
                () -> assertThat(topics.get("table").get("columns"))
                        .extracting(column -> column.get("id").asString() + " " + column.get("label").asString() + " "
                                + column.get("kind").asString())
                        .containsExactly(
                                "topic Topic text",
                                "subtopic Subtopic count",
                                "documents Documents count",
                                "share-of-documents Share of documents share",
                                "groups Groups count",
                                "largest-group Largest group count",
                                "middle-group Middle group count",
                                "groups-of-one Groups of one count",
                                "weakest-link Weakest link number",
                                "middle-link Middle link number",
                                "strongest-link Strongest link number"));
        claim("its rows are the page's, in its order: a topic's subtopic null, a subtopic's its number, each share a"
                        + " fraction of the " + DOCUMENTS + " documents to three places, and no link null",
                () -> assertThat(AnalysisReportRows.of(topics)).containsExactly(
                        Arrays.asList("seeds/a.txt chunk 0", null, 4.0, 0.5, 2.0, 2.0, 2.0, 0.0, null, null, null),
                        Arrays.asList("seeds/b.txt chunk 4", null, 4.0, 0.5, 3.0, 2.0, 1.0, 2.0, null, null, null),
                        Arrays.asList("seeds/b.txt chunk 4", 1.0, 3.0, 0.375, 2.0, 2.0, 2.0, 1.0, null, null, null),
                        Arrays.asList("seeds/b.txt chunk 4", 0.0, 1.0, 0.125, 1.0, 1.0, 1.0, 1.0, null, null, null)));
        claim("its headline counts the topics' rows alone, so no document or group is counted twice: 2 topics, "
                        + DOCUMENTS + " documents, 5 groups, 2 of one",
                () -> assertThat(json.get("headline"))
                        .extracting(figure -> figure.get("key").asString() + " " + figure.get("value").asLong())
                        .containsExactly("topics 2", "documents 8", "groups 5", "groups-of-one 2"));
        claim("its how to read part holds the two paragraphs after the fifth, as the words a reader reads",
                () -> assertThat(textOf(json.get("sections").get(1))).endsWith(
                        SUBTOPICS, THE_SHARE.replace("<em>", "").replace("</em>", "")));
        claim("it is of format 3, the format its published schema holds",
                () -> assertThat(List.of(json.get("format").asLong(), (long) ClusterSizeReport.FORMAT))
                        .containsExactly(schema.get("properties").get("format").get("const").asLong(), (long) FORMAT_THREE));
        claim("the schema uses only the eight keywords a report's schema may, and describes every position",
                () -> assertThat(SchemaWalker.keywordProblems(schema)).isEmpty());
        claim("and the JSON follows it: every field it requires, none it does not name, each of its type and among the"
                        + " values it allows",
                () -> assertThat(SchemaWalker.problems(json, schema)).isEmpty());
    }

    private static List<String> textOf(JsonNode section) {
        List<String> text = new ArrayList<>();
        section.get("text").forEach(paragraph -> text.add(paragraph.asString()));
        return text;
    }
}
