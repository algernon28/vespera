package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.pipeline.JarWrittenAnalysisReports;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The native view of each analysis report shows every number and row its page shows, its charts and its cells as its
 * sections name them (ADR-362 section 10): the jar writes the report over a fixture, the window opens it, and the view is
 * read back. A case is added to the method by the ticket of each report; this class holds the one of the relevance
 * labelling page. The window as shipped draws the reports of {@link AnalysisReportFile#FORMATS}, which the test gives
 * its state, and runs on the Headless platform (ADR-342 section 6).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("756")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    private static final String RELEVANCE_LABELLING = "relevance-labelling";

    /** The word the fixture puts in the path of the one document to judge. */
    private static final String MARKER = "ZXQ-private-path-word";

    /** The bands of the fixture, each with how many documents it holds and how many were asked about. */
    private static final int BANDS = 5;

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The relevance labelling report's view holds every number and row of its page, its two charts and its tables, in the page's order")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        JarWrittenAnalysisReports.relevanceLabelling(workingDirectory);

        List<String> tab;
        List<String> tiles;
        List<String> sections;
        List<List<String>> bands;
        List<List<String>> answers;
        List<List<String>> cuts;
        List<List<String>> documents;
        List<List<String>> spreadChart;
        List<List<String>> answersChart;
        List<String> paragraphs;
        boolean cutsChart;
        boolean documentsChart;
        try (WindowForTests window = WindowForTests.shown(stateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", RELEVANCE_LABELLING));
            tab = window.read(WindowForTests::tabIds);
            tiles = window.read(root -> tilesOf(root));
            sections = window.read(root -> everySectionOf(root));
            bands = window.read(root -> rowsOf(root, "score-spread"));
            answers = window.read(root -> rowsOf(root, "what-the-answers-say-so-far"));
            cuts = window.read(root -> rowsOf(root, "what-each-cut-would-cost"));
            documents = window.read(root -> rowsOf(root, "the-documents-to-judge"));
            spreadChart = window.read(root -> pointsOf(root, "score-spread"));
            answersChart = window.read(root -> pointsOf(root, "what-the-answers-say-so-far"));
            paragraphs = window.read(root -> paragraphsOf(root, "score-spread"));
            cutsChart = window.read(root -> chartIn(root, "what-each-cut-would-cost"));
            documentsChart = window.read(root -> chartIn(root, "the-documents-to-judge"));
        }

        claim("the report opens in a tab of its own, by the analysis report's id",
                () -> assertThat(tab).contains("analysis-report-" + RELEVANCE_LABELLING));
        claim("the headline's six numbers are tiles in the page's order: 200 documents scored, the lowest and highest score, "
                        + BANDS + " bands, the 1 document put to a person and the 3 judged so far",
                () -> assertThat(tiles).containsExactly("scored=200", "lowest=0.8", "highest=0.9", "bands=" + BANDS,
                        "sampled=1", "judged=3"));
        claim("the sections are the page's, in its order, the threshold's section being absent as no floor is ignored",
                () -> assertThat(sections).containsExactly("score-spread", "if-the-bands-look-alike",
                        "what-the-answers-say-so-far", "what-each-cut-would-cost", "the-documents-to-judge"));
        claim("the spread section states its two paragraphs, the first of the scored documents and their bands",
                () -> assertThat(paragraphs).hasSize(2).first().asString()
                        .isEqualTo("200 document(s) were scored, between 0.80 and 0.90, divided into 5 equal bands."));
        claim("a row for each of the " + BANDS + " bands in the page's order, with its bounds, documents, those asked about and its shortfall",
                () -> assertThat(bands).containsExactly(
                        List.of("Band 1", "0.8", "0.82", "40", "12", "0"),
                        List.of("Band 2", "0.82", "0.84", "44", "12", "1"),
                        List.of("Band 3", "0.84", "0.86", "39", "12", "2"),
                        List.of("Band 4", "0.86", "0.88", "41", "12", "0"),
                        List.of("Band 5", "0.88", "0.9", "36", "12", "3")));
        claim("the spread's bars chart draws the documents and those asked about for each band",
                () -> assertThat(spreadChart).containsExactly(
                        List.of("Documents", "Band 1=40", "Band 2=44", "Band 3=39", "Band 4=41", "Band 5=36"),
                        List.of("Being asked about", "Band 1=12", "Band 2=12", "Band 3=12", "Band 4=12", "Band 5=12")));
        claim("a row for each band judged: 1 of 1 relevant in the first, and 2 judged with 1 relevant in the second",
                () -> assertThat(answers).containsExactly(
                        List.of("Band 1", "1", "1"),
                        List.of("Band 2", "2", "1")));
        claim("and its bars chart draws the judged and the relevant for those two bands",
                () -> assertThat(answersChart).containsExactly(
                        List.of("Judged", "Band 1=1", "Band 2=2"),
                        List.of("Judged relevant", "Band 1=1", "Band 2=1")));
        claim("a row for the one candidate cut with its seven numbers, and no chart for the cuts or for the documents",
                () -> {
                    assertThat(cuts).containsExactly(List.of("0.84", "120", "3", "2", "80", "1", "0"));
                    assertThat(cutsChart).isFalse();
                    assertThat(documentsChart).isFalse();
                });
        claim("a row for the one document to judge with its band, path, score, closest seed and opening",
                () -> assertThat(documents).containsExactly(List.of("Band 2", "reports/" + MARKER + ".pdf", "0.83",
                        "seeds/exemplar.pdf", "The quarterly figures were tabled on the third.")));
    }

    private static ControlPanelState stateOver(Path folder, Path workingDirectory) throws IOException {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"),
                AnalysisReportFile.FORMATS.keySet());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }

    private static List<String> tilesOf(Parent root) {
        List<String> tiles = new ArrayList<>();
        String prefix = "headline-" + RELEVANCE_LABELLING + "-";
        for (Node each : HeadlessToolkit.everyNodeUnder(
                WindowForTests.node(root, "headline-" + RELEVANCE_LABELLING, FlowPane.class))) {
            if (each instanceof Label label && label.getId() != null && label.getId().startsWith(prefix)) {
                tiles.add(label.getId().substring(prefix.length()) + "=" + label.getText());
            }
        }
        return tiles;
    }

    private static List<String> everySectionOf(Parent root) {
        List<String> ids = new ArrayList<>();
        String prefix = "section-" + RELEVANCE_LABELLING + "-";
        for (Node each : HeadlessToolkit.everyNodeUnder(root)) {
            if (each instanceof VBox box && box.getId() != null && box.getId().startsWith(prefix)) {
                ids.add(box.getId().substring(prefix.length()));
            }
        }
        return ids;
    }

    /** The paragraphs of a section, after its title. */
    private static List<String> paragraphsOf(Parent root, String section) {
        VBox box = WindowForTests.node(root, "section-" + RELEVANCE_LABELLING + "-" + section, VBox.class);
        return box.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText)
                .skip(1).toList();
    }

    private static boolean chartIn(Parent root, String section) {
        return HeadlessToolkit.nodeWithId(root, "chart-" + RELEVANCE_LABELLING + "-" + section).isPresent();
    }

    private static List<List<String>> pointsOf(Parent root, String section) {
        @SuppressWarnings("unchecked")
        BarChart<String, Number> chart = WindowForTests.node(root, "chart-" + RELEVANCE_LABELLING + "-" + section,
                BarChart.class);
        List<List<String>> series = new ArrayList<>();
        for (XYChart.Series<String, Number> each : chart.getData()) {
            List<String> points = new ArrayList<>();
            points.add(each.getName());
            each.getData().forEach(point -> points.add(point.getXValue() + "=" + point.getYValue().intValue()));
            series.add(List.copyOf(points));
        }
        return series;
    }

    private static List<List<String>> rowsOf(Parent root, String section) {
        @SuppressWarnings("unchecked")
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + RELEVANCE_LABELLING + "-" + section,
                TableView.class);
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < table.getItems().size(); row++) {
            List<String> cells = new ArrayList<>();
            for (int at = 0; at < table.getColumns().size(); at++) {
                cells.add(String.valueOf(table.getColumns().get(at).getCellData(row)));
            }
            rows.add(List.copyOf(cells));
        }
        return rows;
    }
}
