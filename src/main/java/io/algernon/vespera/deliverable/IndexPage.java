package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.FilenameStem;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code index.html}, the mechanical listing at the root of every tree (ADR-103, ADR-112, ADR-122, ADR-274 §1.4), and
 * the names it and the tree beneath it give their partitions and cluster pages.
 *
 * <p>It is appended to as its rows come (ADR-223 section 6): {@link #open} writes what produced the tree,
 * then {@link #partitionHeading} opens one partition's table and {@link #clusterRow} appends each of its rows,
 * as the cluster's page is written from the same synthesis doc (ADR-241), {@link #partitionEnd} closes the table, and
 * {@link #close} the page. Nothing here holds the index whole.
 *
 * <p><b>The order is rendered, never re-derived.</b> The partitions and the clusters arrive already in the
 * order the operator approved (ADR-112), and neither level here is sorted again — a partition's position
 * comes from its own {@code partitionOrder}, and a cluster's from its own {@code clusterOrder}.
 *
 * <p><b>It links to a page only where writing exists</b> (ADR-111), though the page itself is written
 * either way, so a cluster keeps its slot in the listing.
 */
final class IndexPage {

    /** The most characters of a slug a name carries, so the longest file of the tree is bounded (ADR-274 §1.2). */
    static final int SLUG_LENGTH = 40;

    /** The index's paragraph on documents.csv and the copies, in English; the Italian is {@link FixedWords}'s (ADR-321 §4). */
    static final String THE_INDEXS_PARAGRAPH = "The path column of documents.csv names each document beneath the archive"
            + " root recorded above. Each group's folder holds its page and a copy of every document listed on it,"
            + " checked against the digest taken when it was converted, and each link on a group's page leads to its"
            + " copy; the archive root names where they were copied from. The seed_partition column is not a path"
            + " beneath that root: it names a seed beneath the seed folder, which is a root of its own. The copy and"
            + " copy_outcome columns say which documents were copied and, of any that were not, why.";

    private IndexPage() {}

    /** One partition's heading and the head of its table of clusters, appended to {@code index}. */
    static void partitionHeading(Appendable index, ListedPartition partition, DeliverableLanguage language)
            throws IOException {
        index.append("<h2>")
                .append(Html.escape(partition.seedPath()))
                .append("</h2>\n<table>\n")
                .append(FixedWords.clusterTableHeadRow(language));
    }

    /** Closes the table {@link #partitionHeading} opened. */
    static void partitionEnd(Appendable index) throws IOException {
        index.append("</table>\n");
    }

    /**
     * One cluster's row of its partition's table, appended to {@code index} after {@link #partitionHeading} and
     * the rows of the clusters before it, in stored order.
     *
     * @param partitionCount how many partitions the tree has, which pads the partition's directory name
     * @param doc the writing over the cluster, empty where there is none
     */
    static void clusterRow(
            Appendable index,
            ListedPartition partition,
            int partitionCount,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            DeliverableLanguage language)
            throws IOException {
        int documentCount = recorded.cluster().documentCount();
        String label = Html.escape(recorded.label().value());
        if (doc.isEmpty()) {
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
                .append(Html.escape(partitionDirectoryName(partition, partitionCount)))
                .append('/')
                .append(Html.escape(clusterFolderName(recorded, widthOf(partition.clusterCount()))))
                .append('/')
                .append(Deliverable.CLUSTER_PAGE_NAME)
                .append("\">")
                .append(label)
                .append("</a></td><td>")
                .append(String.valueOf(documentCount))
                .append("</td><td>")
                .append(Html.escape(doc.get().title()))
                .append("</td></tr>\n");
    }

    /**
     * The name of a partition's directory: its position zero-padded to the width of {@code partitionCount},
     * then its seed's filename stem as a slug (ADR-112).
     */
    static String partitionDirectoryName(ListedPartition partition, int partitionCount) {
        return pad(partition.partitionOrder(), widthOf(partitionCount))
                + "-" + slug(FilenameStem.of(partition.seedPath()));
    }

    /**
     * The name of a cluster's folder within its partition's directory, which holds its page and its copies: its
     * position zero-padded to {@code clusterWidth} digits, then its label as a slug (ADR-112, ADR-280 §0).
     */
    static String clusterFolderName(RecordedCluster recorded, int clusterWidth) {
        return pad(recorded.cluster().clusterOrder(), clusterWidth) + "-" + slug(recorded.label().value());
    }

    /** How many digits it takes to write {@code count}, which is how wide every position in it is padded. */
    static int widthOf(int count) {
        return String.valueOf(count).length();
    }

    /** What produced the tree, and what its path columns resolve against: everything before the first partition. */
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
    static String pad(int ordinal, int width) {
        return String.format("%0" + width + "d", ordinal);
    }

    /**
     * A filename-safe rendering of {@code text}: lower-cased, every run of characters that is not a
     * letter or digit collapsed to one hyphen, cut to its first {@value #SLUG_LENGTH} characters, and no
     * leading or trailing hyphen, the cut's included (ADR-112, ADR-274 §1.2).
     */
    static String slug(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String hyphenated = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "");
        String cut = hyphenated.length() > SLUG_LENGTH ? hyphenated.substring(0, SLUG_LENGTH) : hyphenated;
        return cut.replaceAll("-+$", "");
    }
}
