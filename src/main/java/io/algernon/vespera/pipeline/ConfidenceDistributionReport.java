package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConfidenceDistribution;
import java.util.Locale;

/**
 * Renders a {@link ConfidenceDistribution.Distribution} as one self-contained HTML file (ADR-075) —
 * plain, hand-assembled HTML, no templating library, no new dependency (ADR-046: the pom carries what
 * a recorded decision requires, not what current code happens to use).
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
            rows.append("<tr>")
                    .append("<td>")
                    .append(escape(bucket.grade()))
                    .append("</td>")
                    .append("<td>")
                    .append(formatRange(bucket.lowerBound(), bucket.upperBound()))
                    .append("</td>")
                    .append("<td>")
                    .append(bucket.documentCount())
                    .append("</td>")
                    .append("<td><div class=\"bar\" style=\"width:")
                    .append(String.format(Locale.ROOT, "%.1f", proportion * 100))
                    .append("%\"></div></td>")
                    .append("</tr>\n");
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>Confidence-score distribution</title>\n"
                + "<style>\n"
                + "body { font-family: sans-serif; margin: 2em; }\n"
                + "table { border-collapse: collapse; width: 100%; max-width: 800px; }\n"
                + "th, td { border: 1px solid #ccc; padding: 0.4em 0.8em; text-align: left; }\n"
                + ".bar { background: #4a90d9; height: 1em; }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<h1>Confidence-score distribution</h1>\n"
                + "<p>Stage 2's <code>extraction_metric.mean_score</code> over every stage-2 survivor, "
                + "excluding any occurrence whose score is not computed. Total documents counted: "
                + total + ".</p>\n"
                + "<table>\n"
                + "<thead><tr><th>Grade</th><th>Score range</th><th>Documents</th><th>Proportion</th></tr></thead>\n"
                + "<tbody>\n"
                + rows
                + "</tbody>\n"
                + "</table>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String formatRange(double lowerBound, double upperBound) {
        return "[" + format(lowerBound) + ", " + format(upperBound) + (upperBound >= 1.0 ? "]" : ")");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
