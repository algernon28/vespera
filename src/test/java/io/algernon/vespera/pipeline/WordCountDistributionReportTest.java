package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.WordCountDistribution;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code word-count-distribution.html} as {@link WordCountDistributionReport} renders it (ADR-282 section 5): a
 * table of the survivors by their words, a row a band and a column a detected format, and a table of what each
 * candidate word floor would remove, a row a floor and the same columns, each format labelled in the words {@code
 * format-mix.html} uses.
 *
 * <p>Rendered from a value made here: a PDF scan of 35 words; two one-word images and a poster of 12; a one-word
 * note, a two-word shortcut and a letter of 120 words, as text.
 */
@Epic("Extraction")
@Feature("Content census")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
class WordCountDistributionReportTest {

    private static final RunId THE_RUN = new RunId("a-content-census-run-of-this-test");

    /** The fourteen bands' counts of each format, as {@link WordCountDistribution} would measure the files above. */
    private static final WordCountDistribution.Distribution THE_FILES = new WordCountDistribution.Distribution(Map.of(
            DetectedFormat.PDF, inBands(Map.of(11, 1L)),
            DetectedFormat.IMAGE, inBands(Map.of(0, 2L, 10, 1L)),
            DetectedFormat.PLAIN_TEXT, inBands(Map.of(0, 1L, 1, 1L, 13, 1L))));

    /** The columns after the first: the formats in declaration order, as {@code format-mix.html} names them. */
    private static final List<String> THE_FORMAT_COLUMNS = List.of("PDF documents", "Images", "Text", "All formats");

    private static final Pattern A_TABLE = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern A_HEADER = Pattern.compile("<th[^>]*>(.*?)</th>", Pattern.DOTALL);
    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @Test
    @Story("The page the word floor is read off")
    @DisplayName("The first table has a row for each band of words and a column for each kind of file, then their sum")
    void theFirstTableIsTheSurvivorsByTheirWords() {
        String table = tablesOf(WordCountDistributionReport.render(THE_FILES, THE_RUN)).getFirst();

        claim(
                "its columns are the words, then each detected format with a file counted, in the words format-mix.html"
                        + " uses, then every format together",
                () -> assertThat(headerOf(table)).containsExactlyElementsOf(withFirst("Words", THE_FORMAT_COLUMNS)));
        claim(
                "a row a band, in order, each cell a count, and a last row of each column's sum: the one-word note and"
                        + " images in the first band, the shortcut in the band of 2, the poster from 11 to 19, the scan"
                        + " from 20 to 49 and the letter at 100 or more",
                () -> assertThat(rowsOf(table)).containsExactly(
                        List.of("1 or fewer", "0", "2", "1", "3"),
                        List.of("2", "0", "0", "1", "1"),
                        List.of("3", "0", "0", "0", "0"),
                        List.of("4", "0", "0", "0", "0"),
                        List.of("5", "0", "0", "0", "0"),
                        List.of("6", "0", "0", "0", "0"),
                        List.of("7", "0", "0", "0", "0"),
                        List.of("8", "0", "0", "0", "0"),
                        List.of("9", "0", "0", "0", "0"),
                        List.of("10", "0", "0", "0", "0"),
                        List.of("11 to 19", "0", "1", "0", "1"),
                        List.of("20 to 49", "1", "0", "0", "1"),
                        List.of("50 to 99", "0", "0", "0", "0"),
                        List.of("100 or more", "0", "0", "1", "1"),
                        List.of("All", "1", "3", "3", "7")));
    }

    @Test
    @Story("What a word floor would remove")
    @DisplayName("The second table has a row for each candidate word floor, with what it would remove of each kind of file")
    void theSecondTableIsWhatEachFloorWouldRemove() {
        String table = tablesOf(WordCountDistributionReport.render(THE_FILES, THE_RUN)).get(1);

        claim(
                "its columns are the floor, then the same kinds of file, then every kind together",
                () -> assertThat(headerOf(table)).containsExactlyElementsOf(withFirst("Word floor", THE_FORMAT_COLUMNS)));
        claim(
                "a row a candidate floor, 2 to 10, 20, 50 and 100, each cell the files of that kind with fewer words:"
                        + " 2 removes the one-word images and note alike, 3 the shortcut too, 20 the poster, 50 the scan",
                () -> assertThat(rowsOf(table)).containsExactly(
                        List.of("2", "0", "2", "1", "3"),
                        List.of("3", "0", "2", "2", "4"),
                        List.of("4", "0", "2", "2", "4"),
                        List.of("5", "0", "2", "2", "4"),
                        List.of("6", "0", "2", "2", "4"),
                        List.of("7", "0", "2", "2", "4"),
                        List.of("8", "0", "2", "2", "4"),
                        List.of("9", "0", "2", "2", "4"),
                        List.of("10", "0", "2", "2", "4"),
                        List.of("20", "0", "3", "2", "5"),
                        List.of("50", "1", "3", "2", "6"),
                        List.of("100", "1", "3", "2", "6")));
    }

    @Test
    @Story("The page the word floor is read off")
    @DisplayName("The page names its run and the key the floor is written into, and is a whole page with no script")
    void thePageNamesItsRunAndTheKey() {
        String html = WordCountDistributionReport.render(THE_FILES, THE_RUN);

        claim(
                "it names the content census it was written for",
                () -> assertThat(html).contains(THE_RUN.value()));
        claim(
                "it names the key the number read off it is written into (a page that informs a threshold names it)",
                () -> assertThat(html).contains("degenerateOutputWordFloor"));
        claim(
                "it is a whole page through ReportPage, reaching for no other file",
                () -> assertThat(html).contains("<!DOCTYPE html>").contains("</html>").doesNotContain("<script"));
    }

    /** Fourteen counts, one a band, zero but where {@code counts} names a band's index. */
    private static List<Long> inBands(Map<Integer, Long> counts) {
        List<Long> bands = new ArrayList<>(Collections.nCopies(WordCountDistribution.BANDS.size(), 0L));
        counts.forEach(bands::set);
        return bands;
    }

    private static List<String> withFirst(String first, List<String> rest) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(rest);
        return all;
    }

    private static List<String> tablesOf(String html) {
        List<String> tables = new ArrayList<>();
        Matcher table = A_TABLE.matcher(html);
        while (table.find()) {
            tables.add(table.group(1));
        }
        return tables;
    }

    private static List<String> headerOf(String table) {
        List<String> labels = new ArrayList<>();
        Matcher header = A_HEADER.matcher(table);
        while (header.find()) {
            labels.add(header.group(1).replaceAll("<[^>]*>", "").trim());
        }
        return labels;
    }

    /** The cells of each body row of {@code table}, tags taken out; a header row has no cell and is left out. */
    private static List<List<String>> rowsOf(String table) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(table);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("<[^>]*>", "").trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }
}
