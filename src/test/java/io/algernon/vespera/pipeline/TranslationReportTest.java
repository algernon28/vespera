package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.Translations;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code translations.html} as the translation step renders it (ADR-271 §7): the survivors counted by their
 * detected language, and one row per hundredth of similarity with the likenesses on it and those their paths
 * confirm and refute. The rows and the counts are {@code embedding}'s, taken here from {@link Translations}
 * over five survivors in three detected languages, two of them a pair by their paths.
 */
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
class TranslationReportTest {

    private static final RunId SCORING = new RunId("a-scoring-run-of-this-test");

    private static final String MODEL = "bge-m3";

    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @Test
    @Story("The page the similarity floor is read off")
    @DisplayName("The page counts the survivors by detected language and has a row for each hundredth of similarity")
    void thePageCountsTheLanguagesAndHasARowForEachHundredth() {
        String html = thePage();
        List<List<String>> rows = rowsOf(html);

        claim(
                "the languages table counts the survivors by the code of their detected language, in code order:"
                        + " two English, one Italian and one Chinese; the fifth survivor had no language detected and"
                        + " is on no row",
                () -> assertThat(rows.stream().filter(row -> row.size() == 2).toList())
                        .containsExactly(List.of("en", "2"), List.of("it", "1"), List.of("zh", "1")));
        claim(
                "the similarity table has one row per hundredth, ascending, each with its likenesses, those the"
                        + " paths confirm and those they refute: 0.81 for the Italian and English pair the paths say"
                        + " nothing of, 0.91 for the pair under en/ and zh/, which they confirm",
                () -> assertThat(rows.stream().filter(row -> row.size() == 4).toList())
                        .containsExactly(List.of("0.81", "1", "0", "0"), List.of("0.91", "1", "1", "0")));
        claim(
                "it names the scoring run and the model, and no path of a survivor",
                () -> assertThat(html)
                        .contains(SCORING.value())
                        .contains(MODEL)
                        .doesNotContain("a.html")
                        .doesNotContain("c.pdf"));
    }

    private static String thePage() {
        List<Translations.Member> members = List.of(
                member(1, "en/a.html", "ENGLISH"),
                member(2, "zh/a.html", "CHINESE"),
                member(3, "manuali/c.pdf", "ITALIAN"),
                member(4, "docs/d.pdf", "ENGLISH"),
                member(5, "docs/e.pdf", null));
        List<Translations.Likeness> likenesses = List.of(
                new Translations.Likeness(new OccurrenceId(1), new OccurrenceId(2), 0.91),
                new Translations.Likeness(new OccurrenceId(3), new OccurrenceId(4), 0.81));
        Translations.Resolution resolution =
                Translations.resolve(members, likenesses, OptionalDouble.empty(), List.of());
        return TranslationReport.render(
                resolution, SCORING, MODEL, members.size(), 0, "unset", "unset", false);
    }

    private static Translations.Member member(long id, String path, String detectedLanguage) {
        return new Translations.Member(new OccurrenceId(id), new OccurrencePath(path), detectedLanguage);
    }

    /** The cells of each body row of {@code html}, tags taken out; a header row has no cell and is left out. */
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
