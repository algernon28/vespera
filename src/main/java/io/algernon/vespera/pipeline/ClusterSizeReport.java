package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.RetainedEdgeSpread;
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
 * link to each other, so the only way to know what shape a page tree will have is to look. A
 * partition that is 40% one-document pages is a fact whoever builds that tree needs <em>before</em>
 * they build it, and this is where it belongs rather than in a threshold that quietly merges them.
 *
 * <p><b>It reports and never repairs.</b> A partition that broke into nothing but clusters of one is
 * a real outcome — it says the seed collected documents that resemble it individually and not each
 * other — and merging those into a catch-all would be two unmeasured thresholds and a page no
 * document belongs to.
 */
final class ClusterSizeReport {

    private ClusterSizeReport() {}

    /**
     * One seed partition's clusters.
     *
     * @param seedPath the seed whose partition this is, named as a reader would recognise it
     * @param sizes how many documents each of its clusters holds, in ordinal order
     * @param spread how alike the documents on the kept links were, or empty where the partition
     *     holds one document and so has no link to measure (ADR-096)
     */
    record Partition(String seedPath, List<Integer> sizes, Optional<RetainedEdgeSpread> spread) {

        int documentCount() {
            return sizes.stream().mapToInt(Integer::intValue).sum();
        }

        int clusterCount() {
            return sizes.size();
        }

        int largest() {
            return sizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        /**
         * The middle cluster by size, which says more about the spread than a mean does: one cluster
         * holding most of a partition drags a mean up and leaves the reader thinking the pages are
         * evenly sized.
         */
        int median() {
            if (sizes.isEmpty()) {
                return 0;
            }
            List<Integer> ascending = new ArrayList<>(sizes);
            ascending.sort(Integer::compareTo);
            return ascending.get(ascending.size() / 2);
        }

        /** How many clusters hold exactly one document — the count that decides whether a tree is worth building. */
        int singletons() {
            return (int) sizes.stream().filter(size -> size == 1).count();
        }
    }

    static String render(List<Partition> partitions) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "How the documents grouped under each exemplar"))
                .append(ReportPage.paragraph("Each document was matched to one exemplar, and the"
                        + " documents matched to the same exemplar were then grouped among themselves"
                        + " by how alike they are. Nobody said how many groups to look for: the groups"
                        + " are whatever the documents turned out to form, and this page is what they"
                        + " turned out to be. Nothing here was removed, merged or renamed."));

        if (partitions.isEmpty()) {
            return ReportPage.render(
                    "How the documents grouped under each exemplar",
                    body.append(ReportPage.paragraph(
                                    "No document was matched to an exemplar, so there was nothing"
                                            + " to group."))
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
                .append(ReportPage.paragraph("<em>Groups of one</em> is the number to look at first. A"
                        + " document alone in its group is a real answer rather than a mistake: it"
                        + " means the exemplar collected that document on its own merits and not"
                        + " because it belongs with the others. An exemplar whose documents are nearly"
                        + " all alone is telling you something about the exemplar, and no amount of"
                        + " regrouping would change it."))
                .append(ReportPage.paragraph("The <em>middle group</em> sits halfway up the sizes,"
                        + " which says more than an average would: one group holding most of an"
                        + " exemplar's documents pulls an average upwards and leaves the impression of"
                        + " evenly-sized groups that are not there."))
                .append(ReportPage.paragraph("The three <em>link</em> columns are how alike the"
                        + " documents actually were, where 1 is a pair saying the same thing and 0 is a"
                        + " pair with nothing in common. A link is one pair of documents the grouping"
                        + " treated as belonging together, and every document is linked to its nearest"
                        + " few <em>whether or not they are close</em> — nothing here requires a pair"
                        + " to be alike before grouping them."))
                .append(ReportPage.paragraph("<b>That is why the strongest link is worth reading"
                        + " first.</b> If even it is low, the documents in that exemplar's groups were"
                        + " put together for want of anything better and not because they resemble one"
                        + " another, and the groups will read as lists of unrelated documents under one"
                        + " heading. Where the weakest link is already high, the documents genuinely"
                        + " belong together and the groups are describing the archive rather than the"
                        + " arithmetic. No number here is a cut: nothing was removed or merged on"
                        + " account of a low link, and what a low one is worth doing about is for"
                        + " whoever has read the documents."))
                .append(ReportPage.paragraph("An exemplar with one document has no link at all, shown"
                        + " as <code>&mdash;</code>: there is no pair for a number to describe."))
                .append(ReportPage.heading(2, "What this page cannot see"))
                .append(ReportPage.paragraph("Whether a group reads as one coherent subject to a"
                        + " person. The link columns narrow this: a group of forty documents that"
                        + " resemble nothing no longer looks the same as a group of forty that resemble"
                        + " each other, which is all the sizes alone could say. What resemblance cannot"
                        + " say is what the documents are about — forty documents alike enough to be"
                        + " linked can still be forty things nobody would file under one heading, and"
                        + " only reading them settles that."));

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
