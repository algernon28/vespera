package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * Renders the arrangement as one self-contained HTML page (ADR-107) — plain, hand-assembled HTML, no
 * templating library, the shape {@link ClusterSizeReport} and {@link FormatMixReport} already
 * established (ADR-046).
 *
 * <p><b>This page is a gate, not a report.</b> The other five beside the database inform a number the
 * operator then supplies; this one asks for a decision about the thing it is showing. So it carries
 * the arrangement's short name, and that name is what the operator copies: an approval has to be of
 * the arrangement they actually read, and a page that did not say which arrangement it was could only
 * ever be approved in general.
 *
 * <p><b>Nothing on it was written by a model.</b> Generation happens after this gate for exactly that
 * reason — the review is of a derivation, and a reviewer checking generated text would be reviewing
 * the thing the gate exists to authorise.
 *
 * <p>The order shown is the stored order, never re-sorted here. If this page and the deliverable
 * sorted independently, the arrangement approved and the arrangement received would be two
 * arrangements sharing one name (ADR-112).
 */
final class ArrangementReport {

    private ArrangementReport() {}

    /**
     * One group of documents as the page shows it.
     *
     * @param label what the group is called, derived from its own highest-scoring document
     * @param documentCount how many documents it holds
     * @param leadDocument the document the name was taken from, so a reviewer can open it and disagree
     */
    record Group(String label, int documentCount, String leadDocument) {}

    /**
     * One exemplar's documents.
     *
     * @param seedPath the exemplar whose documents these are, named as a reader would recognise it
     * @param groups its groups, in the order the arrangement gives them
     */
    record Partition(String seedPath, List<Group> groups) {

        int documentCount() {
            return groups.stream().mapToInt(Group::documentCount).sum();
        }
    }

    static String render(String approvalName, String corpusRoot, List<Partition> partitions) {
        StringBuilder page = new StringBuilder();
        page.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<title>How the documents are arranged</title>\n<style>\n")
                .append("body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n")
                .append("table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }\n")
                .append("td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n")
                .append("td.count { text-align: right; }\n")
                .append("code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: #eee; }\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>How the documents are arranged</h1>\n")
                .append("<p>Every document that survived is here, under the exemplar it was matched to"
                        + " and in the group it formed with the documents nearest it. Nothing has been"
                        + " removed, merged or renamed to produce this page, and nothing on it was"
                        + " written for you — every name below was taken from a document you can open.</p>\n")
                .append("<p>The documents themselves are still in <code>")
                .append(escape(corpusRoot))
                .append("</code>. They have not been moved or copied.</p>\n");

        page.append("<h2>What you are being asked</h2>\n")
                .append("<p><b>Is this arrangement worth writing over?</b> The next step reads each group"
                        + " in full and writes a document about it, which is the slowest and most"
                        + " expensive thing this tool does. It will not start until you say this"
                        + " arrangement is the one you want.</p>\n")
                .append("<p>To say so, put this into <code>profile.yaml</code> under"
                        + " <code>arrangementApproved</code>:</p>\n")
                .append("<p><code class=\"name\">")
                .append(escape(approvalName))
                .append("</code></p>\n")
                .append("<p>That name is this arrangement and no other. If the documents are grouped"
                        + " again — because you changed a setting, or added to the archive — this page"
                        + " gets a new name and you are asked again. That is deliberate: an approval"
                        + " that never expired would mean shipping an arrangement nobody looked at.</p>\n")
                .append("<p>Write down what you actually checked in <code>provenance</code> beside it."
                        + " Nothing verifies it; it is there so that whoever reads this archive later"
                        + " knows what the approval was worth.</p>\n");

        if (partitions.isEmpty()) {
            page.append("<p>No document was matched to an exemplar, so there is nothing to arrange.</p>\n")
                    .append("</body>\n</html>\n");
            return page.toString();
        }

        for (Partition partition : partitions) {
            page.append("<h2>")
                    .append(escape(partition.seedPath()))
                    .append("</h2>\n<p>")
                    .append(partition.documentCount())
                    .append(" document(s) in ")
                    .append(partition.groups().size())
                    .append(" group(s).</p>\n")
                    .append("<table>\n<tr><th>Group</th><th>Documents</th><th>Named after</th></tr>\n");
            for (Group group : partition.groups()) {
                page.append("<tr><td>")
                        .append(escape(group.label()))
                        .append("</td><td class=\"count\">")
                        .append(group.documentCount())
                        .append("</td><td>")
                        .append(escape(group.leadDocument()))
                        .append("</td></tr>\n");
            }
            page.append("</table>\n");
        }

        page.append("<h2>How to read this</h2>\n")
                .append("<p>Exemplars are in order of how much of the archive sits under them, largest"
                        + " first, so the substantial part of what you have is at the top. Within each"
                        + " one, groups holding several documents come before groups holding one, and"
                        + " within those two tiers the groups closest to the exemplar come first.</p>\n")
                .append("<p><em>Named after</em> is the document each group's name was taken from — the"
                        + " one closest to the exemplar. Open a few. If a name does not describe the"
                        + " group it heads, that is what this page is for, and grouping the documents"
                        + " again with different settings is cheap compared with what comes next.</p>\n")
                .append("<p>A group holding one document is a real outcome rather than a mistake: it"
                        + " means that document was collected on its own merits and not because it"
                        + " belongs with the others.</p>\n")
                .append("</body>\n</html>\n");
        return page.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
