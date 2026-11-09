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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The review list and the survivor list open in the window as shipped drawn from their JSON (ADR-362 section 10), in the
 * shape their writers give it: the count in the headline's tile, and a table with a row for each file in the page's
 * order, a file with no page count shown as the page shows it, by a dash, and a list of nothing with no table.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("751")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheReviewAndSurvivorListsOpenNativelyTest {

    private static final String THE_REVIEW_LIST = "extraction-failures";

    private static final String THE_SURVIVOR_LIST = "extraction-survivors";

    private static final String WRITTEN_AT = "2026-11-08T10:00:00Z";

    /** What the survivor page shows where a file has no page count. */
    private static final String A_DASH = "—";

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The review list and the survivor list open natively, with every row of their pages and a dash where a file has no page count")
    void bothListsOpenNativelyShowingEveryRowOfTheirPages(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory, THE_REVIEW_LIST, "Files stage 2 could not read", "failed", "Files that could not be read",
                "failures", "[{\"id\":\"path\",\"label\":\"Path\",\"kind\":\"path\"},{\"id\":\"reason\",\"label\":\"Why\",\"kind\":\"text\"}]",
                "[[\"/a/first\",\"timeout\"],[\"/b/second\",\"could not be read\"]]", 2);
        write(workingDirectory, THE_SURVIVOR_LIST, "Files stage 2 read and kept", "cameThrough", "Files that came through",
                "survivors", "[{\"id\":\"path\",\"label\":\"Path\",\"kind\":\"path\"},{\"id\":\"pages\",\"label\":\"Pages\",\"kind\":\"count\"},"
                        + "{\"id\":\"words\",\"label\":\"Words\",\"kind\":\"count\"}]",
                "[[\"/a/kept\",4,1900],[\"/b/kept\",null,12]]", 2);

        List<String> tabs;
        String reviewTile;
        String survivorTile;
        List<List<String>> reviewRows;
        List<List<String>> survivorRows;
        try (WindowForTests window = WindowForTests.shown(aState(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", THE_REVIEW_LIST));
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", THE_SURVIVOR_LIST));
            tabs = window.read(WindowForTests::tabIds);
            reviewTile = window.read(root -> textOf(root, "headline-" + THE_REVIEW_LIST + "-failed"));
            survivorTile = window.read(root -> textOf(root, "headline-" + THE_SURVIVOR_LIST + "-cameThrough"));
            reviewRows = window.read(root -> rows(root, THE_REVIEW_LIST, "failures"));
            survivorRows = window.read(root -> rows(root, THE_SURVIVOR_LIST, "survivors"));
        }

        claim("each list opens in a tab of its own under the new id, after the first three",
                () -> assertThat(tabs).containsExactly("live-report", "profile", "inclusions",
                        "analysis-report-" + THE_REVIEW_LIST, "analysis-report-" + THE_SURVIVOR_LIST));
        claim("the review list's tile states the count of files that could not be read", () -> assertThat(reviewTile).isEqualTo("2"));
        claim("the survivor list's tile states the count of files that came through", () -> assertThat(survivorTile).isEqualTo("2"));
        claim("the review list shows each file and its reason, in the page's order", () -> assertThat(reviewRows)
                .containsExactly(List.of("/a/first", "timeout"), List.of("/b/second", "could not be read")));
        claim("the survivor list shows each file's pages and words, a dash where it has no page count as its page has one",
                () -> assertThat(survivorRows)
                        .containsExactly(List.of("/a/kept", "4", "1,900"), List.of("/b/kept", A_DASH, "12")));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A list of nothing opens with its count of zero and no table")
    void aListOfNothingHasNoTable(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory, THE_REVIEW_LIST, "Files stage 2 could not read", "failed", "Files that could not be read",
                "failures", "[{\"id\":\"path\",\"label\":\"Path\",\"kind\":\"path\"},{\"id\":\"reason\",\"label\":\"Why\",\"kind\":\"text\"}]",
                null, 0);

        String tile;
        boolean table;
        try (WindowForTests window = WindowForTests.shown(aState(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", THE_REVIEW_LIST));
            tile = window.read(root -> textOf(root, "headline-" + THE_REVIEW_LIST + "-failed"));
            table = window.read(root -> HeadlessToolkit.nodeWithId(root, "table-" + THE_REVIEW_LIST + "-failures").isPresent());
        }

        claim("the tile says zero", () -> assertThat(tile).isEqualTo("0"));
        claim("and no table is drawn", () -> assertThat(table).isFalse());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The window lists both lists")
    void theWindowListsBothLists() {
        claim("the reports the window lists name both lists", () -> assertThat(Reports.ALL)
                .contains(THE_REVIEW_LIST, THE_SURVIVOR_LIST));
    }

    private static List<List<String>> rows(Parent root, String name, String section) {
        @SuppressWarnings("unchecked")
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + name + "-" + section, TableView.class);
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < table.getItems().size(); row++) {
            List<String> cells = new ArrayList<>();
            for (var column : table.getColumns()) {
                cells.add(String.valueOf(column.getCellData(row)));
            }
            rows.add(cells);
        }
        return rows;
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, Label.class).getText();
    }

    private static void write(Path workingDirectory, String name, String title, String key, String label, String section,
            String columns, String rows, int count) throws IOException {
        String table = rows == null ? "null" : "{\"columns\":" + columns + ",\"rows\":" + rows + "}";
        Files.writeString(workingDirectory.resolve(Reports.jsonOf(name)),
                "{\"format\":1,\"analysisReport\":\"" + name + "\",\"title\":\"" + title + "\",\"writtenAt\":\"" + WRITTEN_AT
                        + "\",\"headline\":[{\"key\":\"" + key + "\",\"label\":\"" + label + "\",\"value\":" + count
                        + ",\"unit\":\"count\"}],\"sections\":[{\"id\":\"" + section + "\",\"title\":\"" + title
                        + "\",\"text\":[\"One paragraph.\"],\"chart\":null,\"table\":" + table + "}]}",
                StandardCharsets.UTF_8);
    }

    /** A state as the control panel is shipped, with the set of the reports it draws natively. */
    private static ControlPanelState aState(Path folder, Path workingDirectory) throws IOException {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }
}
