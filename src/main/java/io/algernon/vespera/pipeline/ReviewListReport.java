package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RemovedOccurrence;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
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

    private ReviewListReport() {}

    /**
     * Writes the page for {@code count} failures to {@code out}, the rows as {@code eachFailure} hands them
     * over, in path order, and builds no string of the whole (ADR-214 section 6). It is the page {@link
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
            out.write(ReportPage.paragraph("Files that could not be read: " + count
                    + ". Each was marked and skipped; the run went on. They stay removed under this"
                    + " run. A file whose reason begins with could not be read, rejected, crashed the converter, timeout,"
                    + " capacity, target_unavailable or internal left nothing stored, so the next run of"
                    + " this stage asks the converter about it again."));
            out.write("<table>\n");
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
            out.write("</table>\n");
        }
        out.write(ReportPage.tail());
    }
}
