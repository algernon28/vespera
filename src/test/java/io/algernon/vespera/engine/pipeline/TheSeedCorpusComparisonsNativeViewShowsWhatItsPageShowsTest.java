package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.ui.TheWindowsViewOfAnAnalysisReport;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Each analysis report's native view shows what its page shows (ADR-362 section 10): the jar writes the report over a
 * fixture, the window opens it, and every number and row of the page is in the view, in the page's order. A case is
 * added to the test by the ticket of each report's native view; this one holds the seed/corpus comparison's, whose page
 * is a paragraph of counts and a list of statements.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Issue("753")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheSeedCorpusComparisonsNativeViewShowsWhatItsPageShowsTest {

    private static final String SEED_CORPUS_COMPARISON = SeedCorpusComparisonReport.NAME;

    /** The page's paragraph of counts: the three numbers the headline's tiles hold. */
    private static final Pattern THE_PAGES_COUNTS = Pattern.compile(
            "Measured over (\\d+) usable, measured seed document\\(s\\) and (\\d+) surviving corpus document\\(s\\)\\. "
                    + "(\\d+) seed document\\(s\\) carried no measurement");

    private static final Pattern A_LIST_ITEM = Pattern.compile("<li>(.*?)</li>");

    /** The sections of the report, in the page's order: the paragraph of counts, then a table each. */
    private static final List<String> THE_SECTIONS = List.of("population", "language", "provenance", "spreads");

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The view of each analysis report holds every number and row of its page, in the page's order")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path scratch) throws Exception {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus.txt"), "another corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());

        String html = Files.readString(workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".html"), StandardCharsets.UTF_8);
        TheWindowsViewOfAnAnalysisReport.View view =
                TheWindowsViewOfAnAnalysisReport.of(scratch, workingDirectory, SEED_CORPUS_COMPARISON);

        Matcher counts = THE_PAGES_COUNTS.matcher(html);
        claim("the page states its three counts", () -> assertThat(counts.find()).isTrue());
        claim("the tab is titled by the page's title", () -> assertThat(html).contains("<title>" + view.tab() + "</title>"));
        claim("the headline's three tiles are the page's three counts, in the page's order, grouped by thousands",
                () -> assertThat(List.copyOf(view.tiles().values())).containsExactly(
                        grouped(counts.group(1)), grouped(counts.group(2)), grouped(counts.group(3))));
        claim("the view holds the report's four sections in the page's order, and no viewer lies in the window",
                () -> {
                    assertThat(view.sections().stream().map(TheWindowsViewOfAnAnalysisReport.SectionView::id).toList())
                            .isEqualTo(THE_SECTIONS);
                    assertThat(view.viewer()).isFalse();
                });
        claim("the first section holds the page's paragraph of counts whole, and draws no chart",
                () -> {
                    assertThat(view.sections().get(0).paragraphs())
                            .containsExactly(unescaped(html.substring(html.indexOf("Measured over"), html.indexOf("</p>"))));
                    view.sections().forEach(each -> assertThat(each.chart()).isNull());
                });
        List<String> statementsOfTheView = new ArrayList<>();
        for (TheWindowsViewOfAnAnalysisReport.SectionView section : view.sections()) {
            int statement = section.columns().indexOf("Statement");
            section.rows().forEach(row -> statementsOfTheView.add(row.get(statement)));
        }
        claim("the statement of each row of the tables is a statement of the page, in the page's order",
                () -> assertThat(statementsOfTheView).isEqualTo(theStatementsOfThePage(html)).isNotEmpty());
    }

    private static String grouped(String count) {
        return String.format(java.util.Locale.ROOT, "%,d", Long.parseLong(count));
    }

    private static List<String> theStatementsOfThePage(String html) {
        List<String> statements = new ArrayList<>();
        Matcher item = A_LIST_ITEM.matcher(html);
        while (item.find()) {
            statements.add(unescaped(item.group(1)));
        }
        return statements;
    }

    private static String unescaped(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
