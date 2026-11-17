package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.synthesis.GivenName;
import io.algernon.vespera.engine.synthesis.NameTurnDown;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the arrangement as one self-contained HTML page (ADR-107) — plain, hand-assembled HTML, no
 * templating library, the shared {@link ReportPage} module the reports beside the database all
 * supply their title, prose and rows to (ADR-046, ADR-130) — as its partitions come: {@link #open}, then
 * {@link #partition} for each, then {@link #close} (ADR-223 section 3).
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
     * One part of a topic that is shown with a line of counts and a table of clusters: a subtopic of a crowded topic,
     * or the whole of a topic with no subtopics (ADR-409 section 7).
     *
     * @param order the subtopic's place within its topic counted from 1, and 0 for the whole of a topic with none
     * @param name the subtopic's recorded name, or {@code null} for the whole of a topic with no subtopics
     * @param clusters its clusters, in the order the arrangement gives them
     */
    record Part(int order, GivenName name, List<Cluster> clusters) {

        int documentCount() {
            return clusters.stream().mapToInt(Cluster::documentCount).sum();
        }

        /** The paragraph that says why the model's name was not used, or {@code null} where it was. */
        String notUsed() {
            return name == null ? null : name.turnedDown().map(why -> notUsedBecause(true, why)).orElse(null);
        }
    }

    /**
     * One topic's documents.
     *
     * @param name the topic's recorded name
     * @param parts its subtopics in their order, or the one part that holds all its clusters where it has none
     */
    record Topic(GivenName name, List<Part> parts) {

        /** A topic with no subtopics, holding {@code clusters} whole. */
        static Topic undivided(GivenName name, List<Cluster> clusters) {
            return new Topic(name, List.of(new Part(0, null, clusters)));
        }

        /** The paragraph that says why the model's name was not used, or {@code null} where it was. */
        String notUsed() {
            return name.turnedDown().map(why -> notUsedBecause(false, why)).orElse(null);
        }

        boolean isDivided() {
            return parts.size() > 1 || (parts.size() == 1 && parts.getFirst().name() != null);
        }

        int documentCount() {
            return parts.stream().mapToInt(Part::documentCount).sum();
        }

        int clusterCount() {
            return parts.stream().mapToInt(part -> part.clusters().size()).sum();
        }
    }

    /**
     * The paragraph that stands directly after a name that fell back (ADR-409 section 7), in English whatever the
     * deliverable language: that the model's name was not used, why, and where the name shown comes from.
     *
     * @param ofASubtopic whether the name is a subtopic's, and not a topic's
     * @param turnedDown why the model's name was turned down
     */
    static String notUsedBecause(boolean ofASubtopic, NameTurnDown turnedDown) {
        String why = switch (turnedDown) {
            case NOTHING_SENT -> "nothing of it fitted what the model is given to read, so the model was not asked";
            case QUESTION_CUT -> "the question was longer than the model could read at once";
            case RAN_OUT_OF_ROOM -> "the model's answer reached its length limit before it was finished";
            case NOT_THE_SHAPE_ASKED -> "the model's answer did not come back in the shape it was asked for";
            case EMPTY -> "the model's answer was empty";
            case NOT_ONE_SHORT_LINE -> "the model's answer was not one short line";
            case SAME_AS_AN_EARLIER_NAME -> ofASubtopic
                    ? "asked a second time, the model again gave another subtopic's name"
                    : "asked a second time, the model again gave another topic's name";
        };
        return ofASubtopic
                ? "The naming model's name for this subtopic was not used: " + why + ". The name above stands in for it,"
                        + " taken from the document most like the rest of the subtopic, or from the subtopic's place"
                        + " where that document gives none."
                : "The naming model's name for this topic was not used: " + why + ". The name above stands in for it,"
                        + " taken from the seed feeding the topic the most passages, or from the topic's place where"
                        + " that seed gives none.";
    }

    /** The page's title and first heading, and its JSON's {@code title} (ADR-377 §5). */
    private static final String TITLE = "Grouping to approve";

    /** The page's first paragraph, and the first string of its JSON's {@code overview} section (ADR-409 section 7). */
    static final String OPENING = "Every document that survived is here, placed under one topic and in the group it"
            + " formed with the documents nearest it. Nothing has been removed, merged or renamed to produce this page."
            + " The names of the topics, and of the subtopics a crowded topic is divided into, were written by the"
            + " naming model from the documents' own words, except where a paragraph under a name says otherwise. They"
            + " are shown here to read: they are not part of what you approve, and they cannot be changed here. Every"
            + " group's name below was taken from a document you can open.";

    /** What the page and its JSON say where no document was placed under a topic. */
    private static final String NOTHING_PLACED = "No document was placed under a topic, so there is nothing to arrange.";

    /**
     * Everything of the page before its first topic: what is being asked, and the name to approve. The
     * page is written as its topics come, so none is needed to begin it (ADR-223 section 3).
     */
    static void open(Appendable page, String approvalName, String corpusRoot) throws IOException {
        page.append(ReportPage.head(TITLE))
                .append(ReportPage.heading(1, TITLE))
                .append(ReportPage.paragraph(OPENING))
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
    }

    /**
     * One topic's heading, then, under a topic with no subtopics, its line of counts and its table, or, under a crowded
     * topic, each subtopic's heading with its own, appended once the rows are read back (ADR-409 section 7). A name that
     * fell back is followed directly by the paragraph that says so.
     */
    static void topic(Appendable page, Topic topic) throws IOException {
        page.append(ReportPage.heading(2, topic.name().name()));
        if (topic.notUsed() != null) {
            page.append(ReportPage.paragraph(topic.notUsed()));
        }
        for (Part part : topic.parts()) {
            if (part.name() != null) {
                page.append(ReportPage.heading(3, part.name().name()));
                if (part.notUsed() != null) {
                    page.append(ReportPage.paragraph(part.notUsed()));
                }
            }
            StringBuilder rows = new StringBuilder();
            for (Cluster cluster : part.clusters()) {
                rows.append(ReportPage.row(
                        ReportPage.textCell(cluster.label()),
                        ReportPage.numberCell(cluster.documentCount()),
                        ReportPage.linkCell(cluster.leadDocumentLink(), cluster.leadDocument())));
            }
            page.append(ReportPage.paragraph(countsOf(part)))
                    .append(ReportPage.table(
                            ReportPage.headerRow("Group", "Documents", "Named after"),
                            rows.toString()));
        }
    }

    private static String countsOf(Part part) {
        return part.documentCount() + " document(s) in " + part.clusters().size() + " group(s).";
    }

    /**
     * Everything of the page after its last topic, or, where {@code topicsWritten} is none, the
     * sentence that there is nothing to arrange.
     */
    static void close(Appendable page, int topicsWritten) throws IOException {
        if (topicsWritten == 0) {
            page.append(ReportPage.paragraph(NOTHING_PLACED))
                    .append(ReportPage.tail());
            return;
        }
        page.append(ReportPage.heading(2, "How to read this"))
                .append(ReportPage.paragraph("Topics are in order of how much of the archive sits under them, largest"
                        + " first, so the substantial part of what you have is at the top, and a crowded topic's"
                        + " subtopics are in the same order within it. Within each topic, or within each of its"
                        + " subtopics where it is divided, groups holding several documents come before groups"
                        + " holding one, and within those two tiers the groups whose documents scored highest for"
                        + " relevance come first."))
                .append(ReportPage.paragraph("<em>Named after</em> is the document each group's name was taken from"
                        + " — the one in the group that scored highest for relevance, and a link straight to where it"
                        + " already sits. Open a few. If a name does not describe the group it heads, that is what this"
                        + " page is for, and grouping the documents again with different settings is cheap compared"
                        + " with what comes next."))
                .append(ReportPage.paragraph("A group holding one document is a real outcome rather"
                        + " than a mistake: it means that document was collected on its own merits and"
                        + " not because it belongs with the others."))
                .append(ReportPage.tail());
    }

    /** The page's name in {@code Reports.ALL}, and its JSON's without the ending (ADR-362 section 9). */
    static final String NAME = StepNames.ARRANGEMENT;

    private static final List<AnalysisReportJson.Column> COLUMNS = List.of(
            new AnalysisReportJson.Column("group", "Group", "text"),
            new AnalysisReportJson.Column("documents", "Documents", "count"),
            new AnalysisReportJson.Column("namedAfter", "Named after", "path"));

    /**
     * What the page shows, as JSON (ADR-362 section 9), begun: the sections that stand before the first partition,
     * the approval name among them. Written beside the page, a partition at a time, and ended by {@link #closeJson}.
     */
    static AnalysisReportJson openJson(Writer json, String approvalName, String corpusRoot, Instant writtenAt)
            throws IOException {
        AnalysisReportJson report = AnalysisReportJson.begin(json, NAME, TITLE, writtenAt);
        report.section(
                "overview",
                TITLE,
                List.of(
                        OPENING,
                        "The documents themselves are still in " + corpusRoot + ". They have not been moved or copied."),
                null,
                null);
        report.section(
                "approval",
                "What you are being asked",
                List.of(
                        "Is this arrangement worth writing over? The next step reads each group in full and writes a"
                                + " document about it. It will not start until you say this arrangement is the one you"
                                + " want.",
                        "To say so, put this into profile.yaml under arrangementApproved:",
                        approvalName,
                        "That name is this arrangement and no other. If the documents are grouped again, this page"
                                + " gets a new name and you are asked again.",
                        "Write down what you actually checked in provenance beside it."),
                null,
                null);
        return report;
    }

    /**
     * One topic's sections (ADR-409 section 7): its own, {@code topic-<n>}, titled by its name, its text the fallback
     * paragraph's words where its name fell back and then its line of counts and its table where it has no subtopics;
     * and, directly after a topic with subtopics, one {@code topic-<n>-subtopic-<m>} for each, in their order, with its
     * line of counts and its table, each lead document by absolute path.
     */
    static void topicJson(AnalysisReportJson report, int place, Topic topic) throws IOException {
        List<String> text = new ArrayList<>();
        if (topic.notUsed() != null) {
            text.add(topic.notUsed());
        }
        if (!topic.isDivided()) {
            Part whole = topic.parts().getFirst();
            text.add(countsOf(whole));
            report.section("topic-" + place, topic.name().name(), text, COLUMNS, rowsOf(whole));
            return;
        }
        report.section("topic-" + place, topic.name().name(), text, null, null);
        for (Part part : topic.parts()) {
            List<String> partText = new ArrayList<>();
            if (part.notUsed() != null) {
                partText.add(part.notUsed());
            }
            partText.add(countsOf(part));
            report.section(
                    "topic-" + place + "-subtopic-" + part.order(),
                    part.name().name(),
                    partText,
                    COLUMNS,
                    rowsOf(part));
        }
    }

    private static List<List<Object>> rowsOf(Part part) {
        List<List<Object>> rows = new ArrayList<>();
        for (Cluster cluster : part.clusters()) {
            rows.add(List.of(
                    cluster.label(),
                    cluster.documentCount(),
                    Path.of(URI.create(cluster.leadDocumentLink())).toString()));
        }
        return rows;
    }

    /** Ends the JSON with its headline: the topics, the groups and the documents the page shows. */
    static void closeJson(AnalysisReportJson report, int topics, int clusters, long documents)
            throws IOException {
        if (topics == 0) {
            report.section(
                    "nothing",
                    "Nothing to arrange",
                    List.of(NOTHING_PLACED),
                    null,
                    null);
        }
        report.end(List.of(
                new AnalysisReportJson.Figure("topics", "Topics", topics, "count"),
                new AnalysisReportJson.Figure("clusters", "Groups", clusters, "count"),
                new AnalysisReportJson.Figure("documents", "Documents", documents, "count")));
    }
}
