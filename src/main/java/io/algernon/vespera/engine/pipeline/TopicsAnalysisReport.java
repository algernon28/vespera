package io.algernon.vespera.engine.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code topics} analysis report (ADR-416): what feeds each topic that stands, the topics no document was placed
 * under with each seed feeding them, and the seeds feeding no topic, as one self-contained page and its JSON, drawn
 * through {@link ReportPage} and {@link AnalysisReportJson} (ADR-130, ADR-362 section 9).
 *
 * <p>It shows counts, paths, keys and the names recorded, and no text of a document (ADR-264). It draws the topics in
 * the order it is given them, and holds no rule: which topic comes first, and how each count is read, are the
 * arrangement step's.
 */
final class TopicsAnalysisReport {

    /** This report's name: its page's without the ending, and its JSON's. */
    static final String NAME = "topics";

    /** The format of {@code topics.json}, raised by a change that removes, renames or retypes a field. */
    static final int FORMAT = 2;

    /**
     * The page's title and first heading, and its JSON's {@code title} (ADR-377 section 5), whether or not the topics
     * are listed: *Topics found in your seed documents* is untrue of a list (ADR-435 section 10).
     */
    static final String TITLE = "Topics";

    private static final String OPENING =
            "The topics are the subjects the passages of your seed documents fall into, found across every seed"
            + " document's passages rather than one for each file, so that one seed document can feed several topics"
            + " and several seed documents one. Every document that survived was placed under one of them. Each"
            + " topic's row gives its name, the seed document its first passage is in and that passage's number,"
            + " counted from 0, how many passages it holds and their share of all the seed passages, how many seed"
            + " documents feed it, how many documents were placed under it, and how many documents placed under"
            + " another topic are linked from it.";

    /**
     * The first paragraph where the profile lists the topics, until the low-similarity floor ships a value (ADR-435
     * section 10, section 14): the column and the last clause of the list that count the documents placed with low
     * similarity are written once the floor is.
     */
    private static final String OPENING_WITH_LISTED_TOPICS =
            "The topics are the ones your profile lists, in its order, under the names you gave them. Every document"
            + " that survived was placed under the one whose name its passages are nearest to on average. Each"
            + " topic's row gives its name, how many documents were placed under it, and how many documents placed"
            + " under another topic are linked from it.";

    private static final String LISTED_WITH_NO_DOCUMENT =
            "A topic your profile lists that no document was placed under is listed here. It still has its page in what"
            + " is written, with no group on it.";

    private static final String LISTED_LINKS =
            "<em>Linked documents</em> are documents placed under another topic that also fit this one: each is copied"
            + " once, under its own topic, and this topic's page carries a link to it rather than a second copy.";

    private static final String WITH_NO_DOCUMENT_HEADING = "Topics no document was placed under";

    private static final String WITH_NO_DOCUMENT =
            "A topic no document was placed under has no folder and no group in what is written. It is listed here"
            + " with each seed document feeding it and how many of the topic's passages that seed document gives, so"
            + " that you can see which part of your seed documents found nothing in the archive.";

    private static final String EVERY_TOPIC_HAS_ONE = "Every topic has at least one document placed under it.";

    private static final String FEEDING_NONE_HEADING = "Seed documents feeding no topic";

    private static final String FEEDING_NONE =
            "A seed document none of whose passages lies in a topic is listed here: its passages fell in topics too"
            + " narrow to keep. Documents are still scored for relevance against it, as against every seed document,"
            + " but no topic comes from it.";

    private static final String EVERY_SEED_FEEDS_ONE = "Every seed document feeds at least one topic.";

    private static final String THE_SHARE =
            "<em>Share of seed passages</em> is the topic's passages out of every passage of your seed documents, a"
            + " seed document whose text is the same as an earlier one's counted once, under the earlier. The shares"
            + " add up to less than the whole where a topic was too narrow to keep.";

    private static final String THE_LINKS =
            "<em>Linked documents</em> are documents placed under another topic that also fit this one: each is copied"
            + " once, under its own topic, and this topic's page carries a link to it rather than a second copy. A topic no"
            + " document was placed under has no page in what is written: its linked documents are counted here, and no"
            + " link to them is drawn.";

    /** The column of a topic's name: the row's own name, taken from the record, as no literal of this class names a table (ADR-041). */
    private static final String TOPIC_COLUMN = Topic.class.getSimpleName().toLowerCase(Locale.ROOT);

    private TopicsAnalysisReport() {}

    /**
     * A seed feeding a topic.
     *
     * @param path the seed's path in the seed folder
     * @param passages how many of the topic's passages are of it
     */
    record Seed(String path, int passages) {}

    /**
     * One topic that stands.
     *
     * @param name the name recorded under the arrangement run
     * @param seedPath the seed its first passage is in
     * @param chunkIndex that passage's number, counted from 0
     * @param seeds the seeds feeding it, in the order of ADR-399 section 4's paths
     * @param documentsPlaced the survivors placed under it that the clustering step grouped
     * @param linkedDocuments the surviving documents with a link naming it
     */
    record Topic(
            String name,
            String seedPath,
            int chunkIndex,
            List<Seed> seeds,
            int documentsPlaced,
            int linkedDocuments) {

        int passages() {
            return seeds.stream().mapToInt(Seed::passages).sum();
        }

        String key() {
            return seedPath + " chunk " + chunkIndex;
        }
    }

    /**
     * One topic the profile lists.
     *
     * @param name the name as listed, recorded under the arrangement run
     * @param documentsPlaced the survivors placed under it that the clustering step grouped
     * @param linkedDocuments the surviving documents with a link naming it
     */
    record ListedTopic(String name, int documentsPlaced, int linkedDocuments) {}

    private static String percentage(long passages, long seedPassages) {
        return String.format(Locale.ROOT, "%.1f%%", seedPassages == 0 ? 0.0 : 100.0 * passages / seedPassages);
    }

    private static Double fraction(long passages, long seedPassages) {
        return Double.valueOf(String.format(Locale.ROOT, "%.3f", seedPassages == 0 ? 0.0 : (double) passages / seedPassages));
    }

    private static List<Topic> withNoDocument(List<Topic> topics) {
        return topics.stream().filter(topic -> topic.documentsPlaced() == 0).toList();
    }

    /** A paragraph of the page as the words a reader reads: its markup taken off. */
    private static String plain(String html) {
        return html.replaceAll("<[^>]*>", "");
    }

    /** The page, the topics drawn in the order given. */
    static String render(long seedPassages, List<Topic> topics, List<String> seedsFeedingNoTopic) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, TITLE)).append(ReportPage.paragraph(OPENING));

        StringBuilder rows = new StringBuilder();
        for (Topic topic : topics) {
            rows.append(ReportPage.row(
                    ReportPage.textCell(topic.name()),
                    ReportPage.textCell(topic.key()),
                    ReportPage.numberCell(topic.passages()),
                    ReportPage.numberCell(percentage(topic.passages(), seedPassages)),
                    ReportPage.numberCell(topic.seeds().size()),
                    ReportPage.numberCell(topic.documentsPlaced()),
                    ReportPage.numberCell(topic.linkedDocuments())));
        }
        body.append(ReportPage.table(
                ReportPage.headerRow(
                        "Topic", "First passage", "Seed passages", "Share of seed passages", "Seed documents",
                        "Documents placed", "Linked documents"),
                rows.toString()));

        body.append(ReportPage.heading(2, WITH_NO_DOCUMENT_HEADING));
        List<Topic> noDocument = withNoDocument(topics);
        if (noDocument.isEmpty()) {
            body.append(ReportPage.paragraph(EVERY_TOPIC_HAS_ONE));
        } else {
            StringBuilder seedRows = new StringBuilder();
            for (Topic topic : noDocument) {
                for (Seed seed : topic.seeds()) {
                    seedRows.append(ReportPage.row(
                            ReportPage.textCell(topic.name()),
                            ReportPage.textCell(topic.key()),
                            ReportPage.textCell(seed.path()),
                            ReportPage.numberCell(seed.passages())));
                }
            }
            body.append(ReportPage.paragraph(WITH_NO_DOCUMENT))
                    .append(ReportPage.table(
                            ReportPage.headerRow("Topic", "First passage", "Seed document", "Passages"),
                            seedRows.toString()));
        }

        body.append(ReportPage.heading(2, FEEDING_NONE_HEADING));
        if (seedsFeedingNoTopic.isEmpty()) {
            body.append(ReportPage.paragraph(EVERY_SEED_FEEDS_ONE));
        } else {
            StringBuilder seedRows = new StringBuilder();
            for (String path : seedsFeedingNoTopic) {
                seedRows.append(ReportPage.row(ReportPage.textCell(path)));
            }
            body.append(ReportPage.paragraph(FEEDING_NONE))
                    .append(ReportPage.table(ReportPage.headerRow("Seed document"), seedRows.toString()));
        }

        body.append(ReportPage.heading(2, "How to read this"))
                .append(ReportPage.paragraph(THE_SHARE))
                .append(ReportPage.paragraph(THE_LINKS));
        return ReportPage.render(TITLE, body.toString());
    }

    /** What the page shows, as {@code topics.json} holds it (ADR-362 section 9). */
    static AnalysisReportJson.Document document(
            long seedPassages, List<Topic> topics, List<String> seedsFeedingNoTopic, Instant writtenAt) {
        List<Topic> noDocument = withNoDocument(topics);
        List<AnalysisReportJson.Figure> headline = List.of(
                new AnalysisReportJson.Figure("topics", "Topics", (long) topics.size(), "count"),
                new AnalysisReportJson.Figure("seed-passages", "Seed passages", seedPassages, "count"),
                new AnalysisReportJson.Figure(
                        "topics-with-no-document", "Topics with no document", (long) noDocument.size(), "count"),
                new AnalysisReportJson.Figure(
                        "seeds-feeding-no-topic", "Seed documents feeding no topic",
                        (long) seedsFeedingNoTopic.size(), "count"));

        List<List<Object>> topicRows = new ArrayList<>();
        for (Topic topic : topics) {
            topicRows.add(new ArrayList<>(List.<Object>of(
                    topic.name(),
                    topic.key(),
                    (long) topic.passages(),
                    fraction(topic.passages(), seedPassages),
                    (long) topic.seeds().size(),
                    (long) topic.documentsPlaced(),
                    (long) topic.linkedDocuments())));
        }
        List<AnalysisReportJson.Section> sections = new ArrayList<>();
        sections.add(new AnalysisReportJson.Section(
                "topics",
                TITLE,
                List.of(plain(OPENING)),
                null,
                new AnalysisReportJson.Table(
                        List.of(
                                new AnalysisReportJson.Column(TOPIC_COLUMN, "Topic", "text"),
                                new AnalysisReportJson.Column("first-passage", "First passage", "text"),
                                new AnalysisReportJson.Column("seed-passages", "Seed passages", "count"),
                                new AnalysisReportJson.Column(
                                        "share-of-seed-passages", "Share of seed passages", "share"),
                                new AnalysisReportJson.Column("seed-documents", "Seed documents", "count"),
                                new AnalysisReportJson.Column("documents-placed", "Documents placed", "count"),
                                new AnalysisReportJson.Column("linked-documents", "Linked documents", "count")),
                        topicRows)));

        if (noDocument.isEmpty()) {
            sections.add(new AnalysisReportJson.Section(
                    "topics-with-no-document", WITH_NO_DOCUMENT_HEADING, List.of(EVERY_TOPIC_HAS_ONE), null, null));
        } else {
            List<List<Object>> seedRows = new ArrayList<>();
            for (Topic topic : noDocument) {
                for (Seed seed : topic.seeds()) {
                    seedRows.add(new ArrayList<>(List.<Object>of(
                            topic.name(), topic.key(), seed.path(), (long) seed.passages())));
                }
            }
            sections.add(new AnalysisReportJson.Section(
                    "topics-with-no-document",
                    WITH_NO_DOCUMENT_HEADING,
                    List.of(WITH_NO_DOCUMENT),
                    null,
                    new AnalysisReportJson.Table(
                            List.of(
                                    new AnalysisReportJson.Column(TOPIC_COLUMN, "Topic", "text"),
                                    new AnalysisReportJson.Column("first-passage", "First passage", "text"),
                                    new AnalysisReportJson.Column("seed-document", "Seed document", "path"),
                                    new AnalysisReportJson.Column("passages", "Passages", "count")),
                            seedRows)));
        }

        if (seedsFeedingNoTopic.isEmpty()) {
            sections.add(new AnalysisReportJson.Section(
                    "seeds-feeding-no-topic", FEEDING_NONE_HEADING, List.of(EVERY_SEED_FEEDS_ONE), null, null));
        } else {
            List<List<Object>> pathRows = new ArrayList<>();
            for (String path : seedsFeedingNoTopic) {
                pathRows.add(new ArrayList<>(List.<Object>of(path)));
            }
            sections.add(new AnalysisReportJson.Section(
                    "seeds-feeding-no-topic",
                    FEEDING_NONE_HEADING,
                    List.of(FEEDING_NONE),
                    null,
                    new AnalysisReportJson.Table(
                            List.of(new AnalysisReportJson.Column("seed-document", "Seed document", "path")),
                            pathRows)));
        }

        sections.add(new AnalysisReportJson.Section(
                "how-to-read", "How to read this", List.of(plain(THE_SHARE), plain(THE_LINKS)), null, null));
        return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline, sections);
    }

    /** The page where the profile lists the topics, one row each in the list's order (ADR-435 section 10). */
    static String renderListed(List<ListedTopic> topics) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, TITLE)).append(ReportPage.paragraph(OPENING_WITH_LISTED_TOPICS));

        StringBuilder rows = new StringBuilder();
        for (ListedTopic topic : topics) {
            rows.append(ReportPage.row(
                    ReportPage.textCell(topic.name()),
                    ReportPage.numberCell(topic.documentsPlaced()),
                    ReportPage.numberCell(topic.linkedDocuments())));
        }
        body.append(ReportPage.table(
                ReportPage.headerRow("Topic", "Documents placed", "Linked documents"), rows.toString()));

        body.append(ReportPage.heading(2, WITH_NO_DOCUMENT_HEADING));
        List<ListedTopic> noDocument = listedWithNoDocument(topics);
        if (noDocument.isEmpty()) {
            body.append(ReportPage.paragraph(EVERY_TOPIC_HAS_ONE));
        } else {
            StringBuilder noDocumentRows = new StringBuilder();
            for (ListedTopic topic : noDocument) {
                noDocumentRows.append(ReportPage.row(ReportPage.textCell(topic.name())));
            }
            body.append(ReportPage.paragraph(LISTED_WITH_NO_DOCUMENT))
                    .append(ReportPage.table(ReportPage.headerRow("Topic"), noDocumentRows.toString()));
        }

        body.append(ReportPage.heading(2, "How to read this")).append(ReportPage.paragraph(LISTED_LINKS));
        return ReportPage.render(TITLE, body.toString());
    }

    /** What {@link #renderListed} shows, as {@code topics.json} holds it (ADR-362 section 9, ADR-435 section 10). */
    static AnalysisReportJson.Document documentListed(List<ListedTopic> topics, Instant writtenAt) {
        List<ListedTopic> noDocument = listedWithNoDocument(topics);
        List<AnalysisReportJson.Figure> headline = List.of(
                new AnalysisReportJson.Figure("topics", "Topics", (long) topics.size(), "count"),
                new AnalysisReportJson.Figure(
                        "topics-with-no-document", "Topics with no document", (long) noDocument.size(), "count"));

        List<List<Object>> topicRows = new ArrayList<>();
        for (ListedTopic topic : topics) {
            topicRows.add(new ArrayList<>(List.<Object>of(
                    topic.name(), (long) topic.documentsPlaced(), (long) topic.linkedDocuments())));
        }
        List<AnalysisReportJson.Section> sections = new ArrayList<>();
        sections.add(new AnalysisReportJson.Section(
                "topics",
                TITLE,
                List.of(plain(OPENING_WITH_LISTED_TOPICS)),
                null,
                new AnalysisReportJson.Table(
                        List.of(
                                new AnalysisReportJson.Column(TOPIC_COLUMN, "Topic", "text"),
                                new AnalysisReportJson.Column("documents-placed", "Documents placed", "count"),
                                new AnalysisReportJson.Column("linked-documents", "Linked documents", "count")),
                        topicRows)));
        if (noDocument.isEmpty()) {
            sections.add(new AnalysisReportJson.Section(
                    "topics-with-no-document", WITH_NO_DOCUMENT_HEADING, List.of(EVERY_TOPIC_HAS_ONE), null, null));
        } else {
            List<List<Object>> noDocumentRows = new ArrayList<>();
            for (ListedTopic topic : noDocument) {
                noDocumentRows.add(new ArrayList<>(List.<Object>of(topic.name())));
            }
            sections.add(new AnalysisReportJson.Section(
                    "topics-with-no-document",
                    WITH_NO_DOCUMENT_HEADING,
                    List.of(LISTED_WITH_NO_DOCUMENT),
                    null,
                    new AnalysisReportJson.Table(
                            List.of(new AnalysisReportJson.Column(TOPIC_COLUMN, "Topic", "text")), noDocumentRows)));
        }
        sections.add(new AnalysisReportJson.Section(
                "how-to-read", "How to read this", List.of(plain(LISTED_LINKS)), null, null));
        return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline, sections);
    }

    private static List<ListedTopic> listedWithNoDocument(List<ListedTopic> topics) {
        return topics.stream().filter(topic -> topic.documentsPlaced() == 0).toList();
    }
}
