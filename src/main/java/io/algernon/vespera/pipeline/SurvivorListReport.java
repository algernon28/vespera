package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.function.Consumer;

/**
 * Renders the survivor list (CONTEXT.md) of files stage 2 converted and did not remove as one self-contained
 * HTML file (ADR-249 section 2), through the shared {@link ReportPage} module (ADR-130).
 *
 * <p>Its opening paragraph names the review list and says that a file converted and then removed as degenerate
 * output is on neither page, so that the two counts are not read as all that entered the stage.
 */
final class SurvivorListReport {

    private static final String TITLE = "Files stage 2 read and kept";

    /** What stands where a row holds no page count. */
    private static final String A_DASH = "&mdash;";

    private SurvivorListReport() {}

    /** One survivor: its path, its page count where it has one, and its word count. */
    record Row(String path, Integer pages, int words) {}

    /**
     * Writes the page for {@code count} survivors to {@code out}, the rows as {@code eachSurvivor} hands them
     * over, and builds no string of the whole (ADR-220 section 6). It is the page {@link ReportPage#render}
     * would make of the same parts, byte for byte.
     *
     * @param count how many survivors {@code eachSurvivor} will hand over: the heading says so before any row
     * @param eachSurvivor hands each survivor to the consumer it is given, once, in the order to be shown
     * @throws IOException if {@code out} refuses a write, including while {@code eachSurvivor} is handing over rows
     */
    static void write(Writer out, long count, Consumer<Consumer<Row>> eachSurvivor) throws IOException {
        out.write(ReportPage.head(TITLE));
        out.write(ReportPage.heading(1, TITLE));
        if (count == 0) {
            out.write(ReportPage.paragraph("No file came through. Stage 2 kept none of the files it was given."));
        } else {
            out.write(ReportPage.paragraph("Files that came through: " + count
                    + ". Each was converted to text and not removed by this stage, so the stages after it read it."
                    + " A file that could not be read is listed in extraction-failures.html. A file that was"
                    + " converted and then removed as degenerate output is on neither list."));
            out.write(ReportPage.tableOpened());
            out.write(ReportPage.headerRow("Path", "Pages", "Words"));
            try {
                eachSurvivor.accept(survivor -> {
                    try {
                        out.write(ReportPage.row(
                                ReportPage.textCell(survivor.path()),
                                survivor.pages() == null
                                        ? ReportPage.numberCell(A_DASH)
                                        : ReportPage.numberCell(survivor.pages().longValue()),
                                ReportPage.numberCell(survivor.words())));
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
