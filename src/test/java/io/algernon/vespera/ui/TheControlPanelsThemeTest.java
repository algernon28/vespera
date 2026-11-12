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
import java.math.BigDecimal;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
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
 * tokens (§4). And every view inside the window following the theme (ADR-374): every colour a token of the
 * stylesheet's two theme rules, none set by a class, each at its contrast in both themes (§2, §4, §5), and each view's
 * colours, a tooltip's and a context menu's showing over the window among them, moving with the switch at once (§1, §3,
 * §6).
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

    private static final Pattern A_COLOUR = Pattern.compile("(?m)^\\s*(-[a-z0-9-]+):\\s*(#[0-9a-fA-F]{6});");

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    /** The least contrast WCAG 2.1's 1.4.11 asks of a graphical object needed to read a chart. */
    private static final double MARKS = 3.0;

    /** How many lines a chart of the window draws at most, each in a series colour of its own. */
    private static final int SERIES = 5;

    /** The series colours, one for each line a chart draws at most, in the order the lines take them. */
    private static final List<String> SERIES_TOKENS = List.of("-vespera-series-1", "-vespera-series-2",
            "-vespera-series-3", "-vespera-series-4", "-vespera-series-5");

    /** The marks held at 3 : 1: the series colours and a bar's fill, the accent. */
    private static final List<String> MARK_TOKENS = Stream.concat(SERIES_TOKENS.stream(), Stream.of("-vespera-accent"))
            .toList();

    /** The backgrounds a mark stands on in either theme: the surface of a plot or a track, and the window's ground. */
    private static final List<String> MARK_BACKGROUNDS = List.of("-vespera-surface", "-vespera-ground");

    /** The two theme rules, by the selector each is written under; every colour is written in one of them. */
    private static final List<String> THEME_SELECTORS = List.of(".root", ".root.dark, .dark-dialog");

    private static final Pattern A_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    private static final Pattern A_DECLARATION = Pattern.compile("([A-Za-z_-][A-Za-z0-9_-]*)\\s*:\\s*([^;]+);");

    /** A declaration of a colour: of a background, a border, a fill, a stroke or a text, or an effect drawn in one. */
    private static final Pattern A_COLOUR_PROPERTY = Pattern.compile("-fx-(?:[a-z-]*-)?(?:color|fill|stroke)|-fx-effect");

    /** The words of a colour's value that name no colour: a number, an effect's or a gradient's own words. */
    private static final Pattern NO_COLOUR = Pattern.compile("-?[0-9.]+(?:px|em|%)?|dropshadow|innershadow|gaussian"
            + "|one-pass-box|two-pass-box|three-pass-box|linear-gradient|to|left|right|top|bottom");

    /** What a class of the control panel would write to set a colour of its own (ADR-374 §2). */
    private static final Map<String, Pattern> A_COLOUR_IN_CODE = Map.of(
            "names javafx.scene.paint or javafx.scene.effect", Pattern.compile("javafx\\.scene\\.(?:paint|effect)\\b"),
            "calls setStyle", Pattern.compile("\\bsetStyle\\s*\\("),
            "holds -fx- in a string", Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*-fx-(?:[^\"\\\\\\n]|\\\\.)*\""),
            "holds a hexadecimal colour in a string",
            Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b(?:[^\"\\\\\\n]|\\\\.)*\""));

    /** The control panel's classes, every one read for a colour of its own. */
    private static final Path THE_CONTROL_PANELS_CLASSES = Path.of("src", "main", "java", "io", "algernon", "vespera",
            "ui");

    /** The analysis report whose native view the window test opens, a section of it and its table's rows. */
    private static final String AN_ANALYSIS_REPORT = "format-mix";

    private static final String ITS_SECTION = "by-band";

    /** The row of the native view's table the test selects, and one that is not selected and not an alternate row. */
    private static final int SELECTED_ROW = 0;

    private static final int AN_UNSELECTED_ROW = 2;

    /** The leg whose summary the window test reads, and the step whose bar it reads, running at 375 of 1,000. */
    private static final String A_LEG = "deduplication";

    private static final String A_STEP = "extraction";

    /** The look-up key under which {@link Tooltip#install} keeps a node's tooltip among its properties. */
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";

    /**
     * Each colour the window test reads, by what it is, and the token it is drawn in (ADR-374 §3, §6); the five lines
     * of a chart and the two series of an analysis report's bar chart are added in order after these.
     */
    private static final Map<String, String> DRAWN_IN = drawnIn();

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
    @DisplayName("The theme is kept nowhere where the folder is missing, is not a folder, or others can write in it, and is not read from a folder others can write in")
    void theThemeIsKeptNowhereAndReadFromNowhereWhereTheFolderIsAbsentOrNotTheUsersOwn(@TempDir Path folder) throws IOException {
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
        ControlPanelState overShared = WindowForTests.aState(shared, aRuntime(folder));
        overShared.keepDark(true);

        claim("where the folder exists and others can write in it, choosing dark writes nothing there",
                () -> assertThat(namesIn(sharedFolder)).isEmpty());

        Files.writeString(sharedFolder.resolve("theme"), "dark", UTF_8);

        claim("and a theme file reading dark that another user left in that folder is not read: the window opens light",
                () -> assertThat(overShared.darkChosen()).as("dark read back").isFalse());
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

    @Test
    @Link(name = "ADR-374", url = Adr.EVERY_VIEW_OF_THE_CONTROL_PANELS_WINDOW_FOLLOWS_ITS_THEME, type = "adr")
    @Story("Every view of the window follows its theme")
    @DisplayName("Every colour of the window is one of its stylesheet's two themes' colours, and each reads at its contrast in both themes")
    void everyColourOfTheWindowIsAThemeTokenAndStandsAtItsContrastInBothThemes() throws IOException {
        List<String> setInCode = coloursSetInTheClasses();
        String stylesheet = stylesheet();
        Map<String, Map<String, String>> themes = themes(stylesheet);
        List<String> writtenOutsideTheThemes = coloursWrittenOutsideTheThemeRules(stylesheet,
                themes.get("dark").keySet());

        Map<String, List<String>> seriesByTheme = new LinkedHashMap<>();
        List<String> marksBelowThreeToOne = new ArrayList<>();
        List<String> textBelowAa = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> theme : themes.entrySet()) {
            Map<String, String> declared = theme.getValue();
            seriesByTheme.put(theme.getKey(), SERIES_TOKENS.stream()
                    .map(token -> resolved(declared, token).map(Color::toString).orElse(token + " not set")).toList());
            for (String mark : MARK_TOKENS) {
                for (String background : MARK_BACKGROUNDS) {
                    below(declared, mark, background, MARKS).ifPresent(why -> marksBelowThreeToOne.add(theme.getKey()
                            + " " + why));
                }
            }
            for (String text : TEXT_TOKENS) {
                for (String background : DARK_BACKGROUNDS) {
                    below(declared, text, background, AA).ifPresent(why -> textBelowAa.add(theme.getKey() + " " + why));
                }
            }
            below(declared, "-vespera-accent-ink", "-vespera-accent", AA)
                    .ifPresent(why -> textBelowAa.add(theme.getKey() + " " + why));
            below(declared, "-vespera-ink", "-vespera-rule", AA)
                    .ifPresent(why -> textBelowAa.add(theme.getKey() + " " + why));
        }

        claim("no class of the control panel sets a colour of its own: none names javafx.scene.paint or"
                        + " javafx.scene.effect, calls setStyle, or holds -fx- or a hexadecimal colour in a string",
                () -> assertThat(setInCode).as("the classes and what each writes").isEmpty());
        claim("no rule of the stylesheet but its light and its dark theme writes a colour of its own: every colour any"
                        + " other declares names a colour the two themes set, or is transparent",
                () -> assertThat(writtenOutsideTheThemes).as("the rules and the colours each writes").isEmpty());
        claim("each theme defines five series colours, one for each line a chart draws at most, no two alike",
                () -> {
                    assertThat(seriesByTheme.get("light")).as("the light theme's series").hasSize(SERIES)
                            .doesNotHaveDuplicates().noneMatch(colour -> colour.endsWith("not set"));
                    assertThat(seriesByTheme.get("dark")).as("the dark theme's series").hasSize(SERIES)
                            .doesNotHaveDuplicates().noneMatch(colour -> colour.endsWith("not set"));
                });
        claim("in the light theme and in the dark, each series colour and the accent, a bar's fill, reads at 3 : 1 or"
                        + " more against the surface and the ground",
                () -> assertThat(marksBelowThreeToOne).as("the marks below 3 : 1").isEmpty());
        claim("in the light theme and in the dark, the text, dim text, accent, error and faint text colours each read at"
                        + " 4.5 : 1 or more against the surface, the ground and a control's inside; the text on the"
                        + " accent against the accent; and the text against the rule, a row selected in a view not"
                        + " focused",
                () -> assertThat(textBelowAa).as("the text pairs below 4.5 : 1").isEmpty());
    }

    @Test
    @Link(name = "ADR-374", url = Adr.EVERY_VIEW_OF_THE_CONTROL_PANELS_WINDOW_FOLLOWS_ITS_THEME, type = "adr")
    @Story("Every view of the window follows its theme")
    @DisplayName("The charts, the steps' bars, a report's own view, and a tooltip and a context menu showing over the window take the theme as the switch flips, nothing reopened")
    void everyViewOfTheWindowAndAPopupShowingOverItTakeTheThemeAsTheSwitchFlips(@TempDir Path folder)
            throws Exception {
        Map<String, Map<String, String>> themes = themes(stylesheet());
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, aRuntime(folder)))) {
            Popups popups = window.read(TheControlPanelsThemeTest::drawnAndShown);
            Map<String, Paint> asOpened = window.read(root -> drawn(root, popups));
            window.act(root -> toggle(root).fire());
            Map<String, Paint> afterTheSwitch = window.read(root -> drawn(root, popups));
            window.act(root -> toggle(root).fire());
            Map<String, Paint> afterItsReturn = window.read(root -> drawn(root, popups));
            window.act(root -> popups.hide());

            claim("as the window opens light, the plot, the chart's title, its time axis's title and ticks, its legend"
                            + " and the legend's first name, its five lines, the two series' bars of a report's own view,"
                            + " the running step's bar, track and its percent's halo, the leg's summary and its name, the"
                            + " selected row of a focused table and its text, a row not selected and its text, a"
                            + " sample's tooltip and its text, and the table's context menu, its item under the keys and"
                            + " the item's text are each drawn in its light colour",
                    () -> assertThat(notDrawnIn(asOpened, themes.get("light")))
                            .as("what is not drawn in its light colour").isEmpty());
            claim("once the switch is pressed, nothing reopened and the tooltip and the context menu still showing,"
                            + " each is drawn in its dark colour",
                    () -> assertThat(notDrawnIn(afterTheSwitch, themes.get("dark")))
                            .as("what is not drawn in its dark colour").isEmpty());
            claim("and once it is pressed back, each is drawn in its light colour again",
                    () -> assertThat(notDrawnIn(afterItsReturn, themes.get("light")))
                            .as("what is not drawn in its light colour").isEmpty());
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

    // ---- every view

    /** The tooltip and the context menu the window test shows over the window and keeps showing across the switch. */
    private record Popups(Tooltip tooltip, ContextMenu menu) {

        void hide() {
            tooltip.hide();
            menu.hide();
        }
    }

    private static Map<String, String> drawnIn() {
        Map<String, String> drawn = new LinkedHashMap<>();
        drawn.put("the plot", "-vespera-surface");
        drawn.put("the chart's title", "-vespera-ink");
        drawn.put("the time axis's title", "-vespera-ink");
        drawn.put("the time axis's ticks", "-vespera-dim");
        drawn.put("the legend", "-vespera-surface");
        drawn.put("the legend's first name", "-vespera-ink");
        drawn.put("the step's bar", "-vespera-accent");
        drawn.put("the step's track", "-vespera-surface");
        drawn.put("the step's percent's halo", "-vespera-surface");
        drawn.put("the leg's summary", "-vespera-surface");
        drawn.put("the leg's name", "-vespera-ink");
        drawn.put("the selected row", "-vespera-accent");
        drawn.put("the selected row's text", "-vespera-accent-ink");
        drawn.put("a row not selected", "-fx-control-inner-background");
        drawn.put("a row not selected's text", "-vespera-ink");
        drawn.put("the tooltip", "-vespera-surface");
        drawn.put("the tooltip's text", "-vespera-ink");
        drawn.put("the context menu", "-vespera-surface");
        drawn.put("the context menu's item under the keys", "-vespera-accent");
        drawn.put("the context menu's item's text", "-vespera-accent-ink");
        for (int line = 1; line <= SERIES; line++) {
            drawn.put("line " + line, SERIES_TOKENS.get(line - 1));
        }
        drawn.put("the bars of series 1", SERIES_TOKENS.get(0));
        drawn.put("the bars of series 2", SERIES_TOKENS.get(1));
        return drawn;
    }

    /**
     * Hands the window a reading of a chart of five lines and of the steps at extraction running 375 of 1,000, opens an
     * analysis report's native view in a tab, scrolled as the window opens one, selects a row of its table and focuses the table,
     * and shows a sample's tooltip and the table's context menu over the window, its item under the keys.
     */
    @SuppressWarnings("unchecked")
    private static Popups drawnAndShown(Parent root) {
        InvocationReportFile.Report reading = aReading();
        LineChart<Number, Number> chart = WindowForTests.node(root, "verdicts-chart", LineChart.class);
        ((ProgressCharts) chart.getParent()).show(reading);
        WindowForTests.node(root, "steps", StepsChart.class).show(reading);
        VBox view = AnalysisReportView.of(AN_ANALYSIS_REPORT, anAnalysisReport(), AN_ANALYSIS_REPORT + ".html",
                name -> { }, address -> { });
        TabPane tabs = HeadlessToolkit.tabWithId(root, LiveReport.ID).orElseThrow().getTabPane();
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        Tab tab = new Tab("Format mix", scroll);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        root.applyCss();
        root.layout();
        TableView<?> table = table(root);
        table.getSelectionModel().select(SELECTED_ROW);
        table.requestFocus();
        Node sample = chart.lookupAll(".sample").stream().findFirst()
                .orElseThrow(() -> new AssertionError("the chart draws no sample"));
        if (!(sample.getProperties().get(TOOLTIP_KEY) instanceof Tooltip tooltip)) {
            throw new AssertionError("the chart's sample holds no tooltip");
        }
        tooltip.show(sample, 0, 0);
        ContextMenu menu = table.getContextMenu();
        // as a control shows its context menu where it is asked for one, over the control
        menu.show(table, 0, 0);
        Node item = menu.getSkin().getNode().lookup(".menu-item");
        if (item == null) {
            throw new AssertionError("the table's context menu shows no item");
        }
        item.requestFocus();
        return new Popups(tooltip, menu);
    }

    /** Each colour of {@link #DRAWN_IN} as the window and its two popups are drawn now, by what it is. */
    private static Map<String, Paint> drawn(Parent root, Popups popups) {
        popups.tooltip().getScene().getRoot().applyCss();
        popups.menu().getScene().getRoot().applyCss();
        Map<String, Paint> drawn = new LinkedHashMap<>();
        LineChart<?, ?> chart = WindowForTests.node(root, "verdicts-chart", LineChart.class);
        drawn.put("the plot", innermost(chart.lookup(".chart-plot-background")));
        drawn.put("the chart's title", inkOf(chart.lookup(".chart-title")));
        drawn.put("the time axis's title", inkOf(chart.getXAxis().lookup(".axis-label")));
        drawn.put("the time axis's ticks", chart.getXAxis().getTickLabelFill());
        drawn.put("the legend", innermost(chart.lookup(".chart-legend")));
        drawn.put("the legend's first name", inkOf(chart.lookup(".chart-legend-item")));
        ProgressBar bar = WindowForTests.every(root, "step-" + A_STEP, ProgressBar.class).getFirst();
        drawn.put("the step's bar", innermost(bar.lookup(".bar")));
        drawn.put("the step's track", innermost(bar.lookup(".track")));
        Node percent = WindowForTests.node(root, "step-percent-" + A_STEP, Node.class);
        drawn.put("the step's percent's halo", percent.getEffect() instanceof DropShadow halo ? halo.getColor() : null);
        drawn.put("the leg's summary", innermost(WindowForTests.node(root, "leg-" + A_LEG, Node.class).lookup(".title")));
        drawn.put("the leg's name", inkOf(WindowForTests.node(root, "leg-name-" + A_LEG, Node.class)));
        TableView<?> table = table(root);
        drawn.put("the selected row", innermost(row(table, SELECTED_ROW)));
        drawn.put("the selected row's text", inkOf(firstCell(row(table, SELECTED_ROW))));
        drawn.put("a row not selected", innermost(row(table, AN_UNSELECTED_ROW)));
        drawn.put("a row not selected's text", inkOf(firstCell(row(table, AN_UNSELECTED_ROW))));
        Node tooltip = popups.tooltip().getSkin().getNode();
        drawn.put("the tooltip", innermost(tooltip));
        drawn.put("the tooltip's text", inkOf(tooltip));
        Node menu = popups.menu().getSkin().getNode();
        Node item = menu.lookup(".menu-item");
        drawn.put("the context menu", innermost(menu));
        drawn.put("the context menu's item under the keys", innermost(item));
        drawn.put("the context menu's item's text", item == null ? null : inkOf(item.lookup(".label")));
        for (int line = 1; line <= SERIES; line++) {
            String colour = "default-color" + (line - 1);
            drawn.put("line " + line, chart.lookupAll(".chart-series-line").stream()
                    .filter(each -> each.getStyleClass().contains(colour) && each instanceof Shape)
                    .map(each -> ((Shape) each).getStroke()).findFirst().orElse(null));
        }
        Node bars = WindowForTests.node(root, "chart-" + AN_ANALYSIS_REPORT + "-" + ITS_SECTION, Node.class);
        for (int series = 1; series <= 2; series++) {
            String colour = "default-color" + (series - 1);
            drawn.put("the bars of series " + series, bars.lookupAll(".chart-bar").stream()
                    .filter(each -> each.getStyleClass().contains(colour)
                            && !each.getStyleClass().contains("chart-legend-item-symbol"))
                    .findFirst().map(TheControlPanelsThemeTest::innermost).orElse(null));
        }
        return drawn;
    }

    /** What of {@code drawn} is not drawn in the colour {@link #DRAWN_IN} gives it in {@code theme}, and in what it is. */
    private static List<String> notDrawnIn(Map<String, Paint> drawn, Map<String, String> theme) {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> each : DRAWN_IN.entrySet()) {
            Optional<Color> wanted = resolved(theme, each.getValue());
            Paint seen = drawn.get(each.getKey());
            if (wanted.isEmpty()) {
                wrong.add(each.getKey() + ": " + each.getValue() + " is not set as a colour");
            } else if (!wanted.get().equals(seen)) {
                wrong.add(each.getKey() + ": drawn in " + seen + ", not " + each.getValue() + ", " + wanted.get());
            }
        }
        return wrong;
    }

    /** The innermost of the fills {@code node} is drawn on, the last; null where it is drawn on none. */
    private static Paint innermost(Node node) {
        if (!(node instanceof Region region) || region.getBackground() == null
                || region.getBackground().getFills().isEmpty()) {
            return null;
        }
        return region.getBackground().getFills().getLast().getFill();
    }

    /** The colour of {@code node}'s text; null where it is no labelled control. */
    private static Paint inkOf(Node node) {
        return node instanceof Labeled labeled ? labeled.getTextFill() : null;
    }

    private static TableView<?> table(Parent root) {
        return WindowForTests.node(root, "table-" + AN_ANALYSIS_REPORT + "-" + ITS_SECTION, TableView.class);
    }

    private static TableRow<?> row(TableView<?> table, int index) {
        return table.lookupAll(".table-row-cell").stream()
                .filter(each -> each instanceof TableRow<?> row && row.getIndex() == index)
                .map(each -> (TableRow<?>) each).findFirst()
                .orElseThrow(() -> new AssertionError("the table draws no row " + index));
    }

    private static Node firstCell(TableRow<?> row) {
        return row.lookupAll(".table-cell").stream().filter(TableCell.class::isInstance).findFirst().orElse(null);
    }

    /** A reading of a running job: extraction at 375 of 1,000, and the verdicts chart drawing five lines. */
    private static InvocationReportFile.Report aReading() {
        InvocationReportFile.Invocation invocation = new InvocationReportFile.Invocation("20261112T000000Z", 1L,
                "run", Instant.EPOCH, null, "running", null, null, Instant.EPOCH, 1L);
        List<InvocationReportFile.Step> steps = List.of(
                new InvocationReportFile.Step("census", null, "finished", 10L, 10L),
                new InvocationReportFile.Step("byte-level-reduction", null, "finished", null, null),
                new InvocationReportFile.Step(A_STEP, null, "running", 375L, 1000L));
        List<InvocationReportFile.Line> lines = new ArrayList<>();
        for (int line = 1; line <= SERIES; line++) {
            lines.add(new InvocationReportFile.Line("kind-" + line, List.of((double) line, line + 1.0)));
        }
        InvocationReportFile.Chart verdicts = new InvocationReportFile.Chart("verdicts", "Verdicts by kind",
                "Verdicts", List.of(0L, 10L), List.copyOf(lines));
        return new InvocationReportFile.Report(InvocationReportFile.FORMAT, invocation, null, null, null, steps,
                List.of(), List.of(verdicts), List.of(), List.of(), List.of(), null, null, List.of(), List.of());
    }

    /** An analysis report of one section, a bar chart of two series over a table of four rows. */
    private static AnalysisReportFile.Report anAnalysisReport() {
        AnalysisReportFile.Table table = new AnalysisReportFile.Table(List.of(
                new AnalysisReportFile.Column("band", "Band", "text"),
                new AnalysisReportFile.Column("documents", "Documents", "count"),
                new AnalysisReportFile.Column("asked", "Asked", "count")), List.of(
                List.of("low", new BigDecimal("12"), new BigDecimal("3")),
                List.of("middle", new BigDecimal("30"), new BigDecimal("8")),
                List.of("high", new BigDecimal("7"), new BigDecimal("2")),
                List.of("top", new BigDecimal("4"), new BigDecimal("1"))));
        AnalysisReportFile.Chart chart = new AnalysisReportFile.Chart("bars", "band", List.of("documents", "asked"));
        return new AnalysisReportFile.Report(1, AN_ANALYSIS_REPORT, "Format mix", Instant.parse("2026-11-12T10:00:00Z"),
                List.of(), List.of(new AnalysisReportFile.Section(ITS_SECTION, "By band", List.of(), chart, table)));
    }

    // ---- the stylesheet

    /** What each class of the control panel writes to set a colour of its own, named with its file. */
    private static List<String> coloursSetInTheClasses() throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> classes;
        try (Stream<Path> files = Files.list(THE_CONTROL_PANELS_CLASSES)) {
            classes = files.filter(each -> each.toString().endsWith(".java")).sorted().toList();
        }
        if (classes.stream().noneMatch(each -> each.endsWith("ThemeSwitch.java"))) {
            found.add("the control panel's classes are not under " + THE_CONTROL_PANELS_CLASSES);
        }
        for (Path file : classes) {
            String code = Files.readString(file, UTF_8);
            A_COLOUR_IN_CODE.forEach((what, pattern) -> pattern.matcher(code).results()
                    .forEach(match -> found.add(file.getFileName() + " " + what + ": " + match.group())));
        }
        return found;
    }

    /**
     * Each colour a rule of {@code stylesheet} other than its two theme rules writes of its own, named with its rule: a
     * word of a colour's value that is neither a name the two theme rules set, among {@code themes}, nor
     * {@code transparent}.
     */
    private static List<String> coloursWrittenOutsideTheThemeRules(String stylesheet, Set<String> themes) {
        List<String> written = new ArrayList<>();
        Matcher rule = A_RULE.matcher(withoutComments(stylesheet));
        while (rule.find()) {
            String selector = rule.group(1).trim().replaceAll("\\s+", " ");
            if (THEME_SELECTORS.contains(selector)) {
                continue;
            }
            Matcher declaration = A_DECLARATION.matcher(rule.group(2));
            while (declaration.find()) {
                if (!A_COLOUR_PROPERTY.matcher(declaration.group(1)).matches()) {
                    continue;
                }
                for (String word : declaration.group(2).trim().split("[\\s,()]+")) {
                    if (!word.isEmpty() && !themes.contains(word) && !"transparent".equals(word) && !NO_COLOUR.matcher(word).matches()) {
                        written.add(selector + " { " + declaration.group(1) + " }: " + word);
                    }
                }
            }
        }
        return written;
    }

    private static String withoutComments(String stylesheet) {
        return stylesheet.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /** Every declaration the rule {@code block} finds in {@code stylesheet} makes, its value by its name, in order. */
    private static Map<String, String> declaredIn(Pattern block, String stylesheet) {
        Matcher rule = block.matcher(stylesheet);
        if (!rule.find()) {
            throw new AssertionError("the stylesheet holds no rule " + block.pattern());
        }
        Map<String, String> declared = new LinkedHashMap<>();
        Matcher declaration = A_DECLARATION.matcher(withoutComments(rule.group(1)));
        while (declaration.find()) {
            declared.put(declaration.group(1), declaration.group(2).trim());
        }
        return declared;
    }

    /** The light theme's declarations, and the dark theme's: the light ones with the dark rule's put over them. */
    private static Map<String, Map<String, String>> themes(String stylesheet) {
        Map<String, String> light = declaredIn(LIGHT_BLOCK, stylesheet);
        Map<String, String> dark = new LinkedHashMap<>(light);
        dark.putAll(declaredIn(DARK_BLOCK, stylesheet));
        Map<String, Map<String, String>> themes = new LinkedHashMap<>();
        themes.put("light", light);
        themes.put("dark", dark);
        return themes;
    }

    /** The colour {@code name} comes to in {@code declared}, each name it is set to followed; empty where it comes to none. */
    private static Optional<Color> resolved(Map<String, String> declared, String name) {
        Set<String> followed = new HashSet<>();
        String value = name;
        while (followed.add(value) && declared.containsKey(value)) {
            value = declared.get(value);
        }
        if (value.matches("#[0-9a-fA-F]{6}")) {
            return Optional.of(Color.web(value));
        }
        return "transparent".equals(value) ? Optional.of(Color.TRANSPARENT) : Optional.empty();
    }

    /** The pair {@code fore} on {@code back}, named with its ratio, where it reads below {@code least}; empty where not. */
    private static Optional<String> below(Map<String, String> declared, String fore, String back, double least) {
        Optional<Color> one = resolved(declared, fore);
        Optional<Color> other = resolved(declared, back);
        if (one.isEmpty() || other.isEmpty()) {
            return Optional.of(fore + " on " + back + ": not set as a colour");
        }
        double ratio = contrast(one.get(), other.get());
        return ratio >= least ? Optional.empty()
                : Optional.of(fore + " on " + back + " at " + Math.round(ratio * 100) / 100.0 + " : 1");
    }

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
