package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What the progress page shows of the step removing occurrences (ADR-268 sections 1 to 5), reached through a whole
 * invocation over a corpus of which extraction removes two of five: one the converter refuses, one whose only text
 * is a page header and a page footer. Stage 4's gate is left shut, so extraction is the last step to judge and is
 * the outcome step of the last write.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-268", url = Adr.THE_PROGRESS_PAGE_SHOWS_WHAT_IS_REMOVED_AND_DRAWS_TO_THE_DATA_VIZ_METHOD, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ProgressPageOutcomesInvocationTest {

    private static final String PAGE = "progress.html";

    private static final String STEPS_PAGE = "progress-steps.html";

    private static final String SAMPLES_PAGE = "progress-samples.html";

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final List<String> READABLE = List.of(MARKER + "-one.txt", MARKER + "-two.txt", MARKER + "-three.txt");

    private static final String REFUSED = SeedScriptedExtractionBeans.REFUSED_CONVERSION;

    private static final String HEADERS_AND_FOOTERS_ONLY = SeedScriptedExtractionBeans.HEADERS_AND_FOOTERS_ONLY;

    /** Every occurrence of the corpus entered extraction: byte-level reduction removed none. */
    private static final int ENTERED = 5;

    /** What survives extraction: the three readable files. */
    private static final int SURVIVORS = 3;

    /** A tile: its key, label, value and, where it has one, detail (ADR-268 section 5). */
    private static final Pattern TILE = Pattern.compile("<div class=\"tile\" data-tile=\"([a-z-]+)\">"
            + "<span class=\"label\">([^<]*)</span><span class=\"value\">([^<]*)</span>"
            + "(?:<span class=\"detail\">([^<]*)</span>)?</div>");

    private static final Pattern A_KINDS_RATE =
            Pattern.compile("[0-9][0-9,]*\\.[0-9] / min over the last [0-9]+:[0-5][0-9]:[0-5][0-9]|rate not measured yet");

    private static final Pattern END_LABEL = Pattern.compile("<text class=\"end-label\"[^>]*>([^<]*)</text>");

    private static final Pattern A_ROW = Pattern.compile("<tr>(<td.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>([^<]*)</td>");

    /** The eight blocking kinds in {@code VerdictKind}'s order, as ADR-268 section 1 writes them. */
    private static final List<String> BLOCKING_KINDS = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope",
            "extraction-failed", "degenerate-output", "redundant-with", "below-threshold");

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndInterval(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.progress-interval", () -> "100ms");
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @BeforeEach
    @AfterEach
    void aProfileThatLoads() {
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("The page shows what the step removing occurrences has removed, by kind, and how many survive")
    @DisplayName("After extraction removed one file the converter refused and one of page headers alone, the last write shows three survivors of five, one extraction-failed and one degenerate-output, charts them, and tables them")
    void theLastWriteShowsWhatExtractionRemovedByKind(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusExtractionRemovesTwoOf(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        String page = thePage(PAGE);
        List<String[]> tiles = tiles(page);
        claim(
                "the last write's tiles are the four that are always there, then the survivors and one tile for each"
                        + " kind extraction records, in that order, and no tile of a kind another step records",
                () -> assertThat(tiles).extracting(tile -> tile[0]).containsExactly(
                        "steps", "step", "elapsed", "database", "survivors", "extraction-failed", "degenerate-output"));
        claim(
                "the survivors are the three readable files, of the five that were survivors when extraction started",
                () -> assertThat(tile(tiles, "survivors")).containsExactly(
                        "survivors", "Survivors", String.valueOf(SURVIVORS), "of " + ENTERED + " when extraction started"));
        claim(
                "extraction recorded one extraction-failed, the file the converter refused, and one degenerate-output,"
                        + " the file of page headers and footers alone, each with its rate or a word that it has none",
                () -> {
                    assertThat(tile(tiles, "extraction-failed")).startsWith("extraction-failed", "extraction-failed", "1");
                    assertThat(tile(tiles, "degenerate-output")).startsWith("degenerate-output", "degenerate-output", "1");
                    assertThat(tile(tiles, "extraction-failed")[3]).matches(A_KINDS_RATE);
                    assertThat(tile(tiles, "degenerate-output")[3]).matches(A_KINDS_RATE);
                });
        String verdicts = between(page, "data-chart=\"verdicts\"");
        claim(
                "the page draws a chart of extraction's verdicts, titled with the step, ending at one of each kind",
                () -> {
                    assertThat(verdicts).contains("<title>Verdicts recorded by extraction over time</title>");
                    assertThat(String.join(", ", all(END_LABEL, verdicts)))
                            .contains("extraction-failed 1")
                            .contains("degenerate-output 1");
                });
        List<List<String>> rows = rows(thePage(SAMPLES_PAGE));
        claim("the table of samples has a row for each sample", () -> assertThat(rows).isNotEmpty());
        List<String> expected = new ArrayList<>(List.of("extraction", String.valueOf(SURVIVORS)));
        for (String kind : BLOCKING_KINDS) {
            expected.add(kind.equals("extraction-failed") || kind.equals("degenerate-output") ? "1" : "0");
        }
        claim(
                "and its last row, the last write's sample, names extraction as the outcome step, its three survivors"
                        + " and the count of each of the eight kinds in the order VerdictKind declares them",
                () -> assertThat(rows.getLast()).hasSize(16).endsWith(expected.toArray(String[]::new)));
    }

    @Test
    @Story("The page can be sent to anyone: it names no path and no document")
    @DisplayName("After an invocation whose extraction removed two files, no name of the corpus, the converter's message, the root, the seeds or the working directory is on any of the three pages")
    void theOutcomePagesNameNoPathAndNoTextOfADocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusExtractionRemovesTwoOf(root, seeds);

        cli.run("run", root.toString());

        for (String name : List.of(PAGE, STEPS_PAGE, SAMPLES_PAGE)) {
            String page = thePage(name);
            for (String forbidden : List.of(
                    MARKER,
                    REFUSED,
                    HEADERS_AND_FOOTERS_ONLY,
                    SeedScriptedExtractionBeans.REFUSAL_MESSAGE,
                    root.toString(),
                    seeds.toString(),
                    workingDirectory.toString())) {
                claim(
                        name + " does not contain '" + forbidden + "', in any letter case",
                        () -> assertThat(page).doesNotContainIgnoringCase(forbidden));
            }
        }
    }

    /** Five occurrences: three readable, one the converter refuses, one of page headers and footers alone. */
    private void aCorpusExtractionRemovesTwoOf(Path root, Path seeds) throws IOException {
        for (String name : READABLE) {
            Files.writeString(root.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(root.resolve(REFUSED), "stands in for a container the converter cannot open, " + MARKER);
        Files.writeString(root.resolve(HEADERS_AND_FOOTERS_ONLY), "converts to a page header and footer alone, " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
        // no boilerplate floor: stage 4's gate is shut, so no step after extraction judges anything
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .build());
    }

    private static String thePage(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim("the invocation left " + name + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private static List<String[]> tiles(String page) {
        List<String[]> tiles = new ArrayList<>();
        Matcher tile = TILE.matcher(page);
        while (tile.find()) {
            tiles.add(new String[] {tile.group(1), tile.group(2), tile.group(3), tile.group(4) == null ? "" : tile.group(4)});
        }
        return tiles;
    }

    private static String[] tile(List<String[]> tiles, String key) {
        return tiles.stream().filter(tile -> tile[0].equals(key)).findFirst().orElse(new String[] {key, "", "", ""});
    }

    private static List<String> all(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<List<String>> rows(String page) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(page);
        while (row.find()) {
            rows.add(all(A_CELL, row.group(1)));
        }
        return rows;
    }

    private static String between(String page, String opening) {
        int from = page.indexOf(opening);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</svg>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }
}
