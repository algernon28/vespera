package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's questions and buttons (ADR-423): a question of {@code Dialogs.confirm} is a dialog titled as its
 * caller names it, Stop's <i>Stop the invocation</i>, with no header and no icon, showing its sentence alone beside its
 * answers (§1); its answer that goes on is drawn in the muted red of {@code -vespera-ending} and the one that keeps in
 * the muted green of {@code -vespera-keeping}, and a choice of {@code Dialogs.choose} draws its first answer keeping
 * and its second ending, any other as every button (§2, §3); and every button is drawn under the pointer at once in a
 * lighter fill and 1.04 times its size, a question's answers in shades of their own, while a disabled button is drawn
 * as it is away from it, the stylesheet declaring no transition (§4).
 *
 * <p>Each question is asked through {@link Dialogs#of} over a window drawn on the Headless platform (ADR-342 §6), read
 * while it shows and answered by the test. The pointer over a button is the {@code hover} state given to the button,
 * which is all the stylesheet reads of it. The colours expected are read from the stylesheet's two theme rules, light
 * then dark, the window switched between them by its switch.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-423", url = Adr.A_QUESTIONS_ANSWERS_ARE_COLOURED_AND_A_BUTTON_GROWS_UNDER_THE_POINTER, type = "adr")
class TheWindowsQuestionsAndButtonsTest {

    /** The words of the question the test asks, under a title of its caller's, and its two answers. */
    private static final String QUESTIONS_TITLE = "A question's title";

    private static final String QUESTION = "A sentence asking whether to go on.";

    private static final String GO_ON = "Go on";

    private static final String KEEP = "Keep";

    /** The title Stop's question is asked under. */
    private static final String STOPS_TITLE = "Stop the invocation";

    /** The words of the choice the test asks, its answers in the order the profile's question gives them. */
    private static final String TITLE = "The profile";

    private static final String SENTENCE = "The profile has changes not saved in profile.yaml.";

    private static final String SAVE = "Save";

    private static final String DISCARD = "Discard";

    private static final String CANCEL = "Cancel";

    /** The two themes, in the order the test draws them: the window opens light, and its switch makes it dark. */
    private static final List<String> THEMES = List.of("light", "dark");

    /** The size a button under the pointer is drawn at, across and down, against its own; and its own. */
    private static final double GROWN = 1.04;

    private static final double OWN_SIZE = 1.0;

    /** The state a button under the pointer is in, and all the stylesheet reads of the pointer. */
    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");

    /** A declaration of a transition of JavaFX's stylesheets, its shorthand or any of its four properties. */
    private static final Pattern A_TRANSITION = Pattern.compile("(?:-fx-)?transition(?:-[a-z]+)?\\s*:");

    /** How many turns of the toolkit the reading waits for the dialog to show. */
    private static final int TURNS = 100;

    @Test
    @Story("A question's answers say by their colour what they do")
    @DisplayName("A question shows its title and its sentence alone, with no header and no icon, its answer that goes on in the ending red and the one that keeps in the keeping green, each with a shade of its own under the pointer, in both themes")
    void aQuestionShowsItsTitleAndSentenceAloneAndColoursItsAnswersByWhatTheyDo(@TempDir Path folder)
            throws Exception {
        Map<String, Map<String, String>> themes = TheControlPanelsThemeTest.themes(TheControlPanelsThemeTest.stylesheet());
        Map<String, Asked> drawn = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            Dialogs dialogs = Dialogs.of(() -> window.stage, address -> { });
            for (String theme : THEMES) {
                inTheme(window, theme);
                drawn.put(theme, readWhileAsked(window.stage,
                        () -> dialogs.confirm(QUESTIONS_TITLE, QUESTION, GO_ON, KEEP),
                        TheWindowsQuestionsAndButtonsTest::asked, KEEP));
            }
        }

        for (String theme : THEMES) {
            Asked asked = drawn.get(theme);
            Map<String, String> declared = themes.get(theme);
            claim("in the " + theme + " theme, the dialog is titled as its caller names it, " + QUESTIONS_TITLE,
                    () -> assertThat(asked.title()).isEqualTo(QUESTIONS_TITLE));
            claim("in the " + theme + " theme, it has no header, no header text and no icon, and the only words it"
                            + " shows beside its answers are its sentence",
                    () -> {
                        assertThat(asked.header()).as("whether it has a header").isFalse();
                        assertThat(asked.headerText()).as("its header text").isNullOrEmpty();
                        assertThat(asked.icon()).as("whether it has an icon").isFalse();
                        assertThat(asked.words()).as("the words it shows beside its answers")
                                .containsExactly(QUESTION);
                    });
            claim("in the " + theme + " theme, the answer that goes on is drawn on the ending red and the one that"
                            + " keeps on the keeping green, each with a button's text colour",
                    () -> assertThat(List.of(List.of(asked.goOn().fill(), asked.goOnInk()),
                            List.of(asked.keep().fill(), asked.keepInk())))
                            .as("the answer that goes on, then the one that keeps: its fill and its text")
                            .containsExactly(colours(declared, "-vespera-ending", "-vespera-accent-ink"),
                                    colours(declared, "-vespera-keeping", "-vespera-accent-ink")));
            claim("in the " + theme + " theme, under the pointer each is drawn at once on a shade of its own, the"
                            + " ending red's and the keeping green's, at 1.04 times its size across and down",
                    () -> {
                        assertThat(List.of(asked.goOn().hoveredFill(), asked.keep().hoveredFill()))
                                .as("under the pointer: the answer that goes on, the one that keeps")
                                .containsExactly(colour(declared, "-vespera-ending-hover"),
                                        colour(declared, "-vespera-keeping-hover"));
                        assertThat(List.of(asked.goOn().hoveredScale(), asked.keep().hoveredScale()))
                                .as("their sizes under the pointer, across and down")
                                .allSatisfy(scale -> assertThat(scale).hasSize(2).allSatisfy(each ->
                                        assertThat(each).isCloseTo(GROWN, within(1e-9))));
                    });
        }
    }

    @Test
    @Story("A question's answers say by their colour what they do")
    @DisplayName("Stop, with an invocation running, asks its question under the title Stop the invocation, in Stop's words and answers")
    void stopAsksItsQuestionUnderItsTitle(@TempDir Path folder) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));

        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            window.dialogs.answer = false;
            window.awaiting(root -> !WindowForTests.node(root, "stop", Button.class).isDisabled(), Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "stop", Button.class).fire());

            claim("Stop asked one question, titled Stop the invocation, with Stop's sentence, Stop to go on and Keep it"
                            + " running not to",
                    () -> assertThat(window.dialogs.asked).containsExactly(new AnsweringDialogs.Asked(
                            STOPS_TITLE, Child.STOP_CONFIRMATION, "Stop", "Keep it running")));
        }
    }

    @Test
    @Story("A question's answers say by their colour what they do")
    @DisplayName("A choice draws its first answer, Save, in the keeping green and its second, Discard, in the ending red, and Cancel as every button, in both themes")
    void aChoiceDrawsItsFirstAnswerKeepingAndItsSecondEnding(@TempDir Path folder) throws Exception {
        Map<String, Map<String, String>> themes = TheControlPanelsThemeTest.themes(TheControlPanelsThemeTest.stylesheet());
        Map<String, List<List<Paint>>> drawn = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            Dialogs dialogs = Dialogs.of(() -> window.stage, address -> { });
            for (String theme : THEMES) {
                inTheme(window, theme);
                drawn.put(theme, readWhileAsked(window.stage,
                        () -> dialogs.choose(TITLE, SENTENCE, List.of(SAVE, DISCARD, CANCEL)),
                        pane -> List.of(fillAndInk(answer(pane, SAVE)), fillAndInk(answer(pane, DISCARD)),
                                fillAndInk(answer(pane, CANCEL))), CANCEL));
            }
        }
        Map<String, List<List<Paint>>> wanted = new LinkedHashMap<>();
        for (String theme : THEMES) {
            Map<String, String> declared = themes.get(theme);
            wanted.put(theme, List.of(colours(declared, "-vespera-keeping", "-vespera-accent-ink"),
                    colours(declared, "-vespera-ending", "-vespera-accent-ink"),
                    colours(declared, "-vespera-accent", "-vespera-accent-ink")));
        }

        claim("in the light theme and then the dark, Save is drawn on the keeping green, Discard on the ending red and"
                        + " Cancel on the accent, each with a button's text colour",
                () -> assertThat(drawn).as("each theme's answers, Save, Discard and Cancel, each its fill and text")
                        .containsExactlyEntriesOf(wanted));
    }

    @Test
    @Story("Every button answers the pointer")
    @DisplayName("Under the pointer a button is drawn at once in a lighter fill and 1.04 times its size, and a disabled one as it is, in both themes, the stylesheet declaring no transition")
    void aButtonUnderThePointerIsDrawnAtOnceLighterAndLargerAndADisabledOneIsNot(@TempDir Path folder)
            throws Exception {
        String stylesheet = TheControlPanelsThemeTest.stylesheet();
        Map<String, Map<String, String>> themes = TheControlPanelsThemeTest.themes(stylesheet);
        Map<String, Reacted> enabled = new LinkedHashMap<>();
        Map<String, Reacted> disabled = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            for (String theme : THEMES) {
                inTheme(window, theme);
                enabled.put(theme, window.read(root -> reacted(WindowForTests.node(root, "shut-down-docker",
                        Button.class))));
                disabled.put(theme, window.read(root -> reacted(WindowForTests.node(root, "stop", Button.class))));
            }
        }
        List<String> transitions = A_TRANSITION.matcher(TheControlPanelsThemeTest.withoutComments(stylesheet))
                .results().map(MatchResult::group).toList();

        claim("Shut down Docker is enabled, and Stop, with no invocation running, is disabled",
                () -> assertThat(List.of(enabled.get("light").disabled(), disabled.get("light").disabled()))
                        .as("disabled: Shut down Docker, Stop").containsExactly(false, true));
        for (String theme : THEMES) {
            Map<String, String> declared = themes.get(theme);
            Reacted docker = enabled.get(theme);
            Reacted stop = disabled.get(theme);
            claim("in the " + theme + " theme, Shut down Docker is drawn on the accent at its own size away from the"
                            + " pointer, and under it, as soon as its look is worked out, on the lighter accent at 1.04"
                            + " times its size across and down",
                    () -> {
                        assertThat(docker.fill()).as("away from the pointer").isEqualTo(colour(declared,
                                "-vespera-accent"));
                        assertThat(docker.scale()).as("its size away from the pointer, across and down")
                                .containsExactly(OWN_SIZE, OWN_SIZE);
                        assertThat(docker.hoveredFill()).as("under the pointer").isEqualTo(colour(declared,
                                "-vespera-accent-hover"));
                        assertThat(docker.hoveredScale()).as("its size under the pointer, across and down").hasSize(2)
                                .allSatisfy(scale -> assertThat(scale).isCloseTo(GROWN, within(1e-9)));
                    });
            claim("in the " + theme + " theme, Stop, disabled, is drawn under the pointer as away from it: on the"
                            + " accent, at its own size",
                    () -> {
                        assertThat(List.of(stop.fill(), stop.hoveredFill())).as("away from the pointer, under it")
                                .containsOnly(colour(declared, "-vespera-accent"));
                        assertThat(stop.hoveredScale()).as("its size under the pointer, across and down")
                                .containsExactly(OWN_SIZE, OWN_SIZE);
                    });
        }
        claim("the stylesheet declares no transition, so that nothing of a button's look is drawn over time",
                () -> assertThat(transitions).as("the transitions declared").isEmpty());
    }

    // ---- the window

    private static ChildRuntime aRuntime(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }

    /** Switches the window to {@code theme} by its switch, where it is not in it. */
    private static void inTheme(WindowForTests window, String theme) throws Exception {
        boolean dark = "dark".equals(theme);
        window.act(root -> {
            ToggleButton toggle = WindowForTests.node(root, "theme-toggle", ToggleButton.class);
            if (toggle.isSelected() != dark) {
                toggle.fire();
            }
        });
    }

    /** What a button is drawn as away from the pointer and under it: its fill and its size across and down. */
    private record Reacted(boolean disabled, Paint fill, List<Double> scale, Paint hoveredFill,
            List<Double> hoveredScale) {
    }

    /**
     * What {@code button} is drawn as away from the pointer, and under it, each read as soon as its look is worked out
     * again, in the same turn of the toolkit; the button is then left as the pointer has it. The Headless platform's
     * pointer stands still at the scene's corner, so a button there is under it as the window opens: each state is given
     * before it is read.
     */
    private static Reacted reacted(Button button) {
        boolean pointedAt = button.isHover();
        button.pseudoClassStateChanged(HOVER, false);
        button.applyCss();
        Paint fill = TheControlPanelsThemeTest.innermost(button);
        List<Double> scale = List.of(button.getScaleX(), button.getScaleY());
        button.pseudoClassStateChanged(HOVER, true);
        button.applyCss();
        Paint hoveredFill = TheControlPanelsThemeTest.innermost(button);
        List<Double> hoveredScale = List.of(button.getScaleX(), button.getScaleY());
        button.pseudoClassStateChanged(HOVER, pointedAt);
        button.applyCss();
        return new Reacted(button.isDisabled(), fill, scale, hoveredFill, hoveredScale);
    }

    // ---- the dialog

    /**
     * Asks a question by {@code asking} over {@code owner} and reads its pane by {@code reading} while it shows, then
     * presses the answer whose words are {@code press}; run on the toolkit's thread, where the dialog's own loop runs
     * the reading.
     */
    private static <T> T readWhileAsked(Window owner, Runnable asking, Function<DialogPane, T> reading, String press)
            throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            List<T> read = new ArrayList<>();
            readWhenShown(owner, reading, press, read, 0);
            asking.run();
            if (read.isEmpty()) {
                throw new AssertionError("no dialog showed over the window");
            }
            return read.getFirst();
        });
    }

    private static <T> void readWhenShown(Window owner, Function<DialogPane, T> reading, String press, List<T> read,
            int turn) {
        Platform.runLater(() -> {
            Optional<DialogPane> pane = Window.getWindows().stream().filter(each -> each != owner && each.isShowing())
                    .filter(each -> each.getScene() != null)
                    .flatMap(each -> HeadlessToolkit.everyNodeUnder(each.getScene().getRoot()).stream())
                    .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst();
            if (pane.isEmpty()) {
                if (turn < TURNS) {
                    readWhenShown(owner, reading, press, read, turn + 1);
                }
                return;
            }
            DialogPane dialog = pane.get();
            dialog.applyCss();
            dialog.layout();
            read.add(reading.apply(dialog));
            answer(dialog, press).fire();
        });
    }

    /**
     * What a question's dialog showed: its window's title, whether it has a header, its header text, whether it has an
     * icon, the words it shows beside its answers, and its two answers as drawn away from the pointer and under it,
     * with their text colours.
     */
    private record Asked(String title, boolean header, String headerText, boolean icon, List<String> words,
            Reacted goOn, Paint goOnInk, Reacted keep, Paint keepInk) {
    }

    private static Asked asked(DialogPane pane) {
        String title = pane.getScene().getWindow() instanceof Stage stage ? stage.getTitle() : null;
        List<String> words = HeadlessToolkit.everyNodeUnder(pane).stream()
                .filter(node -> node instanceof Labeled && !(node instanceof ButtonBase) && shown(node, pane))
                .map(node -> ((Labeled) node).getText()).filter(text -> text != null && !text.isBlank()).toList();
        Button goOn = answer(pane, GO_ON);
        Button keep = answer(pane, KEEP);
        return new Asked(title, pane.getHeader() != null, pane.getHeaderText(), pane.getGraphic() != null, words,
                reacted(goOn), goOn.getTextFill(), reacted(keep), keep.getTextFill());
    }

    /** Whether {@code node} is drawn: it and every parent up to {@code top} visible. */
    private static boolean shown(Node node, Node top) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
            if (at == top) {
                return true;
            }
        }
        return true;
    }

    /** The button of {@code pane}'s answer whose words are {@code words}. */
    private static Button answer(DialogPane pane, String words) {
        ButtonType type = pane.getButtonTypes().stream().filter(each -> words.equals(each.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the dialog has no answer " + words));
        return (Button) pane.lookupButton(type);
    }

    /** The fill {@code button} is drawn on and the colour of its text. */
    private static List<Paint> fillAndInk(Button button) {
        button.applyCss();
        List<Paint> drawn = new ArrayList<>();
        drawn.add(TheControlPanelsThemeTest.innermost(button));
        drawn.add(button.getTextFill());
        return drawn;
    }

    // ---- the stylesheet

    /** The colour {@code token} comes to in {@code declared}; the test fails where it comes to none. */
    private static Color colour(Map<String, String> declared, String token) {
        return TheControlPanelsThemeTest.resolved(declared, token)
                .orElseThrow(() -> new AssertionError(token + " is not set as a colour"));
    }

    /** The colours {@code fill} and {@code ink} come to in {@code declared}, in that order. */
    private static List<Paint> colours(Map<String, String> declared, String fill, String ink) {
        return List.of(colour(declared, fill), colour(declared, ink));
    }
}
