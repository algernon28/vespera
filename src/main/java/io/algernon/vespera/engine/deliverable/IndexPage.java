package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code index.html}, the mechanical listing at the root of every tree (ADR-103, ADR-112, ADR-122, ADR-274 §1.4), and
 * the names it and the tree beneath it give their topics and cluster pages.
 *
 * <p>It is appended to as its rows come (ADR-223 section 6): {@link #open} writes what produced the tree,
 * then {@link #topicHeading} opens one topic's heading and, where it is not divided, its table, {@link
 * #subtopicHeading} a divided topic's subtopic's, and {@link #clusterRow} appends each row, as the cluster's page is
 * written from the same synthesis doc (ADR-241), {@link #tableEnd} closes a table, and {@link #close} the page. Nothing here holds the index whole.
 *
 * <p><b>The order is rendered, never re-derived.</b> The topics and the clusters arrive already in the
 * order the operator approved (ADR-112), and neither level here is sorted again — a topic's position
 * comes from its own {@code topicOrder}, and a cluster's from its own {@code clusterOrder}.
 *
 * <p><b>It links to a page only where writing exists</b> (ADR-111), though the page itself is written
 * either way, so a cluster keeps its slot in the listing.
 */
public final class IndexPage {

    /** The most characters of a slug a name carries, so the longest file of the tree is bounded (ADR-274 §1.2). */
    static final int SLUG_LENGTH = 40;

    /** The index's paragraph on documents.csv and the copies, in English; the Italian is {@link FixedWords}'s (ADR-321 §4). */
    static final String THE_INDEXS_PARAGRAPH = "The path column of documents.csv names each document beneath the archive"
            + " root recorded above. Each group's folder holds its page and a copy of every document listed on it,"
            + " checked against the digest taken when it was converted, and each link on a group's page leads to its"
            + " copy; the archive root names where they were copied from. The topic column is not a path: it holds the"
            + " name of the topic each document is placed under, and the subtopic column, where that topic is divided,"
            + " the name of the subtopic the document lies in. Each numbered folder beside this page is a topic's: it"
            + " holds the topic's page and the folders of its groups, or, where the topic is divided, a folder for each"
            + " subtopic holding the subtopic's page and the folders of its groups. The copy and copy_outcome columns"
            + " say which documents were copied and, of any that were not, why.";

    private IndexPage() {}

    /**
     * One topic's heading, its recorded name linking its page, appended to {@code index}, and, where the topic is not
     * divided, the head of its table of clusters (ADR-415 section 4).
     */
    static void topicHeading(Appendable index, TopicLayout layout, DeliverableLanguage language) throws IOException {
        index.append("<h2><a href=\"")
                .append(Html.escape(layout.topicFolder() + "/" + Deliverable.CLUSTER_PAGE_NAME))
                .append("\">")
                .append(Html.escape(layout.topic().name()))
                .append("</a></h2>\n");
        if (!layout.divided()) {
            index.append("<table>\n").append(FixedWords.clusterTableHeadRow(language));
        }
    }

    /** A divided topic's subtopic's heading, its name linking its page, and the head of its table of clusters. */
    static void subtopicHeading(
            Appendable index, TopicLayout layout, ListedSubtopic subtopic, DeliverableLanguage language)
            throws IOException {
        index.append("<h3><a href=\"")
                .append(Html.escape(layout.topicFolder() + "/" + layout.subtopicFolder(subtopic) + "/"
                        + Deliverable.CLUSTER_PAGE_NAME))
                .append("\">")
                .append(Html.escape(subtopic.name()))
                .append("</a></h3>\n<table>\n")
                .append(FixedWords.clusterTableHeadRow(language));
    }

    /** Closes the table {@link #topicHeading} or {@link #subtopicHeading} opened. */
    static void tableEnd(Appendable index) throws IOException {
        index.append("</table>\n");
    }

    /**
     * One cluster's row of its table, appended to {@code index} after the heading that opened it and the rows of the
     * clusters before it, in stored order.
     *
     * @param title what the writing over the cluster is titled, empty where there is none
     */
    static void clusterRow(
            Appendable index,
            TopicLayout layout,
            RecordedCluster recorded,
            Optional<String> title,
            DeliverableLanguage language)
            throws IOException {
        int documentCount = recorded.cluster().documentCount();
        String label = Html.escape(recorded.label().value());
        if (title.isEmpty()) {
            index.append("<tr><td>")
                    .append(label)
                    .append("</td><td>")
                    .append(String.valueOf(documentCount))
                    .append("</td><td><em>")
                    .append(Html.escape(FixedWords.nothingWasWrittenOverIt(language)))
                    .append("</em></td></tr>\n");
            return;
        }
        index.append("<tr><td><a href=\"")
                .append(Html.escape(layout.clusterFolder(recorded)))
                .append('/')
                .append(Deliverable.CLUSTER_PAGE_NAME)
                .append("\">")
                .append(label)
                .append("</a></td><td>")
                .append(String.valueOf(documentCount))
                .append("</td><td>")
                .append(Html.escape(title.get()))
                .append("</td></tr>\n");
    }

    /**
     * The name of a topic's directory: its position zero-padded to the width of {@code topicCount}, then its recorded
     * name as a slug, as a cluster's folder is named from its label (ADR-112, ADR-409 section 10, ADR-415 section 1).
     */
    public static String topicDirectoryName(ListedTopic topic, int topicCount) {
        return pad(topic.topicOrder(), widthOf(topicCount)) + "-" + slug(topic.name());
    }

    /**
     * The name of a subtopic's directory within its topic's: its order zero-padded to the width of {@code subtopicCount},
     * the count of its topic's subtopics, then its recorded name as a slug (ADR-415 section 1).
     */
    public static String subtopicDirectoryName(ListedSubtopic subtopic, int subtopicCount) {
        return pad(subtopic.subtopicOrder(), widthOf(subtopicCount)) + "-" + slug(subtopic.name());
    }

    /**
     * The name of a cluster's folder within its topic's folder, which holds its page and its copies: its
     * position zero-padded to {@code clusterWidth} digits, then its label as a slug (ADR-112, ADR-280 §0).
     */
    public static String clusterFolderName(RecordedCluster recorded, int clusterWidth) {
        return pad(recorded.cluster().clusterOrder(), clusterWidth) + "-" + slug(recorded.label().value());
    }

    /** How many digits it takes to write {@code count}, which is how wide every position in it is padded. */
    public static int widthOf(int count) {
        return String.valueOf(count).length();
    }

    /** What produced the tree, and what its path columns resolve against: everything before the first topic. */
    static void open(Appendable index, DeliverableProvenance provenance, DeliverableLanguage language)
            throws IOException {
        index.append(Html.head(FixedWords.theDeliverable(language) + provenance.runId(), language))
                .append("<h1>")
                .append(FixedWords.theDeliverable(language))
                .append(Html.escape(provenance.runId()))
                .append("</h1>\n<ul>\n<li>")
                .append(FixedWords.run(language))
                .append(Html.escape(provenance.runId()))
                .append("</li>\n<li>")
                .append(FixedWords.walk(language))
                .append(String.valueOf(provenance.walk()))
                .append("</li>\n<li>")
                .append(FixedWords.archiveRoot(language))
                .append(Html.escape(provenance.corpusRoot()))
                .append("</li>\n");
        for (NamedValue value : provenance.values()) {
            index.append("<li>")
                    .append(Html.escape(value.key()))
                    .append(": ")
                    .append(Html.escape(value.value()))
                    .append("</li>\n");
        }
        index.append("</ul>\n<p>").append(FixedWords.indexParagraph(language)).append("</p>\n");
    }

    /**
     * The heading, the one sentence and the head of the table of code sets (ADR-296 §4), appended after the last
     * group's table. Never says <em>group</em> of a code set (ADR-122).
     */
    static void codeHeading(Appendable index, DeliverableLanguage language) throws IOException {
        index.append("<h2>")
                .append(FixedWords.code(language))
                .append("</h2>\n<p>")
                .append(FixedWords.codeSentence(language))
                .append("</p>\n<table>\n<tr>");
        for (String head : FixedWords.codeTableHeads(language)) {
            index.append("<th>").append(head).append("</th>");
        }
        index.append("</tr>\n");
    }

    /**
     * One code set's row: its drawn folder name linking {@code code/<name>/}, its folder in the archive, and how many of
     * its files' copies stand and how many do not.
     */
    static void codeRow(
            Appendable index, String drawnName, String folder, long copied, long notCopied, DeliverableLanguage language)
            throws IOException {
        index.append("<tr><td><a href=\"")
                .append(Html.escape(CodeCopies.DIRECTORY_NAME + "/" + ClusterPage.percentEncoded(drawnName) + "/"))
                .append("\">")
                .append(Html.escape(drawnName))
                .append("</a></td><td>")
                .append(Html.escape(folder.isEmpty() ? FixedWords.archivesTop(language) : folder))
                .append("</td><td>")
                .append(String.valueOf(copied))
                .append("</td><td>")
                .append(String.valueOf(notCopied))
                .append("</td></tr>\n");
    }

    /** Closes the table {@link #codeHeading} opened. */
    static void codeEnd(Appendable index) throws IOException {
        index.append("</table>\n");
    }

    /** Everything of the page after its last table. */
    static void close(Appendable index) throws IOException {
        index.append(Html.tail());
    }

    /** {@code ordinal}, zero-padded to {@code width} digits (ADR-112). */
    public static String pad(int ordinal, int width) {
        return String.format("%0" + width + "d", ordinal);
    }

    /**
     * A filename-safe rendering of {@code text}: its accented letters folded to their base letter (decomposed, its
     * combining marks removed), lower-cased, every run of characters that is not {@code a} to {@code z} or {@code 0} to
     * {@code 9} collapsed to one hyphen, cut to its first {@value #SLUG_LENGTH} characters, and no leading or trailing
     * hyphen, the cut's included (ADR-112, ADR-274 §1.2, ADR-415 §1).
     */
    static String slug(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String lower = folded.toLowerCase(Locale.ROOT);
        String hyphenated = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "");
        String cut = hyphenated.length() > SLUG_LENGTH ? hyphenated.substring(0, SLUG_LENGTH) : hyphenated;
        return cut.replaceAll("-+$", "");
    }
}
