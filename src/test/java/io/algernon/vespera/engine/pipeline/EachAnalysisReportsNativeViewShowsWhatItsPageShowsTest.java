package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the control panel reads of each analysis report's JSON holds every number and every row its page shows, in the
 * page's order (ADR-362 section 10): the jar writes the page and its JSON over a fixture, the window's reader reads the
 * JSON, and what it reads is compared with the page. One case is added by each report's ticket of step 7; the cases of
 * the two lists are here, and what the window draws of what it reads is held by the tests of the window.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("751")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    /** What the survivor page shows where a file has no page count. */
    private static final String A_DASH_ON_THE_PAGE = "&mdash;";

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("What the window reads of the review list and the survivor list holds every number and row of their pages, in order")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage() throws IOException {
        List<RemovedOccurrence> failures = List.of(
                new RemovedOccurrence(new OccurrenceId(1), "a/first", "timeout"),
                new RemovedOccurrence(new OccurrenceId(2), "b/second", "could not be read"));
        StringWriter reviewPage = new StringWriter();
        StringWriter reviewJson = new StringWriter();
        ReviewListReport.write(reviewPage, reviewJson, failures.size(), Instant.now(), each -> failures.forEach(each));
        List<SurvivorListReport.Row> survivors = List.of(
                new SurvivorListReport.Row("a/kept", 4, 900), new SurvivorListReport.Row("b/kept", null, 12));
        StringWriter survivorPage = new StringWriter();
        StringWriter survivorJson = new StringWriter();
        SurvivorListReport.write(
                survivorPage, survivorJson, survivors.size(), Instant.now(), each -> survivors.forEach(each));

        AnalysisReportFile.Report review = AnalysisReportFile.read(ReviewListReport.NAME, reviewJson.toString());
        AnalysisReportFile.Report survivor = AnalysisReportFile.read(SurvivorListReport.NAME, survivorJson.toString());

        claim("the review list's headline is the count its page states first, and its page says it",
                () -> {
                    assertThat(review.headline()).hasSize(1);
                    assertThat(review.headline().get(0).value())
                            .isEqualByComparingTo(BigDecimal.valueOf(failures.size()));
                    assertThat(reviewPage.toString()).contains("Files that could not be read: " + failures.size());
                });
        claim("its one table holds each file and its reason, each as the page shows it and in the page's order",
                () -> {
                    List<List<Object>> rows = review.sections().get(0).table().rows();
                    assertThat(rows).hasSize(failures.size());
                    int from = 0;
                    for (List<Object> row : rows) {
                        for (Object cell : row) {
                            int at = reviewPage.toString().indexOf(cell.toString(), from);
                            assertThat(at).as("the page shows %s after the cell before it", cell)
                                    .isGreaterThanOrEqualTo(from);
                            from = at;
                        }
                    }
                });
        claim("the survivor list's headline is the count of files that came through, as its page states it",
                () -> {
                    assertThat(survivor.headline().get(0).value())
                            .isEqualByComparingTo(BigDecimal.valueOf(survivors.size()));
                    assertThat(survivorPage.toString()).contains("Files that came through: " + survivors.size());
                });
        claim("its table holds each file's path, pages and words in the page's order, null where the page holds a dash",
                () -> {
                    List<List<Object>> rows = survivor.sections().get(0).table().rows();
                    List<String> read = new ArrayList<>();
                    rows.forEach(row -> read.add(row.get(0) + "|" + row.get(1) + "|" + row.get(2)));
                    assertThat(read).containsExactly("a/kept|4|900", "b/kept|null|12");
                    assertThat(survivorPage.toString()).contains(A_DASH_ON_THE_PAGE);
                    assertThat(survivorPage.toString().indexOf("a/kept"))
                            .isLessThan(survivorPage.toString().indexOf("b/kept"));
                });
    }
}
