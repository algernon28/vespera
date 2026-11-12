package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Window;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's light and dark themes (ADR-372): the switch at the header's right, between the words Light and
 * Dark under the word Theme, light on a first start (§1); the choice kept in a {@code theme} file beside the launch
 * values, only where their folder exists and is the user's own, and read back by the next window (§2); the
 * stylesheet's colour tokens, defined once and redefined for the dark theme, each text token at WCAG AA against the
 * backgrounds of its theme (§3); and a dialog over the window taking its stylesheet and, over a dark window, the dark
 * tokens (§4).
 *
 * <p>Each window test draws the window on the Headless platform (ADR-342 §6) as {@code ControlPanelWindow} shows it,
 * through {@link WindowForTests}, and finds the switch by its id; the words are read where the record fixes them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("794")
@Link(name = "ADR-372", url = Adr.THE_CONTROL_PANELS_WINDOW_HAS_A_LIGHT_AND_A_DARK_THEME, type = "adr")
class TheControlPanelsThemeTest {

    private static final String USER = System.getProperty("user.name");

    /** The style class the window's root carries while the dark theme is chosen. */
    private static final String DARK = "dark";

    /** The style class a dialog's pane carries over a dark window, which the stylesheet gives the dark tokens. */
    private static final String DARK_DIALOG = "dark-dialog";

    /** The file beside the launch values that keeps the theme chosen. */
    private static final String THEME_FILE = "theme";

    /** WCAG AA's least contrast for text of a normal size. */
    private static final double AA = 4.5;

    /** How far, in pixels, the switch's right edge may stand from the header's and still be at it. */
    private static final double ONE_PIXEL = 1.0;

    /** The dark theme's text tokens, each held against every dark background. */
    private static final List<String> TEXT_TOKENS = List.of("-vespera-ink", "-vespera-dim", "-vespera-accent",
            "-vespera-bad", "-vespera-faint");

    /** The dark theme's backgrounds: the surface of a card or a dialog, the window's ground, and a control's inside. */
    private static final List<String> DARK_BACKGROUNDS = List.of("-vespera-surface", "-vespera-ground",
            "-fx-control-inner-background");

    /** The light theme's two backgrounds the faint text stands on: the surface and the ground. */
    private static final List<String> LIGHT_BACKGROUNDS = List.of("-vespera-surface", "-vespera-ground");

    private static final Pattern LIGHT_BLOCK = Pattern.compile("(?ms)^\\.root \\{(.*?)^\\}");

    private static final Pattern DARK_BLOCK = Pattern.compile("(?ms)^\\.root\\.dark, \\.dark-dialog \\{(.*?)^\\}");

    private static final Pattern A_COLOUR = Pattern.compile("(?m)^\\s*(-[a-z-]+):\\s*(#[0-9a-fA-F]{6});");

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    @Test
    @Story("The operator switches the control panel between a light and a dark theme")
    @DisplayName("A switch at the header's right, between Light and Dark under the word Theme, opens light and moves the window between the two")
    void theSwitchStandsAtTheHeadersRightBetweenLightAndDarkAndTheWindowOpensLight(@TempDir Path folder)
            throws Exception {
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            Placed placed = window.read(TheControlPanelsThemeTest::placed);
            boolean darkAsOpened = window.read(TheControlPanelsThemeTest::dark);
            boolean switchOnAsOpened = window.read(root -> toggle(root).isSelected());
            List<Boolean> darkAfterEach = new ArrayList<>();
            window.act(root -> toggle(root).fire());
            darkAfterEach.add(window.read(TheControlPanelsThemeTest::dark));
            window.act(root -> click(word(root, "Light")));
            darkAfterEach.add(window.read(TheControlPanelsThemeTest::dark));
            window.act(root -> click(word(root, "Dark")));
            darkAfterEach.add(window.read(TheControlPanelsThemeTest::dark));
            boolean switchOnAtTheEnd = window.read(root -> toggle(root).isSelected());

            claim("the switch's box is the last part of the header that holds the corpus root and the working"
                            + " directory, and its right edge stands at the header's, within a pixel",
                    () -> {
                        assertThat(placed.lastOfTheHeader()).as("whether the switch's box is the header's last part")
                                .isTrue();
                        assertThat(Math.abs(placed.headerRight() - placed.boxRight()))
                                .as("pixels between the switch's right edge and the header's").isLessThanOrEqualTo(ONE_PIXEL);
                    });
            claim("the box reads Theme above a row of Light, the switch and Dark, in that order, and the switch is named"
                            + " Dark mode to a screen reader",
                    () -> assertThat(placed.words()).as("the box's heading, the row's parts in order, the switch's name")
                            .containsExactly("Theme", "Light", "the switch", "Dark", "Dark mode"));
            claim("a first start opens light: the switch is off and the window carries no dark style",
                    () -> assertThat(List.of(switchOnAsOpened, darkAsOpened)).containsExactly(false, false));
            claim("pressing the switch makes the window dark, clicking Light makes it light again and clicking Dark"
                            + " dark, the switch on at the end",
                    () -> {
                        assertThat(darkAfterEach).as("dark after the switch, after Light, after Dark")
                                .containsExactly(true, false, true);
                        assertThat(switchOnAtTheEnd).as("whether the switch is on at the end").isTrue();
                    });
        }
    }

    @Test
    @Story("The operator switches the control panel between a light and a dark theme")
    @DisplayName("The theme chosen is kept in a file of the user's own and the next window opens in it")
    void theThemeChosenIsKeptBesideTheLaunchValuesAndTheNextWindowOpensInIt(@TempDir Path folder) throws Exception {
        ControlPanelState first = WindowForTests.aState(folder, aRuntime(folder));
        Path theme = Files.createDirectories(launchValuesFolder(folder)).resolve(THEME_FILE);

        try (WindowForTests window = WindowForTests.shown(first)) {
            window.act(root -> toggle(root).fire());
        }
        String keptDark = Files.readString(theme, UTF_8);
        List<Boolean> secondAsOpened;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            secondAsOpened = window.read(root -> List.of(dark(root), toggle(root).isSelected()));
            window.act(root -> toggle(root).fire());
        }
        String keptLight = Files.readString(theme, UTF_8);
        List<Boolean> thirdAsOpened;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            thirdAsOpened = window.read(root -> List.of(dark(root), toggle(root).isSelected()));
        }

        claim("the dark theme chosen is written as dark in the theme file beside the launch values",
                () -> assertThat(keptDark).isEqualTo("dark"));
        claim("the next window opens dark, its switch on",
                () -> assertThat(secondAsOpened).as("dark, switch on").containsExactly(true, true));
        claim("the light theme chosen there is written as light in the same file",
                () -> assertThat(keptLight).isEqualTo("light"));
        claim("and the window after it opens light, its switch off",
                () -> assertThat(thirdAsOpened).as("dark, switch on").containsExactly(false, false));
    }

    @Test
    @Story("The operator switches the control panel between a light and a dark theme")
    @DisplayName("The theme is kept nowhere where the folder is missing, is not a folder, or others can write in it")
    void theThemeIsKeptNowhereWhereTheFolderIsAbsentOrNotTheUsersOwn(@TempDir Path folder) throws IOException {
        Path absent = folder.resolve("absent");
        ControlPanelState overAbsent = WindowForTests.aState(absent, aRuntime(folder));
        overAbsent.keepDark(true);

        Path notAFolder = folder.resolve("not-a-folder");
        Path standingInItsPlace = launchValuesFolder(notAFolder);
        Files.createDirectories(standingInItsPlace.getParent());
        Files.writeString(standingInItsPlace, "", UTF_8);
        ControlPanelState overAFile = WindowForTests.aState(notAFolder, aRuntime(folder));
        overAFile.keepDark(true);

        claim("where the launch values' folder does not exist, choosing dark makes no folder and writes no file, and the"
                        + " next start reads light",
                () -> {
                    assertThat(launchValuesFolder(absent)).doesNotExist();
                    assertThat(overAbsent.darkChosen()).as("dark read back").isFalse();
                });
        claim("where a file stands in the folder's place, choosing dark leaves it as it was and writes nothing beside it",
                () -> {
                    assertThat(Files.readString(standingInItsPlace, UTF_8)).isEmpty();
                    assertThat(namesIn(standingInItsPlace.getParent())).containsExactly(standingInItsPlace
                            .getFileName().toString());
                });

        Assumptions.assumeThat(POSIX).as("a folder others can write in is told apart only where POSIX permissions apply")
                .isTrue();
        Path shared = folder.resolve("shared");
        Path sharedFolder = Files.createDirectories(launchValuesFolder(shared));
        Files.setPosixFilePermissions(sharedFolder, PosixFilePermissions.fromString("rwxrwxrwx"));
        WindowForTests.aState(shared, aRuntime(folder)).keepDark(true);

        claim("where the folder exists and others can write in it, choosing dark writes nothing there",
                () -> assertThat(namesIn(sharedFolder)).isEmpty());
    }

    @Test
    @Story("The operator switches the control panel between a light and a dark theme")
    @DisplayName("Every dark text colour, and the light theme's faint text, reads at 4.5 : 1 or more against its theme's backgrounds")
    void everyDarkTokenAndTheLightFaintTextStandAtWcagAaAgainstTheirBackgrounds() throws IOException {
        String stylesheet = stylesheet();
        Map<String, Color> light = coloursOf(LIGHT_BLOCK, stylesheet);
        Map<String, Color> dark = coloursOf(DARK_BLOCK, stylesheet);
        List<String> lightTokens = light.keySet().stream().filter(name -> name.startsWith("-vespera-")).toList();
        List<String> darkTokens = dark.keySet().stream().filter(name -> name.startsWith("-vespera-")).toList();

        List<String> darkBelowAa = new ArrayList<>();
        for (String text : TEXT_TOKENS) {
            for (String background : DARK_BACKGROUNDS) {
                belowAa(dark, text, background).ifPresent(darkBelowAa::add);
            }
        }
        belowAa(dark, "-vespera-accent-ink", "-vespera-accent").ifPresent(darkBelowAa::add);
        List<String> lightFaintBelowAa = new ArrayList<>();
        for (String background : LIGHT_BACKGROUNDS) {
            belowAa(light, "-vespera-faint", background).ifPresent(lightFaintBelowAa::add);
        }

        claim("the dark theme redefines each of the light theme's colour tokens, the same names in the same order, and"
                        + " no other",
                () -> assertThat(darkTokens).as("the dark theme's tokens").isNotEmpty()
                        .containsExactlyElementsOf(lightTokens));
        claim("in the dark theme the text, dim text, accent, error and faint text colours each read at 4.5 : 1 or more"
                        + " against the surface, the ground and a control's inside, and the text on the accent against"
                        + " the accent",
                () -> assertThat(darkBelowAa).as("the dark pairs below 4.5 : 1").isEmpty());
        claim("in the light theme the faint text reads at 4.5 : 1 or more against the surface and the ground",
                () -> assertThat(lightFaintBelowAa).as("the light faint pairs below 4.5 : 1").isEmpty());
    }

    @Test
    @Story("The operator switches the control panel between a light and a dark theme")
    @DisplayName("A dialog over the window takes its stylesheet, and over a dark window the dark colours")
    void aDialogOverTheWindowTakesItsStylesheetAndOverADarkWindowTheDarkTokens(@TempDir Path folder) throws Exception {
        Map<String, Shown> shown = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            Dialogs dialogs = Dialogs.of(() -> window.stage, address -> { });
            List<String> windowsStylesheets = window.read(root -> List.copyOf(root.getStylesheets()));
            shown.put("light", HeadlessToolkit.onTheToolkit(() -> askedOver(window.stage, dialogs)));
            window.act(root -> toggle(root).fire());
            shown.put("dark", HeadlessToolkit.onTheToolkit(() -> askedOver(window.stage, dialogs)));
            Color darkSurface = coloursOf(DARK_BLOCK, stylesheet()).get("-vespera-surface");

            claim("over the light window and over the dark one, the dialog shows and its pane carries exactly the"
                            + " window's stylesheet",
                    () -> {
                        assertThat(windowsStylesheets).as("the window's stylesheets").hasSize(1);
                        assertThat(shown.get("light").stylesheets()).as("the light window's dialog")
                                .containsExactlyElementsOf(windowsStylesheets);
                        assertThat(shown.get("dark").stylesheets()).as("the dark window's dialog")
                                .containsExactlyElementsOf(windowsStylesheets);
                    });
            claim("over the dark window the pane carries the dark dialog's style and is drawn on the dark surface;"
                            + " over the light window it carries neither",
                    () -> {
                        assertThat(List.of(shown.get("light").darkStyled(), shown.get("dark").darkStyled()))
                                .as("dark styled, light then dark").containsExactly(false, true);
                        assertThat(shown.get("dark").background()).as("the dark window's dialog's background")
                                .isEqualTo(darkSurface);
                        assertThat(shown.get("light").background()).as("the light window's dialog's background")
                                .isNotEqualTo(darkSurface);
                    });
        }
    }

    // ---- the window

    /** Where the switch stands and what its box reads. */
    private record Placed(boolean lastOfTheHeader, double headerRight, double boxRight, List<String> words) {
    }

    private static Placed placed(Parent root) {
        ToggleButton toggle = toggle(root);
        Parent row = toggle.getParent();
        Parent box = row.getParent();
        Parent header = WindowForTests.node(root, "where", Node.class).getParent().getParent();
        Region headerRegion = (Region) header;
        Bounds headerBounds = header.localToScene(header.getLayoutBounds());
        Bounds boxBounds = box.localToScene(box.getLayoutBounds());
        List<String> words = new ArrayList<>();
        words.add(textOf(box.getChildrenUnmodifiable().getFirst()));
        row.getChildrenUnmodifiable().forEach(part -> words.add(part == toggle ? "the switch" : textOf(part)));
        words.add(toggle.getAccessibleText());
        boolean last = header.getChildrenUnmodifiable().getLast() == box;
        return new Placed(last, headerBounds.getMaxX() - headerRegion.getInsets().getRight(), boxBounds.getMaxX(),
                words);
    }

    private static String textOf(Node part) {
        return part instanceof TextInputControl text ? text.getText() : part.getClass().getSimpleName();
    }

    private static ToggleButton toggle(Parent root) {
        return WindowForTests.node(root, "theme-toggle", ToggleButton.class);
    }

    /** The word of the switch's row that reads {@code text}. */
    private static Node word(Parent root, String text) {
        return toggle(root).getParent().getChildrenUnmodifiable().stream()
                .filter(part -> part instanceof TextInputControl words && text.equals(words.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the switch's row holds no word " + text));
    }

    private static void click(Node node) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1, false,
                false, false, false, true, false, false, true, false, false, null));
    }

    private static boolean dark(Parent root) {
        return root.getStyleClass().contains(DARK);
    }

    private static ChildRuntime aRuntime(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }

    /** The folder {@link WindowForTests#aState} names for the launch values under {@code folder}. */
    private static Path launchValuesFolder(Path folder) {
        return LaunchValues.fileIn(folder.resolve("tmp"), USER).getParent();
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (var names = Files.list(folder)) {
            return names.map(name -> name.getFileName().toString()).sorted().toList();
        }
    }

    // ---- the dialog

    /** What a dialog's pane showed: its stylesheets, whether it carried the dark dialog's style, its background. */
    private record Shown(List<String> stylesheets, boolean darkStyled, Paint background) {
    }

    /**
     * Asks a question through {@code dialogs} over {@code owner} and reads its pane while it shows, then answers it with
     * the answer that does not go on; run on the toolkit's thread, where the dialog's own loop runs the reading.
     */
    private static Shown askedOver(Window owner, Dialogs dialogs) {
        List<Shown> read = new ArrayList<>();
        readWhenShown(owner, read, 0);
        dialogs.confirm("A question", "Go on", "Keep");
        if (read.isEmpty()) {
            throw new AssertionError("no dialog showed over the window");
        }
        return read.getFirst();
    }

    /** How many turns of the toolkit the reading waits for the dialog to show. */
    private static final int TURNS = 100;

    private static void readWhenShown(Window owner, List<Shown> read, int turn) {
        Platform.runLater(() -> {
            Optional<DialogPane> pane = Window.getWindows().stream().filter(each -> each != owner && each.isShowing())
                    .filter(each -> each.getScene() != null)
                    .flatMap(each -> HeadlessToolkit.everyNodeUnder(each.getScene().getRoot()).stream())
                    .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst();
            if (pane.isEmpty()) {
                if (turn < TURNS) {
                    readWhenShown(owner, read, turn + 1);
                }
                return;
            }
            DialogPane dialog = pane.get();
            dialog.applyCss();
            Background background = dialog.getBackground();
            Paint fill = background == null || background.getFills().isEmpty() ? null
                    : background.getFills().getFirst().getFill();
            read.add(new Shown(List.copyOf(dialog.getStylesheets()), dialog.getStyleClass().contains(DARK_DIALOG),
                    fill));
            ButtonType keep = dialog.getButtonTypes().stream().filter(type -> "Keep".equals(type.getText()))
                    .findFirst().orElseThrow();
            ((Button) dialog.lookupButton(keep)).fire();
        });
    }

    // ---- the stylesheet

    private static String stylesheet() throws IOException {
        try (InputStream in = ControlPanelWindow.class.getResourceAsStream("control-panel.css")) {
            if (in == null) {
                throw new AssertionError("the control panel's stylesheet is not beside its classes");
            }
            return new String(in.readAllBytes(), UTF_8);
        }
    }

    /** Each colour the rule {@code block} finds in {@code stylesheet} sets, by its name, in the rule's order. */
    private static Map<String, Color> coloursOf(Pattern block, String stylesheet) {
        Matcher rule = block.matcher(stylesheet);
        if (!rule.find()) {
            throw new AssertionError("the stylesheet holds no rule " + block.pattern());
        }
        Map<String, Color> colours = new LinkedHashMap<>();
        Matcher colour = A_COLOUR.matcher(rule.group(1));
        while (colour.find()) {
            colours.put(colour.group(1), Color.web(colour.group(2)));
        }
        return colours;
    }

    /** The pair {@code text} on {@code background}, named with its ratio, where it reads below AA; empty where it does not. */
    private static Optional<String> belowAa(Map<String, Color> theme, String text, String background) {
        Color ink = theme.get(text);
        Color ground = theme.get(background);
        if (ink == null || ground == null) {
            return Optional.of(text + " on " + background + ": not set as a colour");
        }
        double ratio = contrast(ink, ground);
        return ratio >= AA ? Optional.empty()
                : Optional.of(text + " on " + background + " at " + Math.round(ratio * 100) / 100.0 + " : 1");
    }

    /** WCAG 2's contrast ratio of two colours. */
    private static double contrast(Color one, Color other) {
        double lighter = Math.max(luminance(one), luminance(other));
        double darker = Math.min(luminance(one), luminance(other));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color colour) {
        return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen())
                + 0.0722 * linear(colour.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
