package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A profile row's reports read their titles (ADR-377 §5): each report of the key is a link reading the report's title,
 * {@code <prefix><key>.report-<name>}, which opens the report's native view (ADR-362 §10); a report whose JSON the
 * working directory does not hold is its title in grey, not written yet; and the twelve titles are {@code
 * Reports.TITLES}, which the jar's pages carry too. Each window is shown on the Headless platform, the Profile tab
 * selected.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("806")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class ARowsReportsReadTheirTitlesTest {

    /** A key whose row links three reports. */
    private static final ProfileKey THREE_REPORTS = ProfileKey.RELEVANCE_SCORE_FLOOR;

    /** A key whose row links one report, {@link #ITS_REPORT}. */
    private static final ProfileKey ONE_REPORT = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** The report {@link #ONE_REPORT}'s row links. */
    private static final String ITS_REPORT = "document-frequency";

    /** The Profile tab's prefix of every id of its rows. */
    private static final String PROFILE = "profile-";

    /** The twelve analysis reports' titles, by name, as the record gives them. */
    private static final Map<String, String> THE_RECORDS_TITLES = titles(
            "format-mix", "What the files turned out to be",
            "extraction-failures", "Files whose text could not be read",
            "extraction-survivors", "Files whose text was read and kept",
            "confidence-distribution", "Text quality distribution",
            "document-frequency", "Repeated text across documents",
            "word-count-distribution", "Word count distribution",
            "seed-corpus-comparison", "Your documents compared with your seed documents",
            "translations", "Copies in another language",
            "cluster-sizes", "Group sizes",
            "relevance-labelling", "Choosing the relevance cut-off",
            "arrangement", "Grouping to approve",
            "cluster-page-sizes", "Group page sizes");

    private static final String WRITTEN_AT = "2026-11-08T10:00:00Z";

    @Test
    @Story("A row's reports read their titles")
    @DisplayName("Each report a row links reads the report's title, in the order the row lists them, and no text of the row ends .html")
    void eachLinkReadsItsReportsTitle(@TempDir Path folder) throws Exception {
        String prefix = PROFILE + THREE_REPORTS.key();
        List<String> reports = List.of("relevance-labelling", "cluster-sizes", "seed-corpus-comparison");
        Path working = Files.createDirectories(folder.resolve("working"));
        for (String report : reports) {
            writeJson(working, report);
        }
        Map<String, String> links = new LinkedHashMap<>();
        List<String> texts = new ArrayList<>();
        try (WindowForTests window = windowOver(folder, working)) {
            window.act(root -> WindowForTests.node(root, prefix + ".more", ToggleButton.class).fire());
            window.act(root -> {
                for (Node each : HeadlessToolkit.everyNodeUnder(
                        WindowForTests.node(root, prefix + ".reports", Node.class))) {
                    if (each instanceof Hyperlink link) {
                        links.put(link.getId(), link.getText());
                    }
                }
                for (Node each : HeadlessToolkit.everyNodeUnder(
                        WindowForTests.node(root, PROFILE + "row-" + THREE_REPORTS.key(), Node.class))) {
                    textOf(each).ifPresent(texts::add);
                }
            });
        }

        claim("the row's reports are links, by their ids, reading the reports' titles, in the order the row lists them",
                () -> assertThat(links).containsExactly(
                        Map.entry(prefix + ".report-relevance-labelling", "Choosing the relevance cut-off"),
                        Map.entry(prefix + ".report-cluster-sizes", "Group sizes"),
                        Map.entry(prefix + ".report-seed-corpus-comparison",
                                "Your documents compared with your seed documents")));
        claim("that order is the one the row lists the key's reports in",
                () -> assertThat(Reports.OF_A_KEY.get(THREE_REPORTS.key())).containsExactlyElementsOf(reports));
        claim("no text of the row ends .html",
                () -> assertThat(texts).isNotEmpty().noneMatch(text -> text.strip().endsWith(".html")));
    }

    @Test
    @Story("A row's reports read their titles")
    @DisplayName("A row's link opens its report's native view in the window, and hands nothing to the system")
    void aLinkOpensItsReportsNativeView(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, ITS_REPORT);
        Optional<WindowForTests.ReportTab> opened;
        String title;
        List<String> viewers = new ArrayList<>();
        List<URI> handed;
        try (WindowForTests window = windowOver(folder, working)) {
            window.act(root -> WindowForTests.pressRowReportLink(root, PROFILE, ONE_REPORT, ITS_REPORT));
            opened = window.read(root -> WindowForTests.reportTab(root, ITS_REPORT));
            title = window.read(root -> HeadlessToolkit.tabWithId(root, "analysis-report-" + ITS_REPORT)
                    .map(Tab::getText).orElse(null));
            window.act(root -> HeadlessToolkit.tabWithId(root, "analysis-report-" + ITS_REPORT)
                    .map(Tab::getContent)
                    .ifPresent(content -> HeadlessToolkit.everyNodeUnder(content).stream()
                            .map(node -> node.getClass().getName())
                            .filter(name -> name.endsWith("WebView"))
                            .forEach(viewers::add)));
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("the report's tab, analysis-report-" + ITS_REPORT + ", is open and selected",
                () -> assertThat(opened).hasValueSatisfying(tab -> assertThat(tab.selected()).isTrue()));
        claim("it is titled by the report's title", () -> assertThat(title).isEqualTo("Repeated text across documents"));
        claim("it holds no web viewer", () -> assertThat(viewers).isEmpty());
        claim("and nothing is handed to the system", () -> assertThat(handed).isEmpty());
    }

    @Test
    @Story("A row's reports read their titles")
    @DisplayName("A report not written yet is its title in grey, saying so, and no link")
    void aReportNotWrittenIsItsTitleGrey(@TempDir Path folder) throws Exception {
        String prefix = PROFILE + ONE_REPORT.key();
        Path working = Files.createDirectories(folder.resolve("working"));
        Node[] shown = new Node[1];
        List<Hyperlink> links = new ArrayList<>();
        try (WindowForTests window = windowOver(folder, working)) {
            window.act(root -> {
                shown[0] = WindowForTests.node(root, prefix + ".report-" + ITS_REPORT, Node.class);
                links.addAll(WindowForTests.every(root, PROFILE + "row-" + ONE_REPORT.key(), Hyperlink.class).stream()
                        .filter(link -> link.getId() != null && link.getId().contains(".report-")).toList());
            });
        }

        claim("the report is a one-line selectable text, not a link",
                () -> assertThat(shown[0]).isInstanceOf(SelectableText.Line.class));
        claim("in the grey style",
                () -> assertThat(shown[0].getStyleClass()).contains("grey"));
        claim("reading the report's title and that it is not written yet",
                () -> assertThat(((TextInputControl) shown[0]).getText())
                        .isEqualTo("Repeated text across documents (not written yet)"));
        claim("and the row holds no link to a report", () -> assertThat(links).isEmpty());
    }

    @Test
    @Story("A row's reports read their titles")
    @DisplayName("The twelve reports' titles are the ones the record gives, and every report a row links has one")
    void theTwelveTitlesAreTheRecords() {
        claim("the titles are the twelve the record gives, each for its report, in the order the reports are listed",
                () -> assertThat(Reports.TITLES).containsExactlyEntriesOf(THE_RECORDS_TITLES));
        claim("they title every report the window lists",
                () -> assertThat(Reports.TITLES.keySet()).containsExactlyElementsOf(Reports.ALL));
        claim("and every report a row links is among them",
                () -> Reports.OF_A_KEY.values().forEach(reports -> assertThat(Reports.TITLES).containsKeys(
                        reports.toArray(String[]::new))));
    }

    // ---- the window and its files

    /** The window over {@code working}, with a corpus root under {@code folder}, the Profile tab selected. */
    private static WindowForTests windowOver(Path folder, Path working) throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        WindowForTests window = WindowForTests.shown(state);
        try {
            window.act(root -> {
                Tab profileTab = HeadlessToolkit.tabWithId(root, "profile").orElseThrow();
                profileTab.getTabPane().getSelectionModel().select(profileTab);
            });
            return window;
        } catch (Exception | Error failed) {
            window.close();
            throw failed;
        }
    }

    /** Writes {@code name}'s JSON as the jar writes one (ADR-362 §9), titled by the record's title and holding one section of text. */
    private static void writeJson(Path working, String name) throws IOException {
        String json = "{\"format\":" + AnalysisReportFile.FORMATS.get(name) + ",\"analysisReport\":\"" + name
                + "\",\"title\":\"" + THE_RECORDS_TITLES.get(name) + "\",\"writtenAt\":\"" + WRITTEN_AT
                + "\",\"headline\":[],\"sections\":[{\"id\":\"about\",\"title\":\"About\",\"text\":[\"One paragraph.\"],"
                + "\"chart\":null,\"table\":null}]}";
        Files.writeString(working.resolve(Reports.jsonOf(name)), json, StandardCharsets.UTF_8);
    }

    private static Optional<String> textOf(Node node) {
        if (node instanceof TextInputControl input) {
            return Optional.ofNullable(input.getText());
        }
        if (node instanceof Labeled labeled) {
            return Optional.ofNullable(labeled.getText());
        }
        return Optional.empty();
    }

    private static Map<String, String> titles(String... namesAndTitles) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (int i = 0; i < namesAndTitles.length; i += 2) {
            titles.put(namesAndTitles[i], namesAndTitles[i + 1]);
        }
        return titles;
    }
}
