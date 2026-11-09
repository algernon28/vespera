package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.Translations;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.ui.NativeViewForTests;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.OptionalDouble;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each analysis report's native view shows what its page shows (ADR-362 section 10). One case for each of the twelve,
 * added by the ticket of the class that writes the report: the jar writes the report over a fixture, the window opens
 * its JSON as the report's tab, and every headline number, every section, and every row of its page is in the view, in
 * the page's order. This class holds the translation report's, over the five survivors in three
 * detected languages that {@link TranslationReportTest} resolves, written by the translation step's own writers.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("754")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    private static final String TRANSLATIONS = "translations";

    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    private static final Pattern A_SECOND_HEADING = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);

    private static final Pattern A_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final RunId SCORING = new RunId("a-scoring-run-of-this-test");

    private static final String MODEL = "another-comparing-model";

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The translation report's tab holds every number, section and row of its page, in its order, and draws no viewer")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        List<Translations.Member> members = List.of(
                member(1, "en/a.html", "ENGLISH"),
                member(2, "zh/a.html", "CHINESE"),
                member(3, "manuali/c.pdf", "ITALIAN"),
                member(4, "docs/d.pdf", "ENGLISH"),
                member(5, "docs/e.pdf", null));
        List<Translations.Likeness> likenesses = List.of(
                new Translations.Likeness(new OccurrenceId(1), new OccurrenceId(2), 0.91),
                new Translations.Likeness(new OccurrenceId(3), new OccurrenceId(4), 0.81));
        Translations.Resolution resolution = Translations.resolve(members, likenesses, OptionalDouble.empty(), List.of());
        TranslationReport.writeJson(workingDirectory.resolve(TRANSLATIONS + ".json"), WRITTEN_AT, resolution, SCORING,
                MODEL, members.size(), 0, "unset", "unset", false);
        String page = TranslationReport.render(resolution, SCORING, MODEL, members.size(), 0, "unset", "unset", false);
        Files.writeString(workingDirectory.resolve(TRANSLATIONS + ".html"), page, StandardCharsets.UTF_8);

        NativeViewForTests.View view = NativeViewForTests.open(Files.createDirectory(folder.resolve("window")),
                workingDirectory, TRANSLATIONS);

        List<List<String>> pageRows = rowsOf(page);
        List<List<String>> pageLanguages = pageRows.stream().filter(row -> row.size() == 2).toList();
        List<List<String>> pageLikenesses = pageRows.stream().filter(row -> row.size() == 4).toList();
        List<String> pageHeadings = headingsOf(page);
        List<String> viewTitles = view.sections().stream().map(NativeViewForTests.Section::title).toList();
        StringBuilder viewText = new StringBuilder(String.join(" ", view.tiles()));
        view.sections().forEach(section -> {
            viewText.append(' ').append(section.title()).append(' ').append(String.join(" ", section.paragraphs()));
            section.rows().forEach(row -> viewText.append(' ').append(String.join(" ", row)));
        });

        claim("the page states its title, and the tab is titled by it, the viewer being no part of the window",
                () -> {
                    assertThat(page).contains("<title>Copies in another language</title>");
                    assertThat(view.title()).isEqualTo("Copies in another language");
                    assertThat(view.viewer()).isFalse();
                });
        claim("the tab's sections are the page's headings in the page's order, after the scoring run's, which the page"
                        + " states under its title",
                () -> {
                    assertThat(pageHeadings).hasSize(5);
                    assertThat(viewTitles.subList(1, viewTitles.size())).isEqualTo(pageHeadings);
                    assertThat(viewTitles.getFirst()).isEqualTo("Scoring run");
                });
        claim("the languages table of the tab holds a row for each of the page's, in its order, the survivors a language"
                        + " carries as the same number",
                () -> {
                    assertThat(pageLanguages).isNotEmpty();
                    assertThat(rowsOfSection(view, "languages")).isEqualTo(pageLanguages);
                });
        claim("the likenesses table of the tab holds a row for each of the page's, in its order, each cell the same"
                        + " number as the page's",
                () -> assertThat(numbers(rowsOfSection(view, "likenesses"))).isEqualTo(numbers(pageLikenesses)));
        claim("every number the page states in its body is somewhere in the tab: the survivors entering the step,"
                        + " the sets and the rows",
                () -> assertThat(numbersIn(viewText.toString())).containsAll(numbersIn(bodyOf(page))));
        claim("the headline's three tiles are the survivors entering the step, the translation sets found and the copies"
                        + " removed, 5, 1 and 0",
                () -> assertThat(view.tiles()).containsExactly("survivors=5", "sets=1", "removed=0"));
        claim("the tab draws no chart, as the page draws none",
                () -> assertThat(view.sections()).noneMatch(NativeViewForTests.Section::chart));
    }

    private static Translations.Member member(long id, String path, String detectedLanguage) {
        return new Translations.Member(new OccurrenceId(id), new OccurrencePath(path), detectedLanguage);
    }

    private static List<List<String>> rowsOfSection(NativeViewForTests.View view, String id) {
        return view.sections().stream().filter(section -> section.id().equals(id)).findFirst().orElseThrow().rows();
    }

    /** Each cell of {@code rows} as the number it states, without its grouping. */
    private static List<List<BigDecimal>> numbers(List<List<String>> rows) {
        List<List<BigDecimal>> numbers = new ArrayList<>();
        for (List<String> row : rows) {
            numbers.add(row.stream().map(cell -> new BigDecimal(cell.replace(",", "")).stripTrailingZeros()).toList());
        }
        return numbers;
    }

    private static Set<String> numbersIn(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher number = A_NUMBER.matcher(text.replace(",", ""));
        while (number.find()) {
            numbers.add(new BigDecimal(number.group()).stripTrailingZeros().toPlainString());
        }
        return numbers;
    }

    /** The text of the page's body, without its markup. */
    private static String bodyOf(String html) {
        String body = html.substring(html.indexOf("<body"));
        return body.replaceAll("<[^>]*>", " ");
    }

    private static List<String> headingsOf(String html) {
        List<String> headings = new ArrayList<>();
        Matcher heading = A_SECOND_HEADING.matcher(html);
        while (heading.find()) {
            headings.add(heading.group(1).replaceAll("<[^>]*>", "").trim());
        }
        return headings;
    }

    private static List<List<String>> rowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(html);
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
