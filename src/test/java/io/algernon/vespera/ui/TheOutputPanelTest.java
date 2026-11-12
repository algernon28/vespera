package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The output panel (ADR-364 sections 1 and 7): copyable text of the watched invocation's whole output, the child's
 * held lines until its report names an output file, then that file, growing a whole line at a time; and a view that
 * follows new lines only while it is at the bottom and nothing in it is selected.
 */
@Epic("Control panel")
@Feature("The output panel")
@Issue("776")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class TheOutputPanelTest {

    /** An invocation id of ADR-362's form, naming an output file. */
    private static final String AN_ID = "20261110T120000Z";

    /** How many lines the child held before the panel first looked, past which some were let go unseen. */
    private static final int LINES_HELD = 3;

    /** How many lines are shown before the following and holding are tried: more than the panel's height shows. */
    private static final int MORE_LINES_THAN_THE_PANEL_SHOWS = 200;

    /** How many lines arrive while the view is held or followed. */
    private static final int FIFTY_LINES = 50;

    @TempDir
    Path workingDirectory;

    @BeforeAll
    static void toolkit() throws Exception {
        HeadlessToolkit.start();
    }

    @Test
    @Story("The output panel shows the whole output")
    @DisplayName("The panel shows the child's lines, says how many it never showed, then its report's output file whole, growing a whole line at a time; an id not of the report's form names no file")
    void itShowsTheChildsLinesThenTheOutputFileItsReportNames() throws Exception {
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Path outputFile = logs.resolve("output-" + AN_ID + ".log");
        Files.writeString(outputFile, "first line of the file\nsecond line of the file\n", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve("elsewhere"), "a file the id below would name\n");
        AtomicReference<Child.Held> held = new AtomicReference<>(new Child.Held(7, List.of("five", "six", "seven")));
        List<Boolean> letGo = new ArrayList<>();

        TextArea area = HeadlessToolkit.onTheToolkit(() -> new TextArea());
        OutputPanel panel = new OutputPanel(area);
        String afterTheHeldLines = HeadlessToolkit.onTheToolkit(() -> {
            panel.showsTheChild();
            panel.tick(held::get);
            held.set(new Child.Held(8, List.of("five", "six", "seven", "eight")));
            panel.tick(held::get);
            panel.tick(held::get);
            return area.getText();
        });
        String afterAnIdNotOfTheForm = HeadlessToolkit.onTheToolkit(() -> {
            panel.fileNamedBy(workingDirectory, "..\\..\\elsewhere", () -> letGo.add(true));
            panel.fileNamedBy(workingDirectory, "../elsewhere", () -> letGo.add(true));
            return area.getText();
        });
        List<Boolean> letGoByAnIdNotOfTheForm = List.copyOf(letGo);
        String afterTheFile = HeadlessToolkit.onTheToolkit(() -> {
            panel.fileNamedBy(workingDirectory, AN_ID, () -> letGo.add(true));
            return area.getText();
        });
        Files.writeString(outputFile, "third line\nfourth without its end", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        String afterAWholeLine = HeadlessToolkit.onTheToolkit(() -> {
            panel.tick(held::get);
            return area.getText();
        });
        Files.writeString(outputFile, "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        String afterItsEnd = HeadlessToolkit.onTheToolkit(() -> {
            panel.tick(held::get);
            return area.getText();
        });

        claim("the child's held lines are shown each once and in order, the lines read before the first that was held"
                        + " said in one line in their place",
                () -> assertThat(afterTheHeldLines).isEqualTo(
                        "4 lines are not shown here; the output file and logs/vespera.log hold them\nfive\nsix\nseven\neight\n"));
        claim("an id holding a separator or a .. names no file: the panel stays on the child's lines and none are let go",
                () -> {
                    assertThat(afterAnIdNotOfTheForm).isEqualTo(afterTheHeldLines);
                    assertThat(letGoByAnIdNotOfTheForm).isEmpty();
                });
        claim("the output file the report names replaces the panel's text, once, whole, and the child's lines are let go",
                () -> {
                    assertThat(afterTheFile).isEqualTo("first line of the file\nsecond line of the file\n");
                    assertThat(letGo).containsExactly(true);
                });
        claim("the file's growth is appended a whole line at a time: a last line not yet ended waits for its end",
                () -> {
                    assertThat(afterAWholeLine).isEqualTo(afterTheFile + "third line\n");
                    assertThat(afterItsEnd).isEqualTo(afterTheFile + "third line\nfourth without its end\n");
                });
        claim("the panel is text that is selected and copied, not edited, wrapping no line",
                () -> assertThat(List.of(area.isEditable(), area.isWrapText())).containsExactly(false, false));
    }

    @Test
    @Story("The output panel shows the whole output")
    @DisplayName("The panel follows new lines at the bottom, and stays where it is, with its selection, when scrolled up or when text is selected")
    void itFollowsTheBottomOnlyWhileNothingHoldsIt() throws Exception {
        AtomicReference<Child.Held> held = new AtomicReference<>(new Child.Held(0, List.of()));
        TextArea area = HeadlessToolkit.onTheToolkit(() -> {
            TextArea text = new TextArea();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new Scene(new StackPane(text), 400, 200));
            stage.show();
            return text;
        });
        OutputPanel panel = new OutputPanel(area);
        HeadlessToolkit.onTheToolkit(() -> {
            panel.showsTheChild();
            return null;
        });

        double[] atTheBottom = HeadlessToolkit.onTheToolkit(() -> {
            linesUpTo(held, MORE_LINES_THAN_THE_PANEL_SHOWS);
            panel.tick(held::get);
            laidOut(area);
            double first = area.getScrollTop();
            linesUpTo(held, MORE_LINES_THAN_THE_PANEL_SHOWS + FIFTY_LINES);
            panel.tick(held::get);
            laidOut(area);
            return new double[] {first, area.getScrollTop()};
        });
        Object[] scrolledUp = HeadlessToolkit.onTheToolkit(() -> {
            area.setScrollTop(0);
            laidOut(area);
            linesUpTo(held, MORE_LINES_THAN_THE_PANEL_SHOWS + 2 * FIFTY_LINES);
            panel.tick(held::get);
            laidOut(area);
            return new Object[] {area.getScrollTop(), area.getText().endsWith("line " + (MORE_LINES_THAN_THE_PANEL_SHOWS + 2 * FIFTY_LINES) + "\n")};
        });
        Object[] selected = HeadlessToolkit.onTheToolkit(() -> {
            area.setScrollTop(Double.MAX_VALUE);
            laidOut(area);
            area.selectRange(10, 20);
            String selection = area.getSelectedText();
            double top = area.getScrollTop();
            linesUpTo(held, MORE_LINES_THAN_THE_PANEL_SHOWS + 3 * FIFTY_LINES);
            panel.tick(held::get);
            laidOut(area);
            return new Object[] {top, area.getScrollTop(), selection, area.getSelectedText(), area.getAnchor(), area.getCaretPosition()};
        });
        double[] followingAgain = HeadlessToolkit.onTheToolkit(() -> {
            area.deselect();
            area.setScrollTop(Double.MAX_VALUE);
            laidOut(area);
            double before = area.getScrollTop();
            linesUpTo(held, MORE_LINES_THAN_THE_PANEL_SHOWS + 4 * FIFTY_LINES);
            panel.tick(held::get);
            laidOut(area);
            return new double[] {before, area.getScrollTop()};
        });

        claim("at the bottom, each new tick's lines move the view down with them",
                () -> assertThat(atTheBottom[1]).isGreaterThan(atTheBottom[0]));
        claim("scrolled to the top, the view stays where it is and still takes the new lines",
                () -> assertThat(scrolledUp).containsExactly(0.0, true));
        claim("with text selected, neither the view nor the selection moves",
                () -> {
                    assertThat((double) selected[1]).isEqualTo((double) selected[0]);
                    assertThat(selected[3]).isEqualTo(selected[2]);
                    assertThat(List.of(selected[4], selected[5])).containsExactly(10, 20);
                });
        claim("back at the bottom with nothing selected, it follows again",
                () -> assertThat(followingAgain[1]).isGreaterThan(followingAgain[0]));
    }

    /** The child holds every line from 1 to {@code last}, as {@code line <n>}. */
    private static void linesUpTo(AtomicReference<Child.Held> held, int last) {
        held.set(new Child.Held(last, IntStream.rangeClosed(1, last).mapToObj(n -> "line " + n).toList()));
    }

    private static void laidOut(TextArea area) {
        area.applyCss();
        area.layout();
        area.getScene().getRoot().applyCss();
        area.getScene().getRoot().layout();
    }
}
