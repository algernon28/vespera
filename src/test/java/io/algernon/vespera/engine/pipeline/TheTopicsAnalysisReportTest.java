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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code topics} analysis report shows what feeds each topic, the topics no document was placed under with each
 * seed feeding them, and the seeds feeding no topic, as counts, paths, keys and names (ADR-416 §2, §3).
 *
 * <p>Drawn by {@link TopicsAnalysisReport} alone. The fixture has {@value #SEED_PASSAGES} seed passages: two topics of
 * three passages each, a third of them, and three passages of the two seeds feeding no topic. The topic on sluice gates
 * is given first, holding {@value #ONE} document and no link, two seeds feeding it; the one on pumps second, holding no
 * document and {@value #ONE} link, two seeds feeding it, its key's chunk {@value #PUMPS_CHUNK} so that a key written
 * with another chunk is told apart. The report draws the topics in the order it is given them.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("834")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class TheTopicsAnalysisReportTest {

    /** Every seed passage, the denominator of each share: the two topics' six and the three that feed none. */
    private static final long SEED_PASSAGES = 9;

    /** The one document, and the one link, of the fixture. */
    private static final int ONE = 1;

    /** The chunk the topic on pumps begins with. */
    private static final int PUMPS_CHUNK = 2;

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-18T09:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The schema {@code topics.json} is published under (ADR-362 §9). */
    private static final Path SCHEMA = Path.of("docs", "report", "topics.schema.json");

    private static final TopicsAnalysisReport.Topic SLUICE_GATES = new TopicsAnalysisReport.Topic(
            "Sluice gates",
            "seeds/sluices-1.txt",
            0,
            List.of(new TopicsAnalysisReport.Seed("seeds/sluices-1.txt", 2),
                    new TopicsAnalysisReport.Seed("seeds/sluices-2.txt", 1)),
            ONE,
            0);

    private static final TopicsAnalysisReport.Topic PUMPS = new TopicsAnalysisReport.Topic(
            "Pumps",
            "seeds/pumps-1.txt",
            PUMPS_CHUNK,
            List.of(new TopicsAnalysisReport.Seed("seeds/pumps-1.txt", 2),
                    new TopicsAnalysisReport.Seed("seeds/pumps-2.txt", 1)),
            0,
            ONE);

    private static final List<TopicsAnalysisReport.Topic> TWO_TOPICS = List.of(SLUICE_GATES, PUMPS);

    private static final List<String> TWO_SEEDS_FEEDING_NONE = List.of("seeds/other-a.txt", "seeds/other-b.txt");

    /** The page's first paragraph, whole. */
    private static final String THE_OPENING = "The topics are the subjects the passages of your seed documents fall"
            + " into, found across every seed document's passages rather than one for each file, so that one seed"
            + " document can feed several topics and several seed documents one. Every document that survived was"
            + " placed under one of them. Each topic's row gives its name, the seed document its first passage is in"
            + " and that passage's number, counted from 0, how many passages it holds and their share of all the seed"
            + " passages, how many seed documents feed it, how many documents were placed under it, and how many"
            + " documents placed under another topic are linked from it.";

    /** The paragraph over the topics no document was placed under, whole. */
    private static final String WITH_NO_DOCUMENT = "A topic no document was placed under has no folder and no group"
            + " in what is written. It is listed here with each seed document feeding it and how many of the topic's"
            + " passages that seed document gives, so that you can see which part of your seed documents found nothing"
            + " in the archive.";

    /** What stands in its place where every topic has a document. */
    private static final String EVERY_TOPIC_HAS_ONE = "Every topic has at least one document placed under it.";

    /** The paragraph over the seeds feeding no topic, whole. */
    private static final String FEEDING_NONE = "A seed document none of whose passages lies in a topic is listed here:"
            + " its passages fell in topics too narrow to keep. Documents are still scored for relevance against it,"
            + " as against every seed document, but no topic comes from it.";

    /** What stands in its place where every seed feeds a topic. */
    private static final String EVERY_SEED_FEEDS_ONE = "Every seed document feeds at least one topic.";

    /** The first paragraph under How to read this, whole, its first words in {@code <em>}. */
    private static final String THE_SHARE = "<em>Share of seed passages</em> is the topic's passages out of every"
            + " passage of your seed documents, a seed document whose text is the same as an earlier one's counted"
            + " once, under the earlier. The shares add up to less than the whole where a topic was too narrow to keep.";

    /** The second, whole. */
    private static final String THE_LINKS = "<em>Linked documents</em> are documents placed under another topic that"
            + " also fit this one: each is copied once, under its own topic, and this topic carries a link to it rather"
            + " than a second copy.";

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("The topics page gives each topic's name, first passage, passages and their share, seeds, documents and links, in the order given")
    void thePageListsEachTopicsCountsInTheOrderGiven() {
        String page = TopicsAnalysisReport.render(SEED_PASSAGES, TWO_TOPICS, TWO_SEEDS_FEEDING_NONE);

        claim("the page is titled Topics found in your seed documents, and opens with the paragraph saying what a"
                        + " topic is and what each row gives",
                () -> assertThat(page).contains(
                        "<h1>Topics found in your seed documents</h1>\n<p>" + THE_OPENING + "</p>\n"));
        claim("its table is headed by the seven columns, then holds the topic on sluice gates and the one on pumps, in"
                        + " the order given: each with its name, its first passage as the seed's path and the chunk,"
                        + " its 3 passages, a third of the " + SEED_PASSAGES + " seed passages shown to one place, its"
                        + " 2 seeds, its documents and its links",
                () -> assertThat(page).contains("<tr><th>Topic</th><th>First passage</th><th>Seed passages</th>"
                        + "<th>Share of seed passages</th><th>Seed documents</th><th>Documents placed</th>"
                        + "<th>Linked documents</th></tr>\n"
                        + "<tr><td>Sluice gates</td><td>seeds/sluices-1.txt chunk 0</td><td class=\"count\">3</td>"
                        + "<td class=\"count\">33.3%</td><td class=\"count\">2</td><td class=\"count\">1</td>"
                        + "<td class=\"count\">0</td></tr>\n"
                        + "<tr><td>Pumps</td><td>seeds/pumps-1.txt chunk 2</td><td class=\"count\">3</td>"
                        + "<td class=\"count\">33.3%</td><td class=\"count\">2</td><td class=\"count\">0</td>"
                        + "<td class=\"count\">1</td></tr>\n"));
    }

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("A topic no document was placed under is listed with each seed document feeding it and its passages")
    void aTopicWithNoDocumentIsListedWithEachOfItsSeeds() {
        String page = TopicsAnalysisReport.render(SEED_PASSAGES, TWO_TOPICS, TWO_SEEDS_FEEDING_NONE);

        claim("under its heading, the paragraph says such a topic has no folder and why it is listed, then a table gives"
                        + " the topic on pumps once for each of its two seeds, in the order given, with the passages"
                        + " each gives it; the topic on sluice gates, which holds a document, is not among them",
                () -> assertThat(page).contains("<h2>Topics no document was placed under</h2>\n<p>" + WITH_NO_DOCUMENT
                        + "</p>\n<table>\n<tr><th>Topic</th><th>First passage</th><th>Seed document</th>"
                        + "<th>Passages</th></tr>\n"
                        + "<tr><td>Pumps</td><td>seeds/pumps-1.txt chunk 2</td><td>seeds/pumps-1.txt</td>"
                        + "<td class=\"count\">2</td></tr>\n"
                        + "<tr><td>Pumps</td><td>seeds/pumps-1.txt chunk 2</td><td>seeds/pumps-2.txt</td>"
                        + "<td class=\"count\">1</td></tr>\n</table>\n"));
    }

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("The seed documents feeding no topic are listed by their paths, in the order given")
    void theSeedsFeedingNoTopicAreListed() {
        String page = TopicsAnalysisReport.render(SEED_PASSAGES, TWO_TOPICS, TWO_SEEDS_FEEDING_NONE);

        claim("under its heading, the paragraph says why a seed document feeds no topic, then a table lists the two by"
                        + " their paths, in the order given",
                () -> assertThat(page).contains("<h2>Seed documents feeding no topic</h2>\n<p>" + FEEDING_NONE
                        + "</p>\n<table>\n<tr><th>Seed document</th></tr>\n<tr><td>seeds/other-a.txt</td></tr>\n"
                        + "<tr><td>seeds/other-b.txt</td></tr>\n</table>\n"));
    }

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("Where every topic has a document and every seed document feeds a topic, each part says so and holds no table")
    void whereNothingIsMissingEachSectionSaysSo(@TempDir Path folder) throws IOException {
        List<TopicsAnalysisReport.Topic> oneTopic = List.of(SLUICE_GATES);
        String page = TopicsAnalysisReport.render(SEED_PASSAGES, oneTopic, List.of());
        JsonNode json = written(TopicsAnalysisReport.document(SEED_PASSAGES, oneTopic, List.of(), WRITTEN_AT), folder);

        claim("the page says, under each heading, that every topic has a document and that every seed document feeds a"
                        + " topic, with no table between them",
                () -> assertThat(page).contains("<h2>Topics no document was placed under</h2>\n<p>"
                        + EVERY_TOPIC_HAS_ONE + "</p>\n<h2>Seed documents feeding no topic</h2>\n<p>"
                        + EVERY_SEED_FEEDS_ONE + "</p>\n"));
        claim("and the JSON's two parts hold each its sentence and no table",
                () -> assertThat(List.of(sectionOf(json, "topics-with-no-document"), sectionOf(json, "seeds-feeding-no-topic")))
                        .extracting(section -> textOf(section) + " table " + section.get("table").isNull())
                        .containsExactly(
                                List.of(EVERY_TOPIC_HAS_ONE) + " table true",
                                List.of(EVERY_SEED_FEEDS_ONE) + " table true"));
    }

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("The topics page says what the share of seed passages and the linked documents are")
    void theHowToReadParagraphsSayWhatTheShareAndTheLinksAre() {
        String page = TopicsAnalysisReport.render(SEED_PASSAGES, TWO_TOPICS, TWO_SEEDS_FEEDING_NONE);

        claim("under How to read this, the share is said to be of every seed passage, a repeated seed counted once, and"
                        + " a linked document to be copied once under its own topic and linked from this one",
                () -> assertThat(page).contains("<h2>How to read this</h2>\n<p>" + THE_SHARE + "</p>\n<p>" + THE_LINKS
                        + "</p>\n"));
    }

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("The topics JSON holds what its page shows, in format 1, and follows its published schema")
    void theJsonHoldsWhatThePageShowsAndFollowsItsSchema(@TempDir Path folder) throws IOException {
        JsonNode json = written(
                TopicsAnalysisReport.document(SEED_PASSAGES, TWO_TOPICS, TWO_SEEDS_FEEDING_NONE, WRITTEN_AT), folder);
        JsonNode schema = JSON.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));

        claim("its name is topics and its title the page's",
                () -> assertThat(List.of(json.get("analysisReport").asString(), json.get("title").asString()))
                        .containsExactly("topics", "Topics found in your seed documents"));
        claim("its headline counts the 2 topics, the " + SEED_PASSAGES + " seed passages, the 1 topic with no document"
                        + " and the 2 seed documents feeding none",
                () -> assertThat(figuresOf(json)).containsExactly(
                        "topics Topics 2 count",
                        "seed-passages Seed passages 9 count",
                        "topics-with-no-document Topics with no document 1 count",
                        "seeds-feeding-no-topic Seed documents feeding no topic 2 count"));
        claim("its parts are the topics, the topics with no document, the seed documents feeding none and how to read"
                        + " them, in the page's order",
                () -> assertThat(json.get("sections")).extracting(section -> section.get("id").asString())
                        .containsExactly("topics", "topics-with-no-document", "seeds-feeding-no-topic", "how-to-read"));
        claim("the topics' part opens with the page's first paragraph, and its table's columns are the page's",
                () -> {
                    assertThat(textOf(sectionOf(json, "topics"))).containsExactly(THE_OPENING);
                    assertThat(columnsOf(sectionOf(json, "topics"))).containsExactly(
                            "topic Topic text",
                            "first-passage First passage text",
                            "seed-passages Seed passages count",
                            "share-of-seed-passages Share of seed passages share",
                            "seed-documents Seed documents count",
                            "documents-placed Documents placed count",
                            "linked-documents Linked documents count");
                });
        claim("its rows are the page's, in its order, each share a fraction of one to three places",
                () -> assertThat(rowsOf(sectionOf(json, "topics"))).containsExactly(
                        Arrays.asList("Sluice gates", "seeds/sluices-1.txt chunk 0", 3.0, 0.333, 2.0, 1.0, 0.0),
                        Arrays.asList("Pumps", "seeds/pumps-1.txt chunk 2", 3.0, 0.333, 2.0, 0.0, 1.0)));
        claim("the topics with no document hold the topic on pumps once for each of its seeds, a seed document being a"
                        + " path",
                () -> {
                    assertThat(textOf(sectionOf(json, "topics-with-no-document"))).containsExactly(WITH_NO_DOCUMENT);
                    assertThat(columnsOf(sectionOf(json, "topics-with-no-document"))).containsExactly(
                            "topic Topic text",
                            "first-passage First passage text",
                            "seed-document Seed document path",
                            "passages Passages count");
                    assertThat(rowsOf(sectionOf(json, "topics-with-no-document"))).containsExactly(
                            Arrays.asList("Pumps", "seeds/pumps-1.txt chunk 2", "seeds/pumps-1.txt", 2.0),
                            Arrays.asList("Pumps", "seeds/pumps-1.txt chunk 2", "seeds/pumps-2.txt", 1.0));
                });
        claim("the seed documents feeding none are listed by their paths",
                () -> {
                    assertThat(textOf(sectionOf(json, "seeds-feeding-no-topic"))).containsExactly(FEEDING_NONE);
                    assertThat(columnsOf(sectionOf(json, "seeds-feeding-no-topic")))
                            .containsExactly("seed-document Seed document path");
                    assertThat(rowsOf(sectionOf(json, "seeds-feeding-no-topic"))).containsExactly(
                            Arrays.asList((Object) "seeds/other-a.txt"), Arrays.asList((Object) "seeds/other-b.txt"));
                });
        claim("how to read them holds the two paragraphs as the words a reader reads, their markup taken off",
                () -> assertThat(textOf(sectionOf(json, "how-to-read"))).containsExactly(
                        THE_SHARE.replace("<em>", "").replace("</em>", ""),
                        THE_LINKS.replace("<em>", "").replace("</em>", "")));
        claim("it is of format 1, the format its published schema holds",
                () -> assertThat(List.of(json.get("format").asLong(), (long) TopicsAnalysisReport.FORMAT))
                        .containsExactly(schema.get("properties").get("format").get("const").asLong(), 1L));
        claim("the schema uses only the eight keywords a report's schema may, and describes every position",
                () -> assertThat(SchemaWalker.keywordProblems(schema)).isEmpty());
        claim("and the JSON follows it: every field it requires, none it does not name, each of its type and among the"
                        + " values it allows",
                () -> assertThat(SchemaWalker.problems(json, schema)).isEmpty());
    }

    /** {@code document} as {@link AnalysisReportJson} writes it into a file, read back. */
    private static JsonNode written(AnalysisReportJson.Document document, Path folder) throws IOException {
        Path file = folder.resolve("topics.json");
        AnalysisReportJson.write(file, document);
        return JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static JsonNode sectionOf(JsonNode json, String id) {
        for (JsonNode section : json.get("sections")) {
            if (section.get("id").asString().equals(id)) {
                return section;
            }
        }
        throw new AssertionError("no section " + id + " in " + json);
    }

    private static List<String> textOf(JsonNode section) {
        List<String> text = new ArrayList<>();
        section.get("text").forEach(paragraph -> text.add(paragraph.asString()));
        return text;
    }

    private static List<String> figuresOf(JsonNode json) {
        List<String> figures = new ArrayList<>();
        json.get("headline").forEach(figure -> figures.add(figure.get("key").asString() + " "
                + figure.get("label").asString() + " " + figure.get("value").asLong() + " "
                + figure.get("unit").asString()));
        return figures;
    }

    private static List<String> columnsOf(JsonNode section) {
        List<String> columns = new ArrayList<>();
        section.get("table").get("columns").forEach(column -> columns.add(column.get("id").asString() + " "
                + column.get("label").asString() + " " + column.get("kind").asString()));
        return columns;
    }

    private static List<List<Object>> rowsOf(JsonNode section) {
        return AnalysisReportRows.of(section);
    }
}
