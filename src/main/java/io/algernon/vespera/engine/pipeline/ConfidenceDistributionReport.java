package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.extraction.ConfidenceDistribution;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>The refused-conversion line rendered below (ADR-139, section 7) is the one fact on this page
 * {@code measure} did not itself bucket — how many occurrences the converter never got to score at
 * all, under the same extraction run. Since ADR-143 those are the ones it blamed on itself or stopped
 * answering for; a file it could not open is a verdict with a metric row, and is not counted here. That line names the extraction run the refusal count came
 * from — {@link ConfidenceDistribution.Distribution#extractionRunId}, stage 2's own run — rather than the
 * stage-3 run this page itself is written under: {@code measure} counts under the former and writes
 * under the latter, so naming "this run" would name whichever one happened to be in reach rather
 * than the one the count is actually about.
 */
final class ConfidenceDistributionReport {

    private ConfidenceDistributionReport() {}

    /** The page's title and first heading, and its JSON's {@code title} (ADR-377 §5). */
    private static final String TITLE = "Text quality distribution";

    /** What every grade's bar shows (ADR-275 section 2). */
    private static final String BAR_HOVER = "The length of the bar is this grade's share of the documents counted.";

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
                            + "%\" title=\"" + ReportPage.escapeAttribute(BAR_HOVER) + "\"></div>")));
        }

        String body = ReportPage.heading(1, TITLE)
                + ReportPage.paragraph("Stage 2's <code>extraction_metric.mean_score</code> over every "
                        + "stage-2 survivor, excluding any occurrence whose score is not computed. Total "
                        + "documents counted: " + total + ".")
                + ReportPage.paragraph("The converter could not answer for "
                        + distribution.refusedConversionCount() + " occurrence(s) under extraction run "
                        + ReportPage.escape(distribution.extractionRunId().value()) + ", reporting a fault "
                        + "of its own or running out of time on several in a row -- examined by no stage, "
                        + "and recorded as an extraction-failed verdict rather than left unjudged.")
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
        return ReportPage.render(TITLE, body);
    }

    /** The name of the page, and of its JSON, without the ending (ADR-362 section 9). */
    static final String NAME = "confidence-distribution";

    /**
     * The same distribution as {@link #render} draws, as the JSON written beside the page: the figures the page states
     * first and one row for each grade, from the same values (ADR-362 section 9).
     */
    static AnalysisReportJson.Document json(ConfidenceDistribution.Distribution distribution, Instant writtenAt) {
        long total = distribution.totalCounted();
        List<List<Object>> rows = new ArrayList<>();
        for (ConfidenceDistribution.Bucket bucket : distribution.buckets()) {
            rows.add(List.of(
                    bucket.grade(),
                    formatRange(bucket.lowerBound(), bucket.upperBound()),
                    bucket.documentCount(),
                    total == 0 ? 0.0 : (double) bucket.documentCount() / total));
        }
        return new AnalysisReportJson.Document(
                1,
                NAME,
                TITLE,
                writtenAt,
                List.of(
                        new AnalysisReportJson.Figure("documentsCounted", "Documents counted", total, "count"),
                        new AnalysisReportJson.Figure(
                                "refusedConversions",
                                "Occurrences the converter could not answer for",
                                distribution.refusedConversionCount(),
                                "count")),
                List.of(new AnalysisReportJson.Section(
                        "grades",
                        "Documents by grade",
                        List.of("The mean score of every stage-2 survivor, by grade."),
                        new AnalysisReportJson.Chart("bars", "grade", List.of("documents")),
                        new AnalysisReportJson.Table(
                                List.of(
                                        new AnalysisReportJson.Column("grade", "Grade", "text"),
                                        new AnalysisReportJson.Column("range", "Score range", "text"),
                                        new AnalysisReportJson.Column("documents", "Documents", "count"),
                                        new AnalysisReportJson.Column("proportion", "Proportion", "share")),
                                rows))));
    }

    private static String formatRange(double lowerBound, double upperBound) {
        return "[" + format(lowerBound) + ", " + format(upperBound) + (upperBound >= 1.0 ? "]" : ")");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
