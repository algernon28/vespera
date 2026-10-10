package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * Three seed partitions as stage 6a's page is handed them: what {@link
 * ArrangementPageIsWrittenAsItsPartitionsComeTest} writes a partition at a time, and what {@link
 * TheArrangementPageRenderedWhole} was captured from.
 *
 * <p>It names no method ADR-223 adds, so it compiles against the tree the expected bytes were captured from.
 */
final class ThreePartitionsOnThePage {

    static final String APPROVAL_NAME = "f00df00df00d";

    static final String CORPUS_ROOT = "C:\\archive <2019> & after";

    private ThreePartitionsOnThePage() {}

    /** The partitions in the order the arrangement gives them, each with its clusters in theirs. */
    static List<ArrangementReport.Partition> partitions() {
        return List.of(
                new ArrangementReport.Partition(
                        "seeds/Beta & Co [2019].pdf",
                        List.of(
                                new ArrangementReport.Cluster(
                                        "Fire doors | inspection",
                                        3,
                                        "reports/doors [final].docx",
                                        "file:///C:/archive/reports/doors%20%5Bfinal%5D.docx"),
                                new ArrangementReport.Cluster(
                                        "Smoke dampers", 2, "reports/dampers \"old\".pdf", "file:///C:/archive/reports/dampers.pdf"))),
                new ArrangementReport.Partition(
                        "seeds/alpha.docx",
                        List.of(
                                new ArrangementReport.Cluster(
                                        "Stairwells", 2, "reports/stairs, north.pdf", "file:///C:/archive/reports/stairs.pdf"),
                                new ArrangementReport.Cluster(
                                        "A note on <risers>", 1, "notes/risers.txt", "file:///C:/archive/notes/risers.txt"))),
                new ArrangementReport.Partition(
                        "seeds/gamma.docx",
                        List.of(new ArrangementReport.Cluster(
                                "Hose reels", 1, "notes/hose reels.txt", "file:///C:/archive/notes/hose%20reels.txt"))));
    }
}
