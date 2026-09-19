package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * Renders the arrangement as one self-contained HTML page (ADR-107) — plain, hand-assembled HTML, no
 * templating library, the shared {@link ReportPage} module the reports beside the database all
 * supply their title, prose and rows to (ADR-046, ADR-130).
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
 * <p><b>Every document named on it is a link to the document itself</b> (ADR-104) — an absolute
 * {@code file:} target composed from the recorded corpus root and the occurrence's root-relative
 * path. Nothing is copied in order to be linked to, and nothing is checked to still be there: a name
 * a reviewer cannot open is a name they can only take on trust, which is the one thing this page
 * exists to spare them.
 *
 * <p>The order shown is the stored order, never re-sorted here. If this page and the deliverable
 * sorted independently, the arrangement approved and the arrangement received would be two
 * arrangements sharing one name (ADR-112).
 */
final class ArrangementReport {

    private ArrangementReport() {}

    /**
     * One cluster of documents as the page shows it.
     *
     * @param label what the cluster is called, derived from its own highest-scoring document
     * @param documentCount how many documents it holds
     * @param leadDocument the document the name was taken from, named as a reader would recognise it
     * @param leadDocumentLink where that document actually is, so a reviewer can open it and disagree
     */
    record Cluster(String label, int documentCount, String leadDocument, String leadDocumentLink) {}

    /**
     * One exemplar's documents.
     *
     * @param seedPath the exemplar whose documents these are, named as a reader would recognise it
     * @param clusters its clusters, in the order the arrangement gives them
     */
    record Partition(String seedPath, List<Cluster> clusters) {

        int documentCount() {
            return clusters.stream().mapToInt(Cluster::documentCount).sum();
        }
    }

    static String render(String approvalName, String corpusRoot, List<Partition> partitions) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "How the documents are arranged"))
                .append(ReportPage.paragraph("Every document that survived is here, under the exemplar"
                        + " it was matched to and in the group it formed with the documents nearest it."
                        + " Nothing has been removed, merged or renamed to produce this page, and"
                        + " nothing on it was written for you — every name below was taken from a"
                        + " document you can open."))
                .append(ReportPage.paragraph("The documents themselves are still in <code>"
                        + ReportPage.escape(corpusRoot)
                        + "</code>. They have not been moved or copied."))
                .append(ReportPage.heading(2, "What you are being asked"))
                .append(ReportPage.paragraph("<b>Is this arrangement worth writing over?</b> The next"
                        + " step reads each group in full and writes a document about it, which is the"
                        + " slowest and most expensive thing this tool does. It will not start until you"
                        + " say this arrangement is the one you want."))
                .append(ReportPage.paragraph("To say so, put this into <code>profile.yaml</code> under"
                        + " <code>arrangementApproved</code>:"))
                .append(ReportPage.paragraph("<code class=\"name\">"
                        + ReportPage.escape(approvalName) + "</code>"))
                .append(ReportPage.paragraph("That name is this arrangement and no other. If the"
                        + " documents are grouped again — because you changed a setting, or added to"
                        + " the archive — this page gets a new name and you are asked again. That is"
                        + " deliberate: an approval that never expired would mean shipping an"
                        + " arrangement nobody looked at."))
                .append(ReportPage.paragraph("Write down what you actually checked in"
                        + " <code>provenance</code> beside it. Nothing verifies it; it is there so that"
                        + " whoever reads this archive later knows what the approval was worth."));

        if (partitions.isEmpty()) {
            return ReportPage.render(
                    "How the documents are arranged",
                    body.append(ReportPage.paragraph(
                                    "No document was matched to an exemplar, so there is nothing to"
                                            + " arrange."))
                            .toString());
        }

        for (Partition partition : partitions) {
            StringBuilder rows = new StringBuilder();
            for (Cluster cluster : partition.clusters()) {
                rows.append(ReportPage.row(
                        ReportPage.textCell(cluster.label()),
                        ReportPage.numberCell(cluster.documentCount()),
                        ReportPage.linkCell(cluster.leadDocumentLink(), cluster.leadDocument())));
            }
            body.append(ReportPage.heading(2, partition.seedPath()))
                    .append(ReportPage.paragraph(partition.documentCount() + " document(s) in "
                            + partition.clusters().size() + " group(s)."))
                    .append(ReportPage.table(
                            ReportPage.headerRow("Group", "Documents", "Named after"),
                            rows.toString()));
        }

        body.append(ReportPage.heading(2, "How to read this"))
                .append(ReportPage.paragraph("Exemplars are in order of how much of the archive sits"
                        + " under them, largest first, so the substantial part of what you have is at"
                        + " the top. Within each one, groups holding several documents come before"
                        + " groups holding one, and within those two tiers the groups closest to the"
                        + " exemplar come first."))
                .append(ReportPage.paragraph("<em>Named after</em> is the document each group's name"
                        + " was taken from — the one closest to the exemplar, and a link straight to"
                        + " where it already sits. Open a few. If a name does not describe the group it"
                        + " heads, that is what this page is for, and grouping the documents again with"
                        + " different settings is cheap compared with what comes next."))
                .append(ReportPage.paragraph("A group holding one document is a real outcome rather"
                        + " than a mistake: it means that document was collected on its own merits and"
                        + " not because it belongs with the others."));

        return ReportPage.render("How the documents are arranged", body.toString());
    }
}
