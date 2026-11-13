package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every {@link SelectableText.Line} whose text runs past its right edge shows "…" there, a {@link Text} laid over its
 * right edge that takes no mouse event, holds no part of the text and is visible exactly while the end of the text is
 * out of view (ADR-377 §6). A {@code Line} is drawn alone on a stage of its own with the window's stylesheet, on the
 * Headless platform, and the window itself as the control panel shows it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("804")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class ACutShortLineShowsAnEllipsisTest {

    /** How far apart two places may be and still be the same: a pixel. */
    private static final double ONE_PIXEL = 1.0;

    /** The width a {@code Line} is laid out in. */
    private static final double LAID_OUT_WIDTH = 200;

    /** The height of the stage a {@code Line} is drawn on. */
    private static final double STAGE_HEIGHT = 100;

    /** How many characters a text cut short holds, far wider than {@link #LAID_OUT_WIDTH}. */
    private static final int LONG = 300;

    /** A text of {@link #LONG} characters, its last ten telling where it ends. */
    private static final String A_LONG_TEXT = "a".repeat(LONG - 10) + "0123456789";

    /** A text of ten characters, narrower than {@link #LAID_OUT_WIDTH}. */
    private static final String A_SHORT_TEXT = "ten chars!";

    /** A folder name long enough that the header's {@code where} is wider than a window of 700. */
    private static final String A_LONG_FOLDER = "/archive/" + "a-folder-with-a-long-name/".repeat(9) + "corpus";

    /** A folder name short enough that {@code where} fits a window of 1500. */
    private static final String A_SHORT_FOLDER = "/archive/corpus";

    /** The window's width where space is tight. */
    private static final double TIGHT_WIDTH = 700;

    /** What the claims read of a {@code Line} and its "…". */
    private record Seen(boolean shown, double ellipsisRight, double innerRight, String text, String copied,
            boolean mouseTransparent, String ellipsisText, int caret, boolean focused) {
    }

    @Test
    @Story("A one-line text cut short shows an ellipsis")
    @DisplayName("A one-line text narrower than its text shows an ellipsis over its end, a short one none, and Select All then Copy still gives the whole text")
    void aLineNarrowerThanItsTextShowsAnEllipsisOverItsEnd() throws Exception {
        Seen cut = onAStage(A_LONG_TEXT, line -> {
            line.selectAll();
            line.copy();
            String copied = Clipboard.getSystemClipboard().getString();
            line.selectRange(0, 0);
            line.getScene().getRoot().layout();
            return seen(line, copied);
        });
        Seen whole = onAStage(A_SHORT_TEXT, line -> seen(line, ""));

        claim("a line of " + LONG + " characters laid out " + (int) LAID_OUT_WIDTH + " pixels wide shows its ellipsis",
                () -> assertThat(cut.shown()).isTrue());
        claim("the ellipsis reads \"…\" and its right edge lies on the line's inner right edge, within a pixel",
                () -> {
                    assertThat(cut.ellipsisText()).isEqualTo("…");
                    assertThat(cut.ellipsisRight()).isCloseTo(cut.innerRight(), offset(ONE_PIXEL));
                });
        claim("a line of ten characters laid out as wide shows none",
                () -> assertThat(whole.shown()).isFalse());
        claim("Select All then Copy gives the " + LONG + " characters, with no ellipsis",
                () -> assertThat(cut.copied()).isEqualTo(A_LONG_TEXT).doesNotContain("…"));
    }

    @Test
    @Story("A one-line text cut short shows an ellipsis")
    @DisplayName("The ellipsis takes no mouse event and is no part of the text, and a press over it places the caret in the line")
    void theEllipsisTakesNoMouseEventAndIsNoPartOfTheText() throws Exception {
        Seen pressed = onAStage(A_LONG_TEXT, line -> {
            Text ellipsis = line.ellipsis();
            Bounds over = line.sceneToLocal(ellipsis.localToScene(ellipsis.getBoundsInLocal()));
            double x = over.getCenterX();
            double y = over.getCenterY();
            Event.fireEvent(line, new MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y, MouseButton.PRIMARY, 1, false,
                    false, false, false, true, false, false, true, false, false, null));
            Event.fireEvent(line, new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, x, y, MouseButton.PRIMARY, 1, false,
                    false, false, false, false, false, false, true, false, false, null));
            return seen(line, "");
        });

        claim("the ellipsis is mouse-transparent", () -> assertThat(pressed.mouseTransparent()).isTrue());
        claim("the line's text is unchanged by it", () -> assertThat(pressed.text()).isEqualTo(A_LONG_TEXT));
        claim("a press over the ellipsis gives the line the focus and places its caret inside its text",
                () -> {
                    assertThat(pressed.focused()).isTrue();
                    assertThat(pressed.caret()).isPositive().isLessThanOrEqualTo(LONG);
                });
    }

    @Test
    @Story("A one-line text cut short shows an ellipsis")
    @DisplayName("The ellipsis goes while the end of the text is in view, and comes back once the caret is put back at the start")
    void itGoesWhileTheEndIsInViewAndComesBackWithTheCaretAtTheStart() throws Exception {
        Map<String, Seen> seen = new LinkedHashMap<>();
        HeadlessToolkit.onTheToolkit(() -> {
            SelectableText.Line line = SelectableText.line(A_LONG_TEXT);
            Button elsewhere = new Button("elsewhere");
            Stage stage = stageOf(new VBox(new HBox(line), elsewhere));
            try {
                stage.getScene().getRoot().layout();
                seen.put("before", seen(line, ""));
                line.requestFocus();
                line.selectAll();
                stage.getScene().getRoot().layout();
                seen.put("selected", seen(line, ""));
                elsewhere.requestFocus();
                stage.getScene().getRoot().layout();
                seen.put("let go", seen(line, ""));
            } finally {
                stage.hide();
            }
            return null;
        });

        claim("the cut line shows its ellipsis before it is selected, so that the claims below have one to hide",
                () -> assertThat(seen.get("before").shown()).isTrue());
        claim("Select All with the focus in the cut line scrolls its end into view, and the ellipsis is not visible",
                () -> {
                    assertThat(seen.get("selected").focused()).isTrue();
                    assertThat(seen.get("selected").shown()).isFalse();
                });
        claim("with the focus moved away, the caret is at 0 and the ellipsis is visible again",
                () -> {
                    assertThat(seen.get("let go").caret()).isZero();
                    assertThat(seen.get("let go").shown()).isTrue();
                });
    }

    @Test
    @Story("A one-line text cut short shows an ellipsis")
    @DisplayName("The window's cut lines show it: the header's where at 700 wide, and not at 1500 where it fits")
    void theWindowsCutLinesShowIt(@TempDir Path folder) throws Exception {
        boolean tight;
        boolean wide;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(
                Files.createDirectories(folder.resolve("tight")), SleepingJar.runtime()))) {
            window.act(root -> {
                window.stage.setWidth(TIGHT_WIDTH);
                WindowForTests.node(root, "vespera.corpus-root", TextField.class).setText(A_LONG_FOLDER);
            });
            tight = window.read(root -> WindowForTests.node(root, "where", SelectableText.Line.class).ellipsis()
                    .isVisible());
        }
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(
                Files.createDirectories(folder.resolve("wide")), SleepingJar.runtime()))) {
            window.act(root -> {
                window.stage.setWidth(ControlPanelWindow.WIDTH);
                WindowForTests.node(root, "vespera.corpus-root", TextField.class).setText(A_SHORT_FOLDER);
            });
            wide = window.read(root -> WindowForTests.node(root, "where", SelectableText.Line.class).ellipsis()
                    .isVisible());
        }

        claim("at 700 by 960, where the path in where is wider than the header, where's ellipsis is visible",
                () -> assertThat(tight).isTrue());
        claim("at 1500 by 960, where it fits, it is not", () -> assertThat(wide).isFalse());
    }

    /** What {@code reading} reads of a {@code Line} of {@code text} laid out {@link #LAID_OUT_WIDTH} wide on a stage of its own. */
    private static Seen onAStage(String text, Function<SelectableText.Line, Seen> reading) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            SelectableText.Line line = SelectableText.line(text);
            Stage stage = stageOf(new HBox(line));
            try {
                stage.getScene().getRoot().layout();
                return reading.apply(line);
            } finally {
                stage.hide();
            }
        });
    }

    /** A stage of {@link #LAID_OUT_WIDTH} by {@link #STAGE_HEIGHT} showing {@code root} with the window's stylesheet. */
    private static Stage stageOf(javafx.scene.Parent root) {
        Scene scene = new Scene(root, LAID_OUT_WIDTH, STAGE_HEIGHT);
        scene.getStylesheets().add(ControlPanelWindow.class.getResource("control-panel.css").toExternalForm());
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return stage;
    }

    private static Seen seen(SelectableText.Line line, String copied) {
        Text ellipsis = line.ellipsis();
        Bounds at = line.sceneToLocal(ellipsis.localToScene(ellipsis.getBoundsInLocal()));
        double innerRight = line.getWidth() - line.getInsets().getRight();
        return new Seen(ellipsis.isVisible(), at.getMaxX(), innerRight, line.getText(), copied,
                ellipsis.isMouseTransparent(), ellipsis.getText(), line.getCaretPosition(), line.isFocused());
    }
}
