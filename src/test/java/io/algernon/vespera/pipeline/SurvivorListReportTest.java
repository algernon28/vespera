package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The survivor list as a page (ADR-250 section 2): its title, the paragraph that says how many came through
 * and which files are on neither list, and one row for each survivor in the order handed over, with its
 * path, its page count or a dash, and its word count. Written as its rows come (ADR-220 section 6), and the
 * page {@link ReportPage} makes of the same parts, character for character.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-250", url = Adr.STAGE_2_WRITES_THE_SURVIVOR_LIST_BESIDE_THE_REVIEW_LIST, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class SurvivorListReportTest {

    private static final String THE_TITLE = "Files stage 2 read and kept";

    /** Four pages: a survivor the paginated pipeline converted. */
    private static final SurvivorListReport.Row PAGINATED = new SurvivorListReport.Row("a/first.pdf", 4, 900);

    /** No page count: a survivor the simple pipeline converted, which counts no pages. */
    private static final SurvivorListReport.Row UNPAGINATED = new SurvivorListReport.Row("b/second.txt", null, 12);

    /** A path holding the three characters a page would read as markup. */
    private static final SurvivorListReport.Row NAMED_IN_MARKUP = new SurvivorListReport.Row("c/<b>&co.txt", null, 3);

    private static final List<SurvivorListReport.Row> THREE_SURVIVORS = List.of(PAGINATED, UNPAGINATED, NAMED_IN_MARKUP);

    /** What stands in a row for a page count the row does not hold. */
    private static final String A_DASH = "&mdash;";

    private static final String WHAT_THE_WRITER_SAID = "the disk is full";

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("The page says how many files came through, which files are on neither list, and names each file with its pages and words in the order given")
    void thePageIsItsParagraphAndOneRowASurvivor() throws IOException {
        StringWriter page = new StringWriter();

        SurvivorListReport.write(page, THREE_SURVIVORS.size(), THREE_SURVIVORS::forEach);

        String expected = ReportPage.render(
                THE_TITLE,
                ReportPage.heading(1, THE_TITLE)
                        + ReportPage.paragraph("Files that came through: " + THREE_SURVIVORS.size()
                                + ". Each was converted to text and not removed by this stage, so the stages after"
                                + " it read it. A file that could not be read is listed in extraction-failures.html."
                                + " A file that was converted and then removed as degenerate output is on neither"
                                + " list.")
                        + ReportPage.table(
                                ReportPage.headerRow("Path", "Pages", "Words"),
                                ReportPage.row(
                                                ReportPage.textCell(PAGINATED.path()),
                                                ReportPage.numberCell(PAGINATED.pages().longValue()),
                                                ReportPage.numberCell(PAGINATED.words()))
                                        + ReportPage.row(
                                                ReportPage.textCell(UNPAGINATED.path()),
                                                ReportPage.numberCell(A_DASH),
                                                ReportPage.numberCell(UNPAGINATED.words()))
                                        + ReportPage.row(
                                                ReportPage.textCell(NAMED_IN_MARKUP.path()),
                                                ReportPage.numberCell(A_DASH),
                                                ReportPage.numberCell(NAMED_IN_MARKUP.words()))));
        claim(
                "the page is its title, the paragraph stating the " + THREE_SURVIVORS.size() + " files given and"
                        + " naming the page of the files that could not be read, and a table of path, pages and"
                        + " words with one row for each file in the order given: character for character",
                () -> assertThat(page.toString()).isEqualTo(expected));
        claim(
                "a file with no page count shows a dash where the count would be, not a zero and not the word"
                        + " null",
                () -> assertThat(page.toString())
                        .contains(ReportPage.textCell(UNPAGINATED.path()) + ReportPage.numberCell(A_DASH))
                        .doesNotContain("null"));
        claim(
                "a path holding markup is written so a browser shows it and does not obey it",
                () -> assertThat(page.toString()).contains("c/&lt;b&gt;&amp;co.txt").doesNotContain("<b>"));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("With no file come through, the page says so, holds no table, and asks for no rows")
    void aPageOfNoneSaysSoAndAsksForNoRows() throws IOException {
        StringWriter page = new StringWriter();

        SurvivorListReport.write(page, 0, each -> {
            throw new AssertionError("the rows were asked for although the count said there are none");
        });

        claim(
                "the page is its title and one paragraph saying no file came through, with no table",
                () -> assertThat(page.toString())
                        .isEqualTo(ReportPage.render(
                                THE_TITLE,
                                ReportPage.heading(1, THE_TITLE)
                                        + ReportPage.paragraph("No file came through. Stage 2 kept none of the"
                                                + " files it was given."))));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("A page that cannot be written to while its rows are coming fails as a failed write, not as anything else")
    void aWriteRefusedWhileRowsComeIsAnIoException() {
        Writer refusing = new Writer() {
            @Override
            public void write(char[] characters, int from, int length) throws IOException {
                if (new String(characters, from, length).contains(PAGINATED.path())) {
                    throw new IOException(WHAT_THE_WRITER_SAID);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        Throwable thrown =
                catchThrowable(() -> SurvivorListReport.write(refusing, THREE_SURVIVORS.size(), THREE_SURVIVORS::forEach));

        claim(
                "the refusal of the first row's write reaches the caller as the input-output failure the writer"
                        + " raised, with what it said, so the caller that says a failed page in one line"
                        + " recognises it",
                () -> assertThat(thrown).isExactlyInstanceOf(IOException.class).hasMessage(WHAT_THE_WRITER_SAID));
    }
}
