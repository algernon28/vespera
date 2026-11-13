package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A profile row is one line (ADR-377 §3): its toggle, its title over its key, and its control; behind the toggle its
 * short description, the line of the value in use, its cost line, its warnings and its reports; its open state carried
 * by its key while the working directory stays the same; and the provenance the file states in its title's tooltip
 * alone, no line or field of the row holding it (§1). Each window is shown on the Headless platform at the control
 * panel's 1500 by 960, the Profile tab selected.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("803")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProfileRowIsOneLineTest {

    /** How far apart two places may be and still be the same: a pixel. */
    private static final double ONE_PIXEL = 1.0;

    /** What the toggle reads while its row is closed. */
    private static final String CLOSED = "▸";

    /** What the toggle reads while its row is open. */
    private static final String OPEN = "▾";

    /** The words that open the provenance's part of a title's tooltip. */
    private static final String CAME_FROM = "Where this value came from: ";

    /** A key with a control of its own on the Profile tab, a number, and one report. */
    private static final ProfileKey A_FLOOR = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** A key no test opens. */
    private static final ProfileKey NEVER_OPENED = ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR;

    /** A provenance the operator wrote by hand. */
    private static final String HAND_WRITTEN = "picked off the frequency table";

    /** A text that marks every provenance of the profile, so that no text of the window can hold one unseen. */
    private static final String MARKER = "zq-provenance-marker";

    @Test
    @Story("A profile row is one line")
    @DisplayName("A row's first line holds its toggle, then its title over its key, then its control, and its details are hidden")
    void aRowsFirstLineIsItsToggleItsTitleOverItsKeyAndItsControl(@TempDir Path folder) throws Exception {
        Map<ProfileKey, FirstLine> lines = new LinkedHashMap<>();
        try (WindowForTests window = windowOver(folder, "")) {
            for (ProfileKey key : List.of(ProfileKey.SEED_FOLDER, A_FLOOR)) {
                lines.put(key, window.read(root -> firstLine(root, "profile-", key)));
            }
        }

        lines.forEach((key, line) -> {
            claim("the " + key.key() + " row's toggle is a toggle button reading " + CLOSED + ", left of its title",
                    () -> {
                        assertThat(line.toggleText()).isEqualTo(CLOSED);
                        assertThat(line.toggle().maxX()).isLessThanOrEqualTo(line.title().minX());
                    });
            claim("its title is a one-line selectable text reading the key's title, and its key one reading the key in"
                            + " brackets",
                    () -> {
                        assertThat(line.titleIsALine()).isTrue();
                        assertThat(line.titleText()).isEqualTo(key.title());
                        assertThat(line.keyIsALine()).isTrue();
                        assertThat(line.keyText()).isEqualTo("(" + key.key() + ")");
                    });
            claim("the key stands beneath the title, its top at the title's bottom or below, its left edge the title's"
                            + " within a pixel",
                    () -> {
                        assertThat(line.key().minY()).isGreaterThanOrEqualTo(line.title().maxY() - ONE_PIXEL);
                        assertThat(line.key().minX()).isCloseTo(line.title().minX(), offset(ONE_PIXEL));
                    });
            claim("the control's top lies within the title's height, and its left is right of the title's right",
                    () -> {
                        assertThat(line.control().minY()).isBetween(line.title().minY() - ONE_PIXEL,
                                line.title().maxY());
                        assertThat(line.control().minX()).isGreaterThan(line.title().maxX());
                    });
            claim("its details are neither visible nor managed while it is closed",
                    () -> {
                        assertThat(line.detailsVisible()).isFalse();
                        assertThat(line.detailsManaged()).isFalse();
                    });
        });
    }

    @Test
    @Story("A profile row is one line")
    @DisplayName("Every control of the Profile tab starts at one edge, and no title is cut")
    void everyControlOfTheTabStartsAtOneEdge(@TempDir Path folder) throws Exception {
        Map<String, Double> lefts = new LinkedHashMap<>();
        Map<String, double[]> titleAndLine = new LinkedHashMap<>();
        try (WindowForTests window = windowOver(folder, "")) {
            window.act(root -> {
                for (ProfileKey key : ProfileKey.values()) {
                    FirstLine line = firstLine(root, "profile-", key);
                    lefts.put(key.key(), line.control().minX());
                    TextInputControl title = WindowForTests.node(root, "profile-" + key.key() + ".title",
                            TextInputControl.class);
                    Text measure = new Text(title.getText());
                    measure.setFont(title.getFont());
                    titleAndLine.put(key.key(), new double[] {measure.getLayoutBounds().getWidth(), title.getWidth()});
                }
            });
        }
        double first = lefts.values().iterator().next();

        claim("every key of the profile has a row on the Profile tab, so that a check of one edge cannot pass over none",
                () -> assertThat(lefts).hasSize(ProfileKey.values().length));
        claim("every row's control starts at the same left edge, within a pixel",
                () -> lefts.forEach((key, left) -> assertThat(left).as(key).isCloseTo(first, offset(ONE_PIXEL))));
        claim("no title is cut: each title's text is no wider than the one-line text that shows it, within a pixel",
                () -> titleAndLine.forEach((key, widths) -> assertThat(widths[0]).as(key)
                        .isLessThanOrEqualTo(widths[1] + ONE_PIXEL)));
    }

    @Test
    @Story("A profile row is one line")
    @DisplayName("The toggle opens the row's description, the value in use, the cost, the warnings and the reports, and closes them again")
    void theToggleOpensItsDescriptionInUseLineCostWarningsAndReports(@TempDir Path folder) throws Exception {
        ProfileKey withWarnings = ProfileKey.RELEVANCE_SCORE_FLOOR;
        ProfileKey withAValueInUse = ProfileKey.GENERATION_MODEL;
        String prefix = "profile-" + withWarnings.key();
        String inUsePrefix = "profile-" + withAValueInUse.key();
        Files.createDirectories(folder.resolve("working"));
        Files.writeString(folder.resolve("working").resolve(EffectiveValues.FILE_NAME),
                withAValueInUse.key() + "=qwen3:8b\n" + withAValueInUse.key() + ".source=shipped\n",
                StandardCharsets.UTF_8);
        String[] opened = new String[2];
        boolean[] openedShown = new boolean[2];
        List<String> details = new ArrayList<>();
        List<String> notSelectable = new ArrayList<>();
        String[] closed = new String[1];
        boolean[] closedShown = new boolean[2];
        boolean[] byTheKeyboard = new boolean[2];
        List<String> inUseDetails = new ArrayList<>();
        String[] inUseLine = new String[1];
        try (WindowForTests window = windowOver(folder, "")) {
            window.act(root -> WindowForTests.node(root, prefix + ".more", ToggleButton.class).fire());
            window.act(root -> {
                ToggleButton toggle = WindowForTests.node(root, prefix + ".more", ToggleButton.class);
                Pane box = WindowForTests.node(root, prefix + ".details", Pane.class);
                opened[0] = toggle.getText();
                openedShown[0] = box.isVisible();
                openedShown[1] = box.isManaged();
                for (Node child : box.getChildren()) {
                    details.add(child.getId());
                    Node text = child instanceof Pane pane ? pane.getChildren().getFirst() : child;
                    if (!(text instanceof SelectableText)) {
                        notSelectable.add(child.getId());
                    }
                }
                toggle.fire();
            });
            window.act(root -> {
                closed[0] = WindowForTests.node(root, prefix + ".more", ToggleButton.class).getText();
                Pane box = WindowForTests.node(root, prefix + ".details", Pane.class);
                closedShown[0] = box.isVisible();
                closedShown[1] = box.isManaged();
                ToggleButton keyed = WindowForTests.node(root, inUsePrefix + ".more", ToggleButton.class);
                byTheKeyboard[0] = keyed.isFocusTraversable();
                keyed.requestFocus();
                Event.fireEvent(keyed, new KeyEvent(KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE, false, false, false,
                        false));
                Event.fireEvent(keyed, new KeyEvent(KeyEvent.KEY_RELEASED, " ", " ", KeyCode.SPACE, false, false, false,
                        false));
                byTheKeyboard[1] = keyed.isSelected();
            });
            window.act(root -> {
                Pane box = WindowForTests.node(root, inUsePrefix + ".details", Pane.class);
                box.getChildren().forEach(child -> inUseDetails.add(child.getId()));
                inUseLine[0] = WindowForTests.node(root, inUsePrefix + ".in-use", TextInputControl.class).getText();
            });
        }

        claim("pressed, the toggle reads " + OPEN + " and the details are visible and managed",
                () -> {
                    assertThat(opened[0]).isEqualTo(OPEN);
                    assertThat(openedShown).containsExactly(true, true);
                });
        claim("the details hold, in order, the short description, the cost line, the approval's, the answers' and the"
                        + " new sample's warnings, and the reports, and each text among them is selectable",
                () -> {
                    assertThat(details).containsExactly(prefix + ".short", prefix + ".cost", prefix + ".approval",
                            prefix + ".answers", prefix + ".new-sample", prefix + ".reports");
                    assertThat(notSelectable).isEmpty();
                });
        claim("pressed again, it reads " + CLOSED + " and the details are neither visible nor managed",
                () -> {
                    assertThat(closed[0]).isEqualTo(CLOSED);
                    assertThat(closedShown).containsExactly(false, false);
                });
        claim("the writing model's toggle is reached by the Tab key, and Space opens it",
                () -> assertThat(byTheKeyboard).containsExactly(true, true));
        claim("its details hold the line of the value in use after the short description, and no warning",
                () -> assertThat(inUseDetails).containsExactly(inUsePrefix + ".short", inUsePrefix + ".in-use",
                        inUsePrefix + ".cost"));
        claim("the line of the value in use names the value and says it comes from Vespera's settings",
                () -> assertThat(inUseLine[0]).isEqualTo("Left empty, Vespera uses qwen3:8b (Vespera's settings)."));
    }

    @Test
    @Story("A profile row is one line")
    @DisplayName("A row opened stays open through a Save and a Reset, and is drawn closed for another working directory")
    void aRowStaysOpenThroughASaveAndAResetAndClosesForAnotherWorkingDirectory(@TempDir Path folder) throws Exception {
        String prefix = "profile-" + A_FLOOR.key();
        String profile = ProfileEdit.rewrite(Optional.empty(), A_FLOOR, "0.4", HAND_WRITTEN);
        Path otherWorking = Files.createDirectories(folder.resolve("other-working"));
        Files.writeString(otherWorking.resolve(ProfileEdit.FILE_NAME), profile, StandardCharsets.UTF_8);
        Map<String, Boolean> openAfter = new LinkedHashMap<>();
        Map<String, Boolean> neverOpenedAfter = new LinkedHashMap<>();
        String saved;
        try (WindowForTests window = windowOver(folder, profile)) {
            window.act(root -> WindowForTests.node(root, prefix + ".more", ToggleButton.class).fire());
            window.act(root -> {
                WindowForTests.node(root, prefix, Spinner.class).getEditor().setText("0.5");
                WindowForTests.node(root, "profile-save", Button.class).fire();
            });
            window.act(root -> {
                openAfter.put("a Save that writes", isOpen(root, prefix));
                neverOpenedAfter.put("a Save that writes", isOpen(root, "profile-" + NEVER_OPENED.key()));
                WindowForTests.node(root, "profile-reset", Button.class).fire();
            });
            window.act(root -> {
                openAfter.put("Reset", isOpen(root, prefix));
                neverOpenedAfter.put("Reset", isOpen(root, "profile-" + NEVER_OPENED.key()));
                WindowForTests.node(root, "vespera.working-dir", TextField.class).setText(otherWorking.toString());
            });
            window.act(root -> {
                openAfter.put("another working directory", isOpen(root, prefix));
                neverOpenedAfter.put("another working directory", isOpen(root, "profile-" + NEVER_OPENED.key()));
            });
            saved = Files.readString(folder.resolve("working").resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8);
        }

        claim("the Save wrote the value changed, so that the row was drawn again",
                () -> assertThat(ProfileEdit.read(saved).get(A_FLOOR).value()).isEqualTo("0.5"));
        claim("the row opened is open after the Save and after Reset, and the row drawn for another working directory is"
                        + " closed",
                () -> assertThat(openAfter).containsExactly(Map.entry("a Save that writes", true),
                        Map.entry("Reset", true), Map.entry("another working directory", false)));
        claim("a row never opened is closed throughout",
                () -> assertThat(neverOpenedAfter.values()).containsOnly(false).hasSize(openAfter.size()));
    }

    @Test
    @Story("A value's provenance is in its title's tooltip")
    @DisplayName("The title's tooltip holds the value's explanation, then where the value came from, where the file says")
    void theTitlesTooltipHoldsTheExplanationAndWhereTheValueCameFrom(@TempDir Path folder) throws Exception {
        ProfileKey blank = NEVER_OPENED;
        ProfileKey absent = ProfileKey.RELEVANCE_SCORE_FLOOR;
        String profile = "---\n" + block(A_FLOOR, "0.4", YamlScalars.write(HAND_WRITTEN))
                + block(blank, "20", "\"   \"");
        Map<ProfileKey, String> tooltips = new LinkedHashMap<>();
        try (WindowForTests window = windowOver(folder, profile)) {
            window.act(root -> {
                for (ProfileKey key : List.of(A_FLOOR, blank, absent)) {
                    tooltips.put(key, textOfTooltip(root, "profile-" + key.key() + ".title"));
                }
            });
        }

        claim("the floor's title's tooltip is the key's tooltip text, an empty line, then where its value came from, as"
                        + " the file states it",
                () -> assertThat(tooltips.get(A_FLOOR)).isEqualTo(A_FLOOR.tooltip() + "\n\n" + CAME_FROM + HAND_WRITTEN));
        claim("a key stated with a blank provenance, and one the file does not state, show the key's tooltip text alone",
                () -> {
                    assertThat(tooltips.get(blank)).isEqualTo(blank.tooltip());
                    assertThat(tooltips.get(absent)).isEqualTo(absent.tooltip());
                });
    }

    @Test
    @Story("A value's provenance is in its title's tooltip")
    @DisplayName("No line of a row shows the provenance and no field holds one")
    void noLineOfTheRowShowsTheProvenanceAndNoFieldHoldsOne(@TempDir Path folder) throws Exception {
        StringBuilder profile = new StringBuilder("---\n");
        for (ProfileKey key : ProfileKey.values()) {
            profile.append(block(key, "null", YamlScalars.write(MARKER + " of " + key.key())));
        }
        List<String> texts = new ArrayList<>();
        List<String> rowTexts = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int[] opened = new int[1];
        try (WindowForTests window = windowOver(folder, profile.toString())) {
            window.act(root -> HeadlessToolkit.everyNodeUnder(root).stream().filter(ToggleButton.class::isInstance)
                    .map(ToggleButton.class::cast).filter(toggle -> toggle.getStyleClass().contains("more"))
                    .forEach(toggle -> {
                        toggle.fire();
                        opened[0]++;
                    }));
            window.act(root -> {
                for (Node node : HeadlessToolkit.everyNodeUnder(root)) {
                    ids.add(node.getId());
                    textOf(node).ifPresent(texts::add);
                }
                for (ProfileKey key : ProfileKey.values()) {
                    HeadlessToolkit.everyNodeUnder(WindowForTests.node(root, "profile-row-" + key.key(), Node.class))
                            .stream().filter(SelectableText.class::isInstance)
                            .forEach(node -> rowTexts.add(((TextInputControl) node).getText()));
                }
            });
        }

        claim("every row of the Profile tab was opened, so that its details are walked",
                () -> assertThat(opened[0]).isGreaterThanOrEqualTo(ProfileKey.values().length));
        claim("no text of the window holds the mark every provenance of the profile carries",
                () -> assertThat(texts).noneMatch(text -> text.contains(MARKER)));
        claim("no node of the window is a field of a provenance, its box or a reason for an edit",
                () -> assertThat(ids).filteredOn(Objects::nonNull).noneMatch(id -> id.endsWith(".provenance")
                        || id.endsWith(".provenance-box") || id.endsWith(".edit-reason")));
        claim("no text reads the caption or the hint the provenance's field had",
                () -> assertThat(texts).noneMatch(text -> text.equals("Note saved with it")
                        || text.startsWith("Saved beside the value")));
        claim("no text of a row is its key alone without brackets, and none says where a value is read from",
                () -> {
                    assertThat(rowTexts).isNotEmpty();
                    for (ProfileKey key : ProfileKey.values()) {
                        assertThat(rowTexts).doesNotContain(key.key());
                    }
                    assertThat(rowTexts).noneMatch(text -> text.contains("Read from"));
                });
    }

    @Test
    @Story("A profile row is one line")
    @DisplayName("The Seed folder's row and a wanted value's row are drawn as the Profile tab's rows are")
    void theSeedFolderAndAWantedValueAreDrawnAsTheProfileTabsRows(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=" + A_FLOOR.key() + "\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        FirstLine seed;
        FirstLine wanted;
        String[] tooltips = new String[2];
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the child that ends at once is started").isEmpty();
            boolean drawn = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-" + A_FLOOR.key()).isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
            window.act(root -> {
                TitledPane section = WindowForTests.node(root, "seed-folder", TitledPane.class);
                section.setAnimated(false);
                section.setExpanded(true);
            });
            seed = window.read(root -> firstLine(root, "seed-", ProfileKey.SEED_FOLDER));
            wanted = window.read(root -> firstLine(root, "wanted-", A_FLOOR));
            window.act(root -> {
                tooltips[0] = textOfTooltip(root, "seed-" + ProfileKey.SEED_FOLDER.key() + ".title");
                tooltips[1] = textOfTooltip(root, "wanted-" + A_FLOOR.key() + ".title");
            });
        }

        Map<String, FirstLine> both = new LinkedHashMap<>();
        both.put("Seed folder's", seed);
        both.put("the wanted value's", wanted);
        both.forEach((which, line) -> claim(which + " row has its toggle, its title over its key and its control on one"
                        + " line",
                () -> {
                    assertThat(line.toggle().maxX()).isLessThanOrEqualTo(line.title().minX());
                    assertThat(line.titleIsALine()).isTrue();
                    assertThat(line.keyIsALine()).isTrue();
                    assertThat(line.key().minY()).isGreaterThanOrEqualTo(line.title().maxY() - ONE_PIXEL);
                    assertThat(line.control().minY()).isBetween(line.title().minY() - ONE_PIXEL, line.title().maxY());
                    assertThat(line.control().minX()).isGreaterThan(line.title().maxX());
                }));
        claim("each title's tooltip is its key's tooltip text",
                () -> assertThat(tooltips).containsExactly(ProfileKey.SEED_FOLDER.tooltip(), A_FLOOR.tooltip()));
        claim("Seed folder's row starts closed and the wanted value's row open",
                () -> {
                    assertThat(seed.toggleText()).isEqualTo(CLOSED);
                    assertThat(seed.detailsVisible()).isFalse();
                    assertThat(wanted.toggleText()).isEqualTo(OPEN);
                    assertThat(wanted.detailsVisible()).isTrue();
                });
    }

    // ---- the window and its rows

    /** The window over a working directory holding {@code profile} as its profile.yaml, the Profile tab selected. */
    private static WindowForTests windowOver(Path folder, String profile) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        if (!profile.isEmpty()) {
            Files.writeString(working.resolve(ProfileEdit.FILE_NAME), profile, StandardCharsets.UTF_8);
        }
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

    /** A key's block of profile.yaml, its value and provenance written as given. */
    private static String block(ProfileKey key, String value, String provenance) {
        return key.key() + ":\n  value: " + value + "\n  provenance: " + provenance + "\n  measurement: null\n";
    }

    /** Where a node lies in the scene. */
    private record Placed(double minX, double minY, double maxX, double maxY) {
    }

    /** What a row's first line shows, and whether its details are shown. */
    private record FirstLine(Placed toggle, String toggleText, Placed title, String titleText, boolean titleIsALine,
            Placed key, String keyText, boolean keyIsALine, Placed control, boolean detailsVisible,
            boolean detailsManaged) {
    }

    private static FirstLine firstLine(Parent root, String prefix, ProfileKey key) {
        String id = prefix + key.key();
        ToggleButton toggle = WindowForTests.node(root, id + ".more", ToggleButton.class);
        Node title = WindowForTests.node(root, id + ".title", Node.class);
        Node small = WindowForTests.node(root, id + ".key", Node.class);
        List<Node> line = toggle.getParent().getChildrenUnmodifiable();
        Node details = WindowForTests.node(root, id + ".details", Node.class);
        return new FirstLine(placed(toggle), toggle.getText(), placed(title), ((TextInputControl) title).getText(),
                title instanceof SelectableText.Line, placed(small), ((TextInputControl) small).getText(),
                small instanceof SelectableText.Line, placed(line.getLast()), details.isVisible(), details.isManaged());
    }

    private static boolean isOpen(Parent root, String prefix) {
        return WindowForTests.node(root, prefix + ".more", ToggleButton.class).isSelected()
                && WindowForTests.node(root, prefix + ".details", Node.class).isVisible();
    }

    private static Placed placed(Node node) {
        Bounds at = node.localToScene(node.getLayoutBounds());
        return new Placed(at.getMinX(), at.getMinY(), at.getMaxX(), at.getMaxY());
    }

    private static Tooltip tooltipOf(Node node) {
        return node instanceof javafx.scene.control.Control control ? control.getTooltip() : null;
    }

    private static String textOfTooltip(Parent root, String id) {
        Tooltip tip = tooltipOf(WindowForTests.node(root, id, Node.class));
        return tip == null ? null : tip.getText();
    }

    private static Optional<String> textOf(Node node) {
        if (node instanceof TextInputControl input) {
            return Optional.ofNullable(input.getText());
        }
        if (node instanceof Labeled labeled) {
            return Optional.ofNullable(labeled.getText());
        }
        if (node instanceof Text text) {
            return Optional.ofNullable(text.getText());
        }
        return Optional.empty();
    }
}
