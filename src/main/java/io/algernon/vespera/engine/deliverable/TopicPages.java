package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A topic's page and a subtopic's page in the HTML tree (ADR-415 section 2, section 3, section 5), each {@code
 * index.html} in its folder and written once its last cluster is drawn, so that what is held is one topic's rows.
 *
 * <p>A topic's page holds its name, its overview or the sentence for its absence, its table and its See also; a
 * subtopic's holds its name, the topic it is part of and its table of clusters. Every text of the writing is escaped
 * before any markup is added ({@link Prose}), and every fixed word is the language's ({@link FixedWords}).
 */
public final class TopicPages {

    /** One cluster's row of a table: the cluster, and what the writing over it is titled, empty where there is none. */
    public record Row(RecordedCluster recorded, Optional<String> title) {}

    private TopicPages() {}

    /** Writes {@code layout}'s topic's page to {@code page}: the content of {@link #topicContent} between the tree's head and tail. */
    static void writeTopicPage(
            Appendable page,
            TopicLayout layout,
            ListedOverview overview,
            List<Row> rows,
            DeliverableLanguage language,
            SeeAlsoFeed seeAlso)
            throws IOException {
        page.append(Html.head(layout.topic().name(), language));
        topicContent(page, layout, overview, rows, language, seeAlso);
        page.append(Html.tail());
    }

    /**
     * Writes what {@code layout}'s topic's page holds between its opening and its closing, so that every rendering that
     * draws the page draws the same content (ADR-415 section 8): its name as its {@code <h1>}, its overview or the
     * sentence for its absence, its table and its See also. Its links are relative to the topic's folder.
     *
     * @param overview what the topic's page carries before its table
     * @param rows a row for each of the topic's clusters, in its one sequence
     * @param seeAlso hands each page of the topic's see-also lines to the consumer it is given; the heading and the
     *     sentence are written before the first page, and none where it hands none
     */
    public static void topicContent(
            Appendable page,
            TopicLayout layout,
            ListedOverview overview,
            List<Row> rows,
            DeliverableLanguage language,
            SeeAlsoFeed seeAlso)
            throws IOException {
        String name = Html.escape(layout.topic().name());
        page.append("<h1>").append(name).append("</h1>\n");
        writeOverview(page, layout, overview, language);
        if (layout.divided()) {
            writeTableOfSubtopics(page, layout, rows, language);
        } else if (!rows.isEmpty()) {
            // A listed topic no document is placed under has no table (ADR-429 section 8).
            writeTableOfClusters(page, layout, rows, language, layout::ownFolder);
        }
        boolean[] heading = {false};
        try {
            seeAlso.eachPage(lines -> {
                try {
                    if (!heading[0]) {
                        heading[0] = true;
                        page.append("<h2>")
                                .append(Html.escape(FixedWords.seeAlso(language)))
                                .append("</h2>\n<p>")
                                .append(Html.escape(FixedWords.seeAlsoSentence(language)))
                                .append("</p>\n<ul>\n");
                    }
                    for (SeeAlsoLine line : lines) {
                        page.append("<li><a href=\"../")
                                .append(Html.escape(line.clusterFolder() + "/" + Deliverable.CLUSTER_PAGE_NAME))
                                .append("\">")
                                .append(Html.escape(line.path().value()))
                                .append("</a> (")
                                .append(Html.escape(line.topic().name()))
                                .append(")</li>\n");
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        if (heading[0]) {
            page.append("</ul>\n");
        }
    }

    /** Writes a divided topic's subtopic's page to {@code page}: {@link #subtopicContent} between the tree's head and tail. */
    static void writeSubtopicPage(
            Appendable page,
            TopicLayout layout,
            ListedSubtopic subtopic,
            List<Row> rows,
            DeliverableLanguage language)
            throws IOException {
        page.append(Html.head(subtopic.name(), language));
        subtopicContent(page, layout, subtopic, rows, language);
        page.append(Html.tail());
    }

    /**
     * Writes what a divided topic's subtopic's page holds between its opening and its closing (ADR-415 section 3): its
     * name, the line naming its topic and linking it, and its table of clusters, which links each through its own folder.
     */
    public static void subtopicContent(
            Appendable page,
            TopicLayout layout,
            ListedSubtopic subtopic,
            List<Row> rows,
            DeliverableLanguage language)
            throws IOException {
        page.append("<h1>")
                .append(Html.escape(subtopic.name()))
                .append("</h1>\n<p>")
                .append(Html.escape(FixedWords.inTheTopic(language)))
                .append(" <a href=\"../")
                .append(Deliverable.CLUSTER_PAGE_NAME)
                .append("\">")
                .append(Html.escape(layout.topic().name()))
                .append("</a>.</p>\n");
        writeTableOfClusters(page, layout, rows, language, layout::ownFolder);
    }

    /** What hands a topic page its see-also lines, a page at a time. */
    @FunctionalInterface
    public interface SeeAlsoFeed {

        void eachPage(Consumer<List<SeeAlsoLine>> page);
    }

    private static void writeOverview(
            Appendable page, TopicLayout layout, ListedOverview overview, DeliverableLanguage language)
            throws IOException {
        switch (overview) {
            case ListedOverview.Written written -> {
                page.append(Prose.emit(written.writing(), citationsLinking(layout))).append('\n');
                int clusters = layout.clusters().size();
                if (written.clustersSent() < clusters) {
                    page.append("<p><em>")
                            .append(Html.escape(TopicOverviewWording.disclosure(language)
                                    .formatted(written.clustersSent(), clusters)))
                            .append("</em></p>\n");
                }
            }
            case ListedOverview.Unwritten unwritten -> page.append("<p><em>")
                    .append(Html.escape(TopicOverviewWording.of(unwritten.why(), language)))
                    .append("</em></p>\n");
        }
    }

    /** An emitter writing each citation as a link to the first page of the cluster at that place, or as plain text. */
    private static Prose.Emitter citationsLinking(TopicLayout layout) {
        return new Prose.Emitter() {
            @Override
            public String lineBreak() {
                return "<br>";
            }

            @Override
            public String citation(String digits) {
                Optional<RecordedCluster> cited = digits.length() > ProseHtml.MOST_DIGITS_OF_AN_ENTRY
                        ? Optional.empty()
                        : layout.clusterAtPlace(Integer.parseInt(digits));
                return cited
                        .map(recorded -> "<a href=\"" + Html.escape(layout.clusterFolderInTopic(recorded) + "/"
                                + Deliverable.CLUSTER_PAGE_NAME) + "\">[" + digits + "]</a>")
                        .orElse("[" + digits + "]");
            }
        };
    }

    private static void writeTableOfClusters(
            Appendable page,
            TopicLayout layout,
            List<Row> rows,
            DeliverableLanguage language,
            java.util.function.Function<RecordedCluster, String> folderOf)
            throws IOException {
        page.append("<table>\n").append(FixedWords.topicTableHeadRow(language));
        for (Row row : rows) {
            RecordedCluster recorded = row.recorded();
            String label = Html.escape(recorded.label().value());
            page.append("<tr><td>").append(String.valueOf(recorded.cluster().clusterOrder())).append("</td><td>");
            if (row.title().isPresent()) {
                page.append("<a href=\"")
                        .append(Html.escape(folderOf.apply(recorded) + "/" + Deliverable.CLUSTER_PAGE_NAME))
                        .append("\">")
                        .append(label)
                        .append("</a>");
            } else {
                page.append(label);
            }
            page.append("</td><td>")
                    .append(String.valueOf(recorded.cluster().documentCount()))
                    .append("</td><td>")
                    .append(row.title().map(Html::escape).orElseGet(() ->
                            "<em>" + Html.escape(FixedWords.nothingWasWrittenOverIt(language)) + "</em>"))
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");
    }

    private static void writeTableOfSubtopics(
            Appendable page, TopicLayout layout, List<Row> rows, DeliverableLanguage language) throws IOException {
        page.append("<table>\n").append(FixedWords.subtopicTableHeadRow(language));
        for (ListedSubtopic subtopic : layout.subtopics()) {
            List<Row> inIt = new ArrayList<>();
            for (Row row : rows) {
                if (layout.subtopicOf(row.recorded()).filter(own -> own.equals(subtopic)).isPresent()) {
                    inIt.add(row);
                }
            }
            long documents = inIt.stream().mapToLong(row -> row.recorded().cluster().documentCount()).sum();
            page.append("<tr><td><a href=\"")
                    .append(Html.escape(layout.subtopicFolder(subtopic) + "/" + Deliverable.CLUSTER_PAGE_NAME))
                    .append("\">")
                    .append(Html.escape(subtopic.name()))
                    .append("</a></td><td>")
                    .append(String.valueOf(inIt.size()))
                    .append("</td><td>")
                    .append(String.valueOf(documents))
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");
    }
}
