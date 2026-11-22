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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Architecture window (ADR-442 §1, §3, §5 to §7): opened from the sidebar's last section, a stage of its own with
 * no owner and no modality, the same one brought back and placed where it was left, closed with the cockpit; lit by the
 * report the cockpit reads, its line saying what runs, every text a selectable text at 4.5:1 against its box in either
 * theme as the switch moves; moving with motion on and not off nor while hidden; scaled to the window, or at its own
 * size with the 100% toggle.
 *
 * <p>Each test shows the cockpit as the control panel shows it, on the Headless platform (ADR-342 §6), and finds the
 * Architecture window among the toolkit's windows by its drawing. That opening it again brings it to the front and
 * gives it the focus is held by no test: the Headless platform reports every window focused and keeps no order of
 * windows a test can read. That *Motion: as the system* follows the system is held by none either: the Headless
 * platform reports no reduced motion and offers no way to set it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("878")
@Link(name = "ADR-442", url = Adr.THE_ARCHITECTURE_WINDOW_LIGHTS_WHAT_RUNS, type = "adr")
class TheArchitectureWindowTest {

    private static final String ITS_TITLE = "Architecture";

    /** Where the test moves the window to, and how large it makes it: inside the Headless platform's screen. */
    private static final Placed MOVED_TO = new Placed(40, 30, 700, 500);

    /** The report the sleeping child is said to have written: extraction running, 37 of 100 done. */
    private static final String EXTRACTING = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + "{\"name\": \"byte-level-reduction\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + "{\"name\": \"extraction\", \"state\": \"running\", \"done\": 37, \"total\": 100}], \"charts\": []}";

    /** WCAG AA's least contrast for text. */
    private static final double AA = 4.5;

    /** Where a window stands and how large it is. */
    private record Placed(double x, double y, double width, double height) {
    }

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("The sidebar's last section opens the Architecture window, apart from the control panel's and not modal, the same one brought back where it was left, and closing the control panel closes it")
    void theArchitectureOpensFromTheLastSectionInAWindowOfItsOwn(@TempDir Path folder) throws Exception {
        int before;
        String lastSection;
        Stage opened;
        Object owner;
        Modality modality;
        boolean resizable;
        String title;
        boolean showingAfterClosingIt;
        List<Stage> afterOpeningAgain;
        Placed placedAgain;
        boolean cockpitShowing;
        boolean architectureShowing;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder))) {
            before = window.architectureWindows().size();
            lastSection = window.read(root -> WindowForTests.node(root, "sidebar", Accordion.class).getPanes()
                    .getLast().getId());
            window.openArchitecture();
            opened = window.architectureWindow();
            owner = HeadlessToolkit.onTheToolkit(opened::getOwner);
            modality = HeadlessToolkit.onTheToolkit(opened::getModality);
            resizable = HeadlessToolkit.onTheToolkit(opened::isResizable);
            title = HeadlessToolkit.onTheToolkit(opened::getTitle);
            HeadlessToolkit.onTheToolkit(() -> {
                opened.setX(MOVED_TO.x());
                opened.setY(MOVED_TO.y());
                opened.setWidth(MOVED_TO.width());
                opened.setHeight(MOVED_TO.height());
                return null;
            });
            showingAfterClosingIt = WindowForTests.closeAsTheOperatorDoes(opened);
            window.openArchitecture();
            afterOpeningAgain = window.architectureWindows();
            placedAgain = HeadlessToolkit.onTheToolkit(() -> placeOf(opened));
            cockpitShowing = window.closedAsTheOperatorDoesAndAwaited();
            architectureShowing = HeadlessToolkit.onTheToolkit(opened::isShowing);
        }

        claim("no Architecture window was open before it was asked for, and Architecture is the sidebar's last section",
                () -> {
                    assertThat(before).isZero();
                    assertThat(lastSection).isEqualTo("architecture");
                });
        claim("its button opens one window apart from the control panel's, with no owner and no modality, resizable"
                        + " and titled Architecture",
                () -> {
                    assertThat(owner).isNull();
                    assertThat(modality).isEqualTo(Modality.NONE);
                    assertThat(resizable).isTrue();
                    assertThat(title).isEqualTo(ITS_TITLE);
                });
        claim("closed by its close button, asking nothing, it closes; opened again, it is the same window, where and"
                        + " as large as it was left",
                () -> {
                    assertThat(showingAfterClosingIt).isFalse();
                    assertThat(afterOpeningAgain).containsExactly(opened);
                    assertThat(placedAgain).isEqualTo(MOVED_TO);
                });
        claim("closing the control panel's window closes the Architecture window with it",
                () -> {
                    assertThat(cockpitShowing).isFalse();
                    assertThat(architectureShowing).isFalse();
                });
    }

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("A report the control panel reads with extraction running lights its stage, with its word and how far it has got, and the line names the step; every text is selectable and stands at AA against its box, in the light and the dark theme")
    void aReportReadLightsTheDrawingAndTheLine(@TempDir Path folder) throws Exception {
        List<String> stageTwo;
        String words;
        String now;
        List<String> stageThree;
        List<String> labels;
        List<String> editable;
        int selectable;
        boolean darkAtFirst;
        boolean darkWithTheCockpit;
        double leastLight;
        double leastDark;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, EXTRACTING);
            window.awaiting(root -> WindowForTests.node(root, "step-extraction", Node.class).lookupAll(".text-field")
                    .stream().map(each -> ((TextInputControl) each).getText()).toList(),
                    texts -> texts.stream().anyMatch(text -> text.startsWith("running")));
            window.openArchitecture();
            stageTwo = window.awaitingArchitecture(root -> styleOf(root, "stage-2"),
                    classes -> classes.contains("lit-running"));
            words = window.readArchitecture(root -> textOf(root, "architecture-words-stage-2"));
            now = window.readArchitecture(root -> textOf(root, "architecture-now"));
            stageThree = window.readArchitecture(root -> styleOf(root, "stage-3"));
            labels = window.readArchitecture(root -> outsideAControl(root).stream()
                    .filter(Label.class::isInstance).map(each -> ((Label) each).getText())
                    .filter(text -> text != null && !text.isBlank()).toList());
            editable = window.readArchitecture(root -> HeadlessToolkit.everyNodeUnder(root).stream()
                    .filter(TextInputControl.class::isInstance).map(TextInputControl.class::cast)
                    .filter(text -> !(text instanceof SelectableText) || text.isEditable()).map(TextInputControl::getText)
                    .toList());
            selectable = window.readArchitecture(root -> (int) HeadlessToolkit.everyNodeUnder(root).stream()
                    .filter(SelectableText.class::isInstance).count());
            darkAtFirst = window.readArchitecture(TheArchitectureWindowTest::dark);
            leastLight = window.readArchitecture(TheArchitectureWindowTest::leastContrastInABox);
            window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).setSelected(true));
            darkWithTheCockpit = window.readArchitecture(TheArchitectureWindowTest::dark);
            leastDark = window.readArchitecture(TheArchitectureWindowTest::leastContrastInABox);
        }

        claim("stage 2 reads running, with the running word, and stage 3, whose steps have not begun, does not",
                () -> {
                    assertThat(stageTwo).contains("lit-running");
                    assertThat(words).isEqualTo(Architecture.Lit.RUNNING.words());
                    assertThat(stageThree).doesNotContain("lit-running").contains("lit-waiting");
                });
        claim("the line above the drawing names the stage, the step and how far it has got, 37 of 100",
                () -> assertThat(now).startsWith("Now: stage 2 Extraction, step extraction 37%"));
        claim("the window shows no label with words, and every text input in it is a selectable text, not editable,"
                        + " the drawing's texts among them",
                () -> {
                    assertThat(labels).isEmpty();
                    assertThat(editable).isEmpty();
                    assertThat(selectable).isGreaterThan(Architecture.Part.values().length);
                });
        claim("the window follows the control panel's theme switch into the dark theme",
                () -> assertThat(List.of(darkAtFirst, darkWithTheCockpit)).containsExactly(false, true));
        claim("every text drawn in a part stands at 4.5:1 or more against the part's fill, in the light theme and in"
                        + " the dark",
                () -> {
                    assertThat(leastLight).isGreaterThanOrEqualTo(AA);
                    assertThat(leastDark).isGreaterThanOrEqualTo(AA);
                });
    }

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("The drawing moves with motion on and not with it off, stops while the window is hidden, and is scaled to the window, or drawn at its own size with 100%")
    void motionFollowsTheSwitchStopsWhileHiddenAndTheDrawingFitsOrStandsAtFullSize(@TempDir Path folder)
            throws Exception {
        boolean asTheSystem;
        boolean off;
        boolean on;
        boolean hidden;
        double fitted;
        double fullSize;
        ScrollPane.ScrollBarPolicy barsFitted;
        ScrollPane.ScrollBarPolicy barsAtFullSize;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder))) {
            window.openArchitecture();
            Stage opened = window.architectureWindow();
            asTheSystem = window.readArchitecture(root -> drawing(root).moving());
            off = window.readArchitecture(root -> {
                motion(root).getSelectionModel().select(ArchitectureWindow.MOTION_OFF);
                return drawing(root).moving();
            });
            on = window.readArchitecture(root -> {
                motion(root).getSelectionModel().select(ArchitectureWindow.MOTION_ON);
                return drawing(root).moving();
            });
            HeadlessToolkit.onTheToolkit(() -> {
                opened.setWidth(MOVED_TO.width());
                opened.setHeight(MOVED_TO.height());
                return null;
            });
            // the platform lays the smaller window out on a pulse of its own, so the scale is read until it follows
            fitted = window.awaitingArchitecture(root -> drawing(root).scale(), scale -> scale < 1);
            barsFitted = window.readArchitecture(root -> scroll(root).getHbarPolicy());
            fullSize = window.readArchitecture(root -> {
                WindowForTests.node(root, "architecture-full-size", ToggleButton.class).setSelected(true);
                return drawing(root).scale();
            });
            barsAtFullSize = window.readArchitecture(root -> scroll(root).getHbarPolicy());
            ArchitectureView view = window.readArchitecture(TheArchitectureWindowTest::drawing);
            WindowForTests.closeAsTheOperatorDoes(opened);
            hidden = HeadlessToolkit.onTheToolkit(view::moving);
        }

        claim("as the system, which reports no reduced motion here, the drawing moves; with motion off it stands still,"
                        + " and with it on it moves again",
                () -> assertThat(List.of(asTheSystem, off, on)).containsExactly(true, false, true));
        claim("the window hidden, the drawing stands still though motion is on", () -> assertThat(hidden).isFalse());
        claim("in a window smaller than the drawing, the drawing is scaled down to fit it, with no scroll bar",
                () -> {
                    assertThat(fitted).isLessThan(1);
                    assertThat(barsFitted).isEqualTo(ScrollPane.ScrollBarPolicy.NEVER);
                });
        claim("with 100% pressed, it is drawn at its own size and the window scrolls over it",
                () -> {
                    assertThat(fullSize).isEqualTo(1);
                    assertThat(barsAtFullSize).isEqualTo(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                });
    }

    // ---- what the tests share

    private static ControlPanelState aStateOver(Path folder) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        return state;
    }

    private static ArchitectureView drawing(Parent root) {
        return WindowForTests.node(root, WindowForTests.ARCHITECTURE_DRAWING, ArchitectureView.class);
    }

    @SuppressWarnings("unchecked")
    private static ChoiceBox<String> motion(Parent root) {
        return WindowForTests.node(root, "architecture-motion", ChoiceBox.class);
    }

    private static ScrollPane scroll(Parent root) {
        return WindowForTests.node(root, "architecture-scroll", ScrollPane.class);
    }

    private static List<String> styleOf(Parent root, String part) {
        return List.copyOf(WindowForTests.node(root, "architecture-" + part, Node.class).getStyleClass());
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }

    /**
     * Every node under {@code root} that lies outside a control, a scroll's content being outside it: the texts of a
     * control, as a choice box's, are the control's own (ADR-364 §8).
     */
    private static List<Node> outsideAControl(Parent root) {
        List<Node> reached = new ArrayList<>();
        List<Node> toVisit = new ArrayList<>(List.of(root));
        while (!toVisit.isEmpty()) {
            Node node = toVisit.removeLast();
            reached.add(node);
            if (node instanceof ScrollPane scrolled) {
                toVisit.add(scrolled.getContent());
            } else if (!(node instanceof javafx.scene.control.Control) && node instanceof Parent parent) {
                toVisit.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return reached;
    }

    private static boolean dark(Parent root) {
        return root.getStyleClass().contains("dark");
    }

    /** The least contrast of a text drawn in a part against the part's fill, over every part. */
    private static double leastContrastInABox(Parent root) {
        List<Double> contrasts = new ArrayList<>();
        for (Architecture.Part part : Architecture.Part.values()) {
            Region box = WindowForTests.node(root, "architecture-" + part.id(), Region.class);
            Color fill = (Color) box.getBackground().getFills().getFirst().getFill();
            for (Node node : HeadlessToolkit.everyNodeUnder(box)) {
                if (node instanceof Text text && text.isVisible() && text.getText() != null && !text.getText().isBlank()
                        && text.getFill() instanceof Color ink) {
                    contrasts.add(contrast(ink, fill));
                }
            }
        }
        return contrasts.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    private static double contrast(Color one, Color other) {
        double lighter = Math.max(luminance(one), luminance(other));
        double darker = Math.min(luminance(one), luminance(other));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color colour) {
        return 0.2126 * channel(colour.getRed()) + 0.7152 * channel(colour.getGreen())
                + 0.0722 * channel(colour.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static Placed placeOf(Stage window) {
        return new Placed(window.getX(), window.getY(), window.getWidth(), window.getHeight());
    }
}
