package io.algernon.vespera.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders how each seed partition broke into clusters as one self-contained HTML file (ADR-087) —
 * plain, hand-assembled HTML, no templating library, the shape {@link FormatMixReport} and {@link
 * SeedCorpusComparisonReport} already established (ADR-046).
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
     */
    record Partition(String seedPath, List<Integer> sizes) {

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
        StringBuilder page = new StringBuilder();
        page.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<title>How the documents grouped under each exemplar</title>\n<style>\n")
                .append("body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n")
                .append("table { border-collapse: collapse; margin-bottom: 1.5em; }\n")
                .append("td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n")
                .append("td.count { text-align: right; }\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>How the documents grouped under each exemplar</h1>\n")
                .append("<p>Each document was matched to one exemplar, and the documents matched to the"
                        + " same exemplar were then grouped among themselves by how alike they are."
                        + " Nobody said how many groups to look for: the groups are whatever the"
                        + " documents turned out to form, and this page is what they turned out to"
                        + " be. Nothing here was removed, merged or renamed.</p>\n");

        if (partitions.isEmpty()) {
            page.append("<p>No document was matched to an exemplar, so there was nothing to group.</p>\n")
                    .append("</body>\n</html>\n");
            return page.toString();
        }

        page.append("<table>\n<tr><th>Exemplar</th><th>Documents</th><th>Groups</th><th>Largest group</th>")
                .append("<th>Middle group</th><th>Groups of one</th></tr>\n");
        for (Partition partition : partitions) {
            page.append("<tr><td>")
                    .append(escape(partition.seedPath()))
                    .append("</td><td class=\"count\">")
                    .append(partition.documentCount())
                    .append("</td><td class=\"count\">")
                    .append(partition.clusterCount())
                    .append("</td><td class=\"count\">")
                    .append(partition.largest())
                    .append("</td><td class=\"count\">")
                    .append(partition.median())
                    .append("</td><td class=\"count\">")
                    .append(partition.singletons())
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");

        page.append("<h2>How to read this</h2>\n")
                .append("<p><em>Groups of one</em> is the number to look at first. A document alone in its"
                        + " group is a real answer rather than a mistake: it means the exemplar collected"
                        + " that document on its own merits and not because it belongs with the others."
                        + " An exemplar whose documents are nearly all alone is telling you something"
                        + " about the exemplar, and no amount of regrouping would change it.</p>\n")
                .append("<p>The <em>middle group</em> sits halfway up the sizes, which says more than an"
                        + " average would: one group holding most of an exemplar's documents pulls an"
                        + " average upwards and leaves the impression of evenly-sized groups that are not"
                        + " there.</p>\n");

        page.append("<h2>What this page cannot see</h2>\n")
                .append("<p>Whether a group reads as one coherent subject to a person. These sizes are"
                        + " arithmetic over how documents resemble each other, and a group of forty that"
                        + " nobody would put under one heading looks exactly like a group of forty that"
                        + " everybody would.</p>\n");

        page.append("</body>\n</html>\n");
        return page.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
