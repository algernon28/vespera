package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.similarity.DocumentFrequency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders {@link DocumentFrequency.Shares} as {@code document-frequency.html}, the page the boilerplate floor
 * is read off (ADR-262), through {@link ReportPage}. The rounding and the grouping into lines are {@code
 * similarity}'s; this holds the words and the cells. The page holds counts, shares and a run id, and no text of
 * a document, no hash and no path.
 */
final class DocumentFrequencyReport {

    private DocumentFrequencyReport() {}

    /** The name of the page, and of its JSON, without the ending (ADR-362 section 9). */
    static final String NAME = "document-frequency";

    /**
     * The same shares as {@link #render} draws, as the JSON written beside the page: the number of documents measured
     * and one row for each line, from the same values (ADR-362 section 9).
     */
    static Map<String, Object> json(DocumentFrequency.Shares shares, Instant writtenAt) {
        List<List<Object>> rows = new ArrayList<>();
        for (DocumentFrequency.ShareLine line : shares.lines()) {
            rows.add(List.of(
                    line.share().doubleValue(), line.shingles(), line.fewestDocuments(), shares.shingledDocumentCount()));
        }
        return AnalysisReportJson.tree(
                NAME,
                "Document frequency of passages",
                writtenAt,
                List.of(AnalysisReportJson.figure(
                        "documentsMeasured", "Documents measured", shares.shingledDocumentCount(), "count")),
                List.of(AnalysisReportJson.section(
                        "shares",
                        "Passages by the share of documents they are found in",
                        List.of("A passage found in one document is on no row."),
                        null,
                        AnalysisReportJson.table(
                                List.of(
                                        AnalysisReportJson.column("share", "Share of documents", "share"),
                                        AnalysisReportJson.column("passages", "Passages", "count"),
                                        AnalysisReportJson.column("fewestDocuments", "Fewest documents", "count"),
                                        AnalysisReportJson.column("documents", "N", "count")),
                                rows))));
    }

    static String render(DocumentFrequency.Shares shares, RunId contentCensusRunId) {
        StringBuilder rows = new StringBuilder();
        for (DocumentFrequency.ShareLine line : shares.lines()) {
            rows.append(ReportPage.row(
                    ReportPage.numberCell(line.share().toPlainString()),
                    ReportPage.numberCell(line.shingles()),
                    ReportPage.numberCell(line.fewestDocuments()),
                    ReportPage.numberCell(shares.shingledDocumentCount())));
        }

        String body = ReportPage.heading(1, "Document frequency of passages")
                + ReportPage.paragraph("Content census run <code>" + ReportPage.escape(contentCensusRunId.value())
                        + "</code>. Documents measured (N): " + shares.shingledDocumentCount() + ". Each row is a "
                        + "share of the documents, rounded down to the hundredth, with the number of passages "
                        + "found in that share of them, the fewest documents any of them is found in, and N. A "
                        + "passage found in one document is on no row.")
                + ReportPage.table(
                        "<thead>"
                                + ReportPage.headerRow("Share of documents", "Passages", "Fewest documents", "N")
                                + "</thead>\n<tbody>\n",
                        rows + "</tbody>\n")
                + ReportPage.paragraph("Set <code>boilerplateDocumentFrequencyFloor</code> in the profile to the "
                        + "share a row shows, and the passages on that row and on every row after it are "
                        + "boilerplate, none on the rows before. Or set it to the row's fewest documents divided "
                        + "by N, written in full or cut off after at least as many decimal places as N has "
                        + "digits. Never round it up: 2 / 3 rounded up to 0.67 leaves out the passages found in "
                        + "2 of 3 documents. Say in its <code>provenance</code> how you arrived at it.");
        return ReportPage.render("Document frequency of passages", body);
    }
}
