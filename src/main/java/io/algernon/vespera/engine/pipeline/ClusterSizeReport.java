package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.RetainedEdgeSpread;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Renders how each seed partition broke into clusters as one self-contained HTML file (ADR-087) —
 * plain, hand-assembled HTML, no templating library, the shared {@link ReportPage} module the
 * reports beside the database all supply their title, prose and rows to (ADR-046, ADR-130).
 *
 * <p>The page exists because the number of clusters is not chosen: it falls out of how the documents
 * link to each other, so the only way to know what shape the deliverable will have is to look. A
 * partition that is 40% one-document pages is a fact whoever builds that tree needs <em>before</em>
 * they build it, and this is where it belongs rather than in a threshold that quietly merges them.
 *
 * <p><b>It reports and never repairs.</b> A partition that broke into nothing but clusters of one is
 * a real outcome — it says the seed collected documents that resemble it individually and not each
 * other — and merging those into a catch-all would be two unmeasured thresholds and a page no
 * document belongs to.
 */
final class ClusterSizeReport {

    /** This report's name: its page's without the ending, and its JSON's (ADR-362 section 9). */
    static final String NAME = "cluster-sizes";

    /** The format of {@code cluster-sizes.json}, raised by a change that removes, renames or retypes a field. */
    static final int FORMAT = 1;

    private static final String TITLE = "How the documents grouped under each exemplar";

    private static final String INTRO =
            "Each document was matched to one exemplar, and the"
            + " documents matched to the same exemplar were then grouped among themselves"
            + " by how alike they are. Nobody said how many groups to look for: the groups"
            + " are whatever the documents turned out to form, and this page is what they"
            + " turned out to be. Nothing here was removed, merged or renamed.";

    private static final String NONE =
            "No document was matched to an exemplar, so there was nothing"
            + " to group.";

    private static final String GROUPS_OF_ONE =
            "<em>Groups of one</em> is the number to look at first. A"
            + " document alone in its group is a real answer rather than a mistake: it"
            + " means the exemplar collected that document on its own merits and not"
            + " because it belongs with the others. An exemplar whose documents are nearly"
            + " all alone is telling you something about the exemplar, and no amount of"
            + " regrouping would change it.";

    private static final String MIDDLE =
            "The <em>middle group</em> sits halfway up the sizes,"
            + " which says more than an average would: one group holding most of an"
            + " exemplar's documents pulls an average upwards and leaves the impression of"
            + " evenly-sized groups that are not there.";

    private static final String LINKS =
            "The three <em>link</em> columns are how alike the"
            + " documents actually were, where 1 is a pair saying the same thing and 0 is a"
            + " pair with nothing in common. A link is one pair of documents the grouping"
            + " treated as belonging together, and every document is linked to its nearest"
            + " few <em>whether or not they are close</em> — nothing here requires a pair"
            + " to be alike before grouping them.";

    private static final String STRONGEST =
            "<b>That is why the strongest link is worth reading"
            + " first.</b> If even it is low, the documents in that exemplar's groups were"
            + " put together for want of anything better and not because they resemble one"
            + " another, and the groups will read as lists of unrelated documents under one"
            + " heading. Where the weakest link is already high, the documents genuinely"
            + " belong together and the groups are describing the archive rather than the"
            + " arithmetic. No number here is a cut: nothing was removed or merged on"
            + " account of a low link, and what a low one is worth doing about is for"
            + " whoever has read the documents.";

    private static final String ONE_DOCUMENT =
            "An exemplar with one document has no link at all, shown"
            + " as <code>&mdash;</code>: there is no pair for a number to describe.";

    private static final String CANNOT_SEE =
            "Whether a group reads as one coherent subject to a"
            + " person. The link columns narrow this: a group of forty documents that"
            + " resemble nothing no longer looks the same as a group of forty that resemble"
            + " each other, which is all the sizes alone could say. What resemblance cannot"
            + " say is what the documents are about — forty documents alike enough to be"
            + " linked can still be forty things nobody would file under one heading, and"
            + " only reading them settles that.";

    private ClusterSizeReport() {}

    /**
     * One seed partition's clusters, as the five numbers the page shows of them (ADR-223 section 2): the page
     * keeps a row for each partition and not the size of each cluster.
     *
     * @param seedPath the seed whose partition this is, named as a reader would recognise it
     * @param documentCount how many documents its clusters hold between them
     * @param clusterCount how many clusters it holds
     * @param largest how many documents its largest cluster holds
     * @param median how many documents its middle cluster by size holds
     * @param singletons how many clusters hold exactly one document — the count that decides whether a tree
     *     is worth building
     * @param spread how alike the documents on the kept links were, or empty where the partition
     *     holds one document and so has no link to measure (ADR-096)
     */
    record Partition(
            String seedPath,
            int documentCount,
            int clusterCount,
            int largest,
            int median,
            int singletons,
            Optional<RetainedEdgeSpread> spread) {

        /**
         * The row for a partition whose clusters hold {@code sizes}, in ordinal order. The middle cluster by
         * size says more about the spread than a mean does: one cluster holding most of a partition drags a
         * mean up and leaves the reader thinking the pages are evenly sized; of two middle sizes it is the
         * upper.
         */
        static Partition of(String seedPath, List<Integer> sizes, Optional<RetainedEdgeSpread> spread) {
            List<Integer> ascending = new ArrayList<>(sizes);
            ascending.sort(Integer::compareTo);
            return new Partition(
                    seedPath,
                    sizes.stream().mapToInt(Integer::intValue).sum(),
                    sizes.size(),
                    sizes.stream().mapToInt(Integer::intValue).max().orElse(0),
                    ascending.isEmpty() ? 0 : ascending.get(ascending.size() / 2),
                    (int) sizes.stream().filter(size -> size == 1).count(),
                    spread);
        }
    }

    /**
     * What the page shows, as {@code cluster-sizes.json} holds it (ADR-362 section 9): the same partitions, the same
     * numbers, in the same order, and the paragraphs the page reads, stripped of their markup. A link the page shows
     * as a dash is {@code null}, and one it shows to two places is those two places.
     */
    static AnalysisReportJson.Document document(List<Partition> partitions, Instant writtenAt) {
        List<String> intro = new ArrayList<>(List.of(plain(INTRO)));
        List<AnalysisReportJson.Section> sections = new ArrayList<>();
        if (partitions.isEmpty()) {
            intro.add(plain(NONE));
            sections.add(new AnalysisReportJson.Section("partitions", TITLE, intro, null, null));
            return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline(partitions), sections);
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Partition partition : partitions) {
            List<Object> row = new ArrayList<>();
            row.add(partition.seedPath());
            row.add((long) partition.documentCount());
            row.add((long) partition.clusterCount());
            row.add((long) partition.largest());
            row.add((long) partition.median());
            row.add((long) partition.singletons());
            row.add(shown(partition.spread().map(RetainedEdgeSpread::lowest).orElse(null)));
            row.add(shown(partition.spread().map(RetainedEdgeSpread::middle).orElse(null)));
            row.add(shown(partition.spread().map(RetainedEdgeSpread::highest).orElse(null)));
            rows.add(row);
        }
        List<AnalysisReportJson.Column> columns = List.of(
                new AnalysisReportJson.Column("exemplar", "Exemplar", "path"),
                new AnalysisReportJson.Column("documents", "Documents", "count"),
                new AnalysisReportJson.Column("groups", "Groups", "count"),
                new AnalysisReportJson.Column("largest-group", "Largest group", "count"),
                new AnalysisReportJson.Column("middle-group", "Middle group", "count"),
                new AnalysisReportJson.Column("groups-of-one", "Groups of one", "count"),
                new AnalysisReportJson.Column("weakest-link", "Weakest link", "number"),
                new AnalysisReportJson.Column("middle-link", "Middle link", "number"),
                new AnalysisReportJson.Column("strongest-link", "Strongest link", "number"));
        sections.add(new AnalysisReportJson.Section(
                "partitions", TITLE, intro, null, new AnalysisReportJson.Table(columns, rows)));
        sections.add(new AnalysisReportJson.Section(
                "how-to-read",
                "How to read this",
                List.of(plain(GROUPS_OF_ONE), plain(MIDDLE), plain(LINKS), plain(STRONGEST), plain(ONE_DOCUMENT)),
                null,
                null));
        sections.add(new AnalysisReportJson.Section(
                "what-this-page-cannot-see", "What this page cannot see", List.of(plain(CANNOT_SEE)), null, null));
        return new AnalysisReportJson.Document(FORMAT, NAME, TITLE, writtenAt, headline(partitions), sections);
    }

    /** The numbers the page states first: what the exemplars' partitions came to between them. */
    private static List<AnalysisReportJson.Figure> headline(List<Partition> partitions) {
        return List.of(
                new AnalysisReportJson.Figure("partitions", "Exemplars grouped", (long) partitions.size(), "count"),
                new AnalysisReportJson.Figure(
                        "documents", "Documents grouped",
                        partitions.stream().mapToLong(Partition::documentCount).sum(), "count"),
                new AnalysisReportJson.Figure(
                        "groups", "Groups",
                        partitions.stream().mapToLong(Partition::clusterCount).sum(), "count"),
                new AnalysisReportJson.Figure(
                        "groups-of-one", "Groups of one",
                        partitions.stream().mapToLong(Partition::singletons).sum(), "count"));
    }

    /** A resemblance as the page shows it, to two places, or {@code null} where it shows a dash. */
    private static Double shown(Double value) {
        return value == null ? null : Double.valueOf(String.format(Locale.ROOT, "%.2f", value));
    }

    /** A paragraph of the page as the words a reader reads: its markup taken off, its dash as a dash. */
    private static String plain(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&mdash;", "\u2014");
    }

    static String render(List<Partition> partitions) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "How the documents grouped under each exemplar"))
                .append(ReportPage.paragraph(INTRO));

        if (partitions.isEmpty()) {
            return ReportPage.render(
                    "How the documents grouped under each exemplar",
                    body.append(ReportPage.paragraph(NONE))
                            .toString());
        }

        String headers = ReportPage.headerRow(
                "Exemplar", "Documents", "Groups", "Largest group", "Middle group", "Groups of one",
                "Weakest link", "Middle link", "Strongest link");
        StringBuilder rows = new StringBuilder();
        for (Partition partition : partitions) {
            rows.append(ReportPage.row(
                    ReportPage.textCell(partition.seedPath()),
                    ReportPage.numberCell(partition.documentCount()),
                    ReportPage.numberCell(partition.clusterCount()),
                    ReportPage.numberCell(partition.largest()),
                    ReportPage.numberCell(partition.median()),
                    ReportPage.numberCell(partition.singletons()),
                    ReportPage.numberCell(resemblance(
                            partition.spread().map(RetainedEdgeSpread::lowest).orElse(null))),
                    ReportPage.numberCell(resemblance(
                            partition.spread().map(RetainedEdgeSpread::middle).orElse(null))),
                    ReportPage.numberCell(resemblance(
                            partition.spread().map(RetainedEdgeSpread::highest).orElse(null)))));
        }
        body.append(ReportPage.table(headers, rows.toString()));

        body.append(ReportPage.heading(2, "How to read this"))
                .append(ReportPage.paragraph(GROUPS_OF_ONE))
                .append(ReportPage.paragraph(MIDDLE))
                .append(ReportPage.paragraph(LINKS))
                .append(ReportPage.paragraph(STRONGEST))
                .append(ReportPage.paragraph(ONE_DOCUMENT))
                .append(ReportPage.heading(2, "What this page cannot see"))
                .append(ReportPage.paragraph(CANNOT_SEE));

        return ReportPage.render("How the documents grouped under each exemplar", body.toString());
    }

    /**
     * One resemblance, to two places, or an em dash where there is none.
     *
     * <p>Two places because a third says nothing a reader would act on, and an em dash rather than
     * 0.00 because a partition of one has no pair to be alike: a zero there would read as a document
     * resembling nothing, which is a different statement about the archive.
     *
     * @param value the resemblance, or {@code null} where the partition had no pair to measure
     */
    private static String resemblance(Double value) {
        return value == null ? "&mdash;" : String.format(Locale.ROOT, "%.2f", value);
    }
}
