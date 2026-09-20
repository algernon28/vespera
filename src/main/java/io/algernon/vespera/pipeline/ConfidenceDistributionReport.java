package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConfidenceDistribution;
import java.util.Locale;

/**
 * Renders a {@link ConfidenceDistribution.Distribution} as one self-contained HTML file (ADR-075) —
 * plain, hand-assembled HTML, no templating library, no new dependency (ADR-046: the pom carries what
 * a recorded decision requires, not what current code happens to use) — through the shared {@link
 * ReportPage} module the reports beside the database all supply their title, prose and rows to
 * (ADR-130).
 *
 * <p>Lives in {@code pipeline} rather than {@code extraction} because rendering is composition over a
 * value {@code extraction} already computed and handed back — the same reason {@link
 * ContentCensusTasklet} rather than {@code extraction} writes the file and the profile pointer
 * (ADR-040).
 *
 * <p>{@link #render} is a pure function of the {@code Distribution} value {@link
 * ConfidenceDistribution#measure} both writes to its own table and returns here — the same value
 * drives both outputs, so the HTML's numbers and the table's numbers can never silently drift apart
 * (ADR-075's acceptance criterion 5).
 */
final class ConfidenceDistributionReport {

    private ConfidenceDistributionReport() {}

    static String render(ConfidenceDistribution.Distribution distribution) {
        long total = distribution.totalCounted();
        StringBuilder rows = new StringBuilder();
        for (ConfidenceDistribution.Bucket bucket : distribution.buckets()) {
            double proportion = total == 0 ? 0.0 : (double) bucket.documentCount() / total;
            rows.append(ReportPage.row(
                    ReportPage.textCell(bucket.grade()),
                    ReportPage.textCell(formatRange(bucket.lowerBound(), bucket.upperBound())),
                    ReportPage.htmlCell(Long.toString(bucket.documentCount())),
                    ReportPage.htmlCell("<div class=\"bar\" style=\"width:"
                            + String.format(Locale.ROOT, "%.1f", proportion * 100)
                            + "%\"></div>")));
        }

        String body = ReportPage.heading(1, "Confidence-score distribution")
                + ReportPage.paragraph("Stage 2's <code>extraction_metric.mean_score</code> over every "
                        + "stage-2 survivor, excluding any occurrence whose score is not computed. Total "
                        + "documents counted: " + total + ".")
                + ReportPage.table(
                        "<thead>"
                                + ReportPage.headerRow(
                                        "Grade", "Score range", "Documents", "Proportion")
                                + "</thead>\n<tbody>\n",
                        rows + "</tbody>\n")
                + ReportPage.paragraph("Write the number you choose into "
                        + "<code>degenerateOutputConfidenceFloor</code> in the profile, and say in its "
                        + "<code>provenance</code> how you arrived at it. A document whose mean score "
                        + "falls below it is treated as degenerate output and removed. The key ships "
                        + "unset, and while it is unset no score is low enough to remove anything — this "
                        + "distribution is what a first run measures so that the number can be read off "
                        + "it rather than guessed.");
        return ReportPage.render("Confidence-score distribution", body);
    }

    private static String formatRange(double lowerBound, double upperBound) {
        return "[" + format(lowerBound) + ", " + format(upperBound) + (upperBound >= 1.0 ? "]" : ")");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
