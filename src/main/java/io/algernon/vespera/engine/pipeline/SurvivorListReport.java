package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders the survivor list (CONTEXT.md) of files stage 2 converted and did not remove as one self-contained
 * HTML file (ADR-250 section 2), through the shared {@link ReportPage} module (ADR-130).
 *
 * <p>Its opening paragraph names the review list and says that a file converted and then removed as degenerate
 * output is on neither page, so that the two counts are not read as all that entered the stage.
 */
final class SurvivorListReport {

    /** The page's title and first heading, and its JSON's {@code title} (ADR-377 §5). */
    private static final String TITLE = "Files whose text was read and kept";

    /** What stands where a row holds no page count. */
    private static final String A_DASH = "&mdash;";

    /** The name of the report, its page's with {@code .html} for {@code .json}. */
    static final String NAME = "extraction-survivors";

    private SurvivorListReport() {}

    /** The paragraph the page and its JSON open with where {@code count} files came through. */
    private static String somethingCameThrough(long count) {
        return "Files that came through: " + count
                + ". Each was converted to text and not removed by this stage, so the stages after it read it."
                + " A file that could not be read is listed in extraction-failures.html. A file that was"
                + " converted and then removed as degenerate output is on neither list.";
    }

    /**
     * Writes the page to {@code page} and its JSON to {@code json} in one pass over the rows (ADR-362 section 9),
     * each from the same values: the JSON holds the count as its headline and each survivor's path, pages and
     * words as a row of its one table, a file with no page count holding {@code null} where the page holds a dash,
     * and no table where the page lists none.
     *
     * @throws IOException if either writer refuses a write, including while {@code eachSurvivor} is handing over rows
     */
    static void write(
            Writer page, Writer json, long count, Instant writtenAt, Consumer<Consumer<Row>> eachSurvivor)
            throws IOException {
        String paragraph = count == 0
                ? "No file came through. Stage 2 kept none of the files it was given."
                : somethingCameThrough(count);
        AnalysisReportJson.StreamedSection section = new AnalysisReportJson.StreamedSection(
                "survivors",
                TITLE,
                List.of(paragraph),
                List.of(
                        new AnalysisReportJson.Column("path", "Path", "path"),
                        new AnalysisReportJson.Column("pages", "Pages", "count"),
                        new AnalysisReportJson.Column("words", "Words", "count")));
        List<AnalysisReportJson.Figure> headline =
                List.of(new AnalysisReportJson.Figure("cameThrough", "Files that came through", count, "count"));
        if (count == 0) {
            AnalysisReportJson.write(json, NAME, 1, TITLE, writtenAt, headline, section, false, rows -> {});
            write(page, count, eachSurvivor);
            return;
        }
        AnalysisReportJson.write(json, NAME, 1, TITLE, writtenAt, headline, section, true, rows ->
                write(page, count, each -> eachSurvivor.accept(survivor -> {
                    rows.accept(Arrays.asList(survivor.path(), survivor.pages(), survivor.words()));
                    each.accept(survivor);
                })));
    }

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
            out.write(ReportPage.paragraph(somethingCameThrough(count)));
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
