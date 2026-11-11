package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders the review list of files stage 2 could not read as one self-contained HTML file (ADR-175
 * section 7), through the shared {@link ReportPage} module (ADR-130).
 *
 * <p>The page is the other half of the rule that a file that fails is marked and skipped: the run goes
 * on, so this is where the operator finds what it went on without.
 *
 * <p>It does not say that running the same command again asks about these files again, which #326 asked
 * it to say: a finished stage 2 reads nothing under the same run (ADR-115), and a resumed one keeps the
 * verdicts its committed chunks wrote (ADR-181). What is true is what it says, and it says it only of
 * the rows it is true of: a file that could not be read (ADR-210), a call that failed, a timeout, and a
 * failure the converter blamed on itself leave nothing in the extraction cache (ADR-183), so a new
 * stage-2 run asks again. A failure the
 * converter blamed on the document is stored and is not asked about again, and the page promises
 * nothing for it.
 */
final class ReviewListReport {

    private static final String TITLE = "Files stage 2 could not read";

    /** The name of the report, its page's with {@code .html} for {@code .json}. */
    static final String NAME = "extraction-failures";

    private ReviewListReport() {}

    /** The paragraph the page and its JSON open with where {@code count} files could not be read. */
    private static String somethingWasFound(long count) {
        return "Files that could not be read: " + count
                + ". Each was marked and skipped; the run went on. They stay removed under this"
                + " run. A file whose reason begins with could not be read, rejected, crashed the converter, timeout,"
                + " capacity, target_unavailable or internal left nothing stored, so the next run of"
                + " this stage asks the converter about it again.";
    }

    /**
     * Writes the page to {@code page} and its JSON to {@code json} in one pass over the rows (ADR-362 section 9),
     * each from the same values: the JSON holds the count as its headline and each failure's path and reason as a
     * row of its one table, which a list of nothing leaves {@code null} as its page leaves none.
     *
     * @throws IOException if either writer refuses a write, including while {@code eachFailure} is handing over rows
     */
    static void write(
            Writer page,
            Writer json,
            long count,
            Instant writtenAt,
            Consumer<Consumer<RemovedOccurrence>> eachFailure)
            throws IOException {
        String paragraph =
                count == 0 ? "No file failed. Stage 2 read every file it was given." : somethingWasFound(count);
        AnalysisReportJson.StreamedSection section = new AnalysisReportJson.StreamedSection(
                "failures",
                TITLE,
                List.of(paragraph),
                List.of(
                        new AnalysisReportJson.Column("path", "Path", "path"),
                        new AnalysisReportJson.Column("reason", "Why", "text")));
        List<AnalysisReportJson.Figure> headline =
                List.of(new AnalysisReportJson.Figure("failed", "Files that could not be read", count, "count"));
        if (count == 0) {
            AnalysisReportJson.write(json, NAME, 1, TITLE, writtenAt, headline, section, false, rows -> {});
            write(page, count, eachFailure);
            return;
        }
        AnalysisReportJson.write(json, NAME, 1, TITLE, writtenAt, headline, section, true, rows ->
                write(page, count, each -> eachFailure.accept(failure -> {
                    rows.accept(List.of(failure.path(), failure.reason()));
                    each.accept(failure);
                })));
    }

    /**
     * Writes the page for {@code count} failures to {@code out}, the rows as {@code eachFailure} hands them
     * over, in path order, and builds no string of the whole (ADR-220 section 6). It is the page {@link
     * ReportPage#render} would make of the same rows, byte for byte.
     *
     * @param count how many failures {@code eachFailure} will hand over: the heading says so before any row
     * @param eachFailure hands each failure to the consumer it is given, once
     * @throws IOException if {@code out} refuses a write, including while {@code eachFailure} is handing over rows
     */
    static void write(Writer out, long count, Consumer<Consumer<RemovedOccurrence>> eachFailure)
            throws IOException {
        out.write(ReportPage.head(TITLE));
        out.write(ReportPage.heading(1, TITLE));
        if (count == 0) {
            out.write(ReportPage.paragraph("No file failed. Stage 2 read every file it was given."));
        } else {
            out.write(ReportPage.paragraph(somethingWasFound(count)));
            out.write(ReportPage.tableOpened());
            out.write(ReportPage.headerRow("Path", "Why"));
            try {
                eachFailure.accept(failure -> {
                    try {
                        out.write(ReportPage.row(
                                ReportPage.textCell(failure.path()), ReportPage.textCell(failure.reason())));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            out.write(ReportPage.tableClosed());
        }
        out.write(ReportPage.tail());
    }
}
