package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javafx.event.Event;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A table of an analysis report's native view selects many rows and copies the rows selected as lines of
 * tab-separated text, with Ctrl+C or Copy in its context menu, which is disabled while no row is selected (ADR-364 §8).
 *
 * <p>The view is drawn on the Headless platform as every window test does (ADR-342 §6); the key pressed is the
 * platform's shortcut, Ctrl but on a Mac.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("749")
@Link(name = "ADR-364", url = Adr.EVERY_TEXT_OF_THE_WINDOW_IS_COPYABLE, type = "adr")
class ATableOfANativeViewCopiesItsRowsTest {

    private static final String THE_REPORT = "format-mix";

    private static final String THE_SECTION = "kinds";

    /** A file of the machine, as a {@code path} cell names it and its link draws it. */
    private static final String A_FILE = new File(System.getProperty("java.io.tmpdir"), "lead.txt").getAbsolutePath();

    /** What the clipboard holds before each copy, so that a copy that did not happen is seen. */
    private static final String UNTOUCHED = "untouched";

    /** The five rows, of a text, a count and a file; the fourth's text holds a tab and a line's end. */
    private static final List<List<Object>> ROWS = List.of(
            List.of("docx", new BigDecimal(1200), ""),
            List.of("pdf", new BigDecimal(3400), A_FILE),
            List.of("txt", new BigDecimal(5), ""),
            List.of("a\tb\r\nc", new BigDecimal(7), A_FILE),
            List.of("png", new BigDecimal(9), ""));

    /** The second and fourth rows, by their indexes, selected the fourth first. */
    private static final int SECOND = 1;

    private static final int FOURTH = 3;

    @Test
    @Story("The operator copies what the window shows")
    @DisplayName("The rows selected in a report's table are copied as tab-separated lines in the table's order, and Copy is disabled with none selected")
    void theRowsSelectedAreCopiedAsTabSeparatedLines() throws Exception {
        record Copied(boolean disabledWithNone, String afterKeyWithNone, boolean disabledWithOne, String afterCopyOfOne,
                String afterKeyWithTwo) {
        }
        Copied copied = HeadlessToolkit.onTheToolkit(() -> {
            VBox content = AnalysisReportView.of(THE_REPORT, aReport(), "format-mix.html", name -> {
            }, address -> {
            });
            @SuppressWarnings("unchecked")
            TableView<List<Object>> table = (TableView<List<Object>>) HeadlessToolkit
                    .nodeWithId(content, "table-" + THE_REPORT + "-" + THE_SECTION).orElseThrow();
            MenuItem copy = table.getContextMenu().getItems().stream()
                    .filter(item -> "Copy".equals(item.getText())).findFirst().orElseThrow();

            boolean disabledWithNone = copy.isDisable();
            untouch();
            pressTheCopyKey(table);
            String afterKeyWithNone = Clipboard.getSystemClipboard().getString();

            table.getSelectionModel().select(SECOND);
            boolean disabledWithOne = copy.isDisable();
            untouch();
            copy.fire();
            String afterCopyOfOne = Clipboard.getSystemClipboard().getString();

            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(FOURTH);
            table.getSelectionModel().select(SECOND);
            untouch();
            pressTheCopyKey(table);
            String afterKeyWithTwo = Clipboard.getSystemClipboard().getString();
            return new Copied(disabledWithNone, afterKeyWithNone, disabledWithOne, afterCopyOfOne, afterKeyWithTwo);
        });

        String second = "pdf\t3,400\t" + A_FILE;
        String fourth = "a b c\t7\t" + A_FILE;
        claim("with no row selected, Copy in the table's context menu is disabled",
                () -> assertThat(copied.disabledWithNone()).isTrue());
        claim("and Ctrl+C leaves the clipboard as it was",
                () -> assertThat(copied.afterKeyWithNone()).isEqualTo(UNTOUCHED));
        claim("with the second row selected, Copy is enabled", () -> assertThat(copied.disabledWithOne()).isFalse());
        claim("and puts that row on the clipboard as one line, its cells as drawn joined by tabs, the file as its link's text",
                () -> assertThat(copied.afterCopyOfOne()).isEqualTo(second));
        claim("with the fourth and then the second selected, Ctrl+C copies both in the table's order, a line each with no"
                        + " header line, a tab and a line's end inside a cell each written as one space",
                () -> assertThat(copied.afterKeyWithTwo()).isEqualTo(second + "\n" + fourth));
    }

    private static void untouch() {
        ClipboardContent content = new ClipboardContent();
        content.putString(UNTOUCHED);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void pressTheCopyKey(TableView<?> table) {
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        Event.fireEvent(table, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, !mac, false, mac));
    }

    /** A report of one section, its table of a text, a count and a {@code path} column holding {@link #ROWS}. */
    private static AnalysisReportFile.Report aReport() {
        AnalysisReportFile.Table table = new AnalysisReportFile.Table(List.of(
                new AnalysisReportFile.Column("kind", "Kind", "text"),
                new AnalysisReportFile.Column("files", "Files", "count"),
                new AnalysisReportFile.Column("where", "Where", "path")), ROWS);
        return new AnalysisReportFile.Report(1, THE_REPORT, "Format mix", Instant.parse("2026-11-08T10:00:00Z"),
                List.of(), List.of(new AnalysisReportFile.Section(THE_SECTION, "By kind", List.of(), null, table)));
    }
}
