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
import java.util.Optional;
import java.util.function.Function;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.Chart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every text the window shows outside a control is a {@link SelectableText} the operator selects and copies, drawn as
 * the label it replaces was (ADR-364 §8). The window is shown as the control panel shows it, on the Headless platform,
 * and the tests read it as the record says a test does: from the root, through every parent that is neither a control
 * nor a chart, and, of the controls, through a scroll pane's content, a split pane's items, each tab's content, an
 * accordion's panes and a titled pane's content, never its header.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("777")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class EveryTextOfTheWindowCanBeCopiedTest {

    /** How far apart two sizes or places may be and still be the same: a pixel. */
    private static final double ONE_PIXEL = 1.0;

    /** The report whose file the working directory holds in the first window. */
    private static final String A_REPORT_WRITTEN = "document-frequency.html";

    /** A folder name long enough that the header's {@code where} is wider than a window of 700. */
    private static final String A_LONG_FOLDER = "/archive/" + "a-folder-with-a-long-name/".repeat(9) + "corpus";

    /** The window's width where space is tight. */
    private static final double TIGHT_WIDTH = 700;

    /** Where a node lies in the scene, and how large it is. */
    private record Placed(double x, double y, double width, double height) {
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("Every text the window shows outside a control is a selectable text that cannot be edited, and no label with words is left")
    void everyTextShownOutsideAControlIsSelectableText(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("papers"));
        Path elsewhere = Files.createDirectories(folder.resolve("elsewhere"));
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.of(ProfileEdit.rewrite(
                Optional.empty(), ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured on the seed set")),
                ProfileKey.CODE_SET_FOLDERS, elsewhere.toString(), "folders outside the corpus root"),
                StandardCharsets.UTF_8);
        Files.writeString(working.resolve(CodeSetRenames.FILE_NAME),
                CodeSetRenames.render(List.of(new CodeSetRenames.Rename("papers", "Papers"))), StandardCharsets.UTF_8);
        Files.writeString(working.resolve(A_REPORT_WRITTEN), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=boilerplateDocumentFrequencyFloor\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        Path otherFolder = Files.createDirectories(folder.resolve("other"));
        Path otherWorking = Files.createDirectories(otherFolder.resolve("working"));
        Files.writeString(otherWorking.resolve(ProfileEdit.FILE_NAME),
                ProfileEdit.rewrite(Optional.empty(), ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured")
                        + "# a comment the jar does not write\n", StandardCharsets.UTF_8);
        ControlPanelState otherState = WindowForTests.aState(otherFolder, SleepingJar.runtime());
        otherState.hold(new LaunchValues(corpus.toString(), otherWorking.toString(), "", "", "", "", "", ""));

        List<Node> reached;
        List<Node> reachedWhenNotInForm;
        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child that ends at once is started").isEmpty();
            boolean drawn = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-boilerplateDocumentFrequencyFloor").isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
            reached = window.read(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl);
        }
        try (WindowForTests window = WindowForTests.shown(otherState)) {
            reachedWhenNotInForm = window.read(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl);
        }
        List<Node> every = new ArrayList<>(reached);
        every.addAll(reachedWhenNotInForm);
        List<String> labelsWithWords = every.stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> label.getText() != null && !label.getText().isBlank()).map(Label::getText).toList();
        List<Node> selectable = every.stream().filter(SelectableText.class::isInstance).toList();
        List<String> reading = selectable.stream().filter(node -> ((TextInputControl) node).isEditable()
                || node.isFocusTraversable()).map(Node::getId).toList();

        claim("the walk reaches no label whose text is not blank",
                () -> assertThat(labelsWithWords).isEmpty());
        claim("every selectable text it reaches is a text input control that is not editable and is not reached by"
                        + " the Tab key",
                () -> assertThat(reading).isEmpty());
        claim("the walk reaches the header's where and child, the outcome, a step's state, a profile row's title, a"
                        + " preflight line, a wanted value's title and a profile problem's line, so that a walk"
                        + " reaching nothing cannot pass",
                () -> {
                    assertThat(reached).filteredOn(node -> "where".equals(node.getId())).hasSize(1);
                    assertThat(reached).filteredOn(node -> "child".equals(node.getId())).hasSize(1);
                    assertThat(reached).filteredOn(node -> "outcome".equals(node.getId())).hasSize(1);
                    assertThat(texts(reached, "step-census")).as("the row of the census step").contains("waiting");
                    assertThat(texts(reached, "profile-row-" + ProfileKey.GENERATION_MODEL.key()))
                            .contains("Generation model");
                    assertThat(texts(reached, "preflight-list")).anyMatch(line -> line.startsWith("Corpus root: "));
                    assertThat(texts(reached, "wanted-row-boilerplateDocumentFrequencyFloor")).isNotEmpty();
                    assertThat(texts(reached, "code-sets")).anyMatch(line -> line.endsWith("named Papers"));
                    assertThat(reachedWhenNotInForm).filteredOn(node -> "profile-problem".equals(node.getId()))
                            .hasSize(1);
                    assertThat(texts(reachedWhenNotInForm, "profile-problem"))
                            .anyMatch(line -> line.startsWith("line "));
                });
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("A part of a selectable text is selected and copied, and nothing typed or pasted changes it")
    void aSelectableTextIsSelectedAndNotEdited(@TempDir Path folder) throws Exception {
        String line = "the folders are measured";
        String selected;
        List<String> enabled;
        String afterTyping;
        String afterPasting;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> WindowForTests.node(root, "outcome", TextInputControl.class).setText(line));
            selected = window.read(root -> {
                TextInputControl outcome = WindowForTests.node(root, "outcome", TextInputControl.class);
                outcome.selectRange(4, 11);
                return outcome.getSelectedText();
            });
            enabled = window.read(root -> {
                TextInputControl outcome = WindowForTests.node(root, "outcome", TextInputControl.class);
                Event.fireEvent(outcome, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 1, 1, 1, 1,
                        false, new PickResult(outcome, 1, 1)));
                return Window.getWindows().stream().filter(each -> each instanceof ContextMenu)
                        .flatMap(each -> ((ContextMenu) each).getItems().stream())
                        .filter(item -> item.getText() != null
                                && List.of("Copy", "Select All").contains(item.getText()) && !item.isDisable())
                        .map(MenuItem::getText).toList();
            });
            afterTyping = window.read(root -> {
                TextInputControl outcome = WindowForTests.node(root, "outcome", TextInputControl.class);
                outcome.selectAll();
                Event.fireEvent(outcome, new KeyEvent(KeyEvent.KEY_TYPED, "x", "x", KeyCode.UNDEFINED, false, false,
                        false, false));
                return outcome.getText();
            });
            afterPasting = window.read(root -> {
                TextInputControl outcome = WindowForTests.node(root, "outcome", TextInputControl.class);
                ClipboardContent content = new ClipboardContent();
                content.putString("pasted");
                Clipboard.getSystemClipboard().setContent(content);
                outcome.selectAll();
                Event.fireEvent(outcome, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.V, false, true, false,
                        false));
                return outcome.getText();
            });
        }

        claim("a part of the outcome selected reads as that part",
                () -> assertThat(selected).isEqualTo("folders"));
        claim("Copy and Select All are enabled in the context menu the outcome carries",
                () -> assertThat(enabled).contains("Copy", "Select All"));
        claim("a character typed over the selected text changes nothing",
                () -> assertThat(afterTyping).isEqualTo(line));
        claim("text pasted over the selected text changes nothing",
                () -> assertThat(afterPasting).isEqualTo(line));
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("The texts of controls stay as they are: tabs, buttons, the sidebar's section titles and each leg's name and state")
    void theTextsOfControlsStayAsTheyAre(@TempDir Path folder) throws Exception {
        List<String> tabTitles;
        List<Class<?>> buttons;
        List<String> sections;
        Map<String, Class<?>> legTexts = new LinkedHashMap<>();
        Map<String, Boolean> legTextsInTheHeader = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            tabTitles = window.read(root -> HeadlessToolkit.tabWithId(root, "profile").orElseThrow().getTabPane()
                    .getTabs().stream().map(Tab::getText).toList());
            buttons = window.read(root -> List.of(WindowForTests.node(root, "run", Node.class).getClass(),
                    WindowForTests.node(root, "stop", Node.class).getClass(),
                    WindowForTests.node(root, "label", Node.class).getClass()));
            sections = window.read(root -> HeadlessToolkit.everyNodeUnder(WindowForTests.node(root, "sidebar",
                    Node.class)).stream().filter(TitledPane.class::isInstance).map(node -> ((TitledPane) node).getText().replace(" (needs a value)", ""))
                    .toList());
            window.act(root -> {
                for (Legs.Leg leg : Legs.ALL) {
                    TitledPane pane = WindowForTests.node(root, "leg-" + leg.name(), TitledPane.class);
                    for (String part : List.of("leg-name-", "leg-state-")) {
                        Node text = WindowForTests.node(root, part + leg.name(), Node.class);
                        legTexts.put(part + leg.name(), text.getClass());
                        legTextsInTheHeader.put(part + leg.name(), pane.getGraphic() instanceof Parent header
                                && HeadlessToolkit.everyNodeUnder(header).contains(text));
                    }
                }
            });
        }

        claim("the tabs' titles are those of the Profile and Inclusions tabs",
                () -> assertThat(tabTitles).containsExactly("Profile", "Inclusions"));
        claim("Run, Stop and Record my answers are buttons", () -> assertThat(buttons).containsOnly(Button.class));
        claim("the sidebar's section titles are the titled panes' own",
                () -> assertThat(sections).containsExactly("Preflight", "Folders", "Services", "Seed folder",
                        "Code sets", "Command options", "Reports"));
        claim("each leg's name and state are labels, and lie in the header of the leg's titled pane",
                () -> {
                    assertThat(legTexts).hasSize(2 * Legs.ALL.size());
                    assertThat(legTexts.values()).containsOnly(Label.class);
                    assertThat(legTextsInTheHeader.values()).containsOnly(true);
                });
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("A selectable text is as wide and as high as a label of the same text, and in the same place, also after its text is twice as long")
    void aSelectableTextIsLaidOutAsTheLabelItReplaces(@TempDir Path folder) throws Exception {
        String hint = LaunchValues.hintOf("vespera.working-dir");
        Map<String, Placed> before = new LinkedHashMap<>();
        Map<String, Placed> asLabels = new LinkedHashMap<>();
        Map<String, Placed> after = new LinkedHashMap<>();
        Map<String, Placed> afterAsLabels = new LinkedHashMap<>();
        List<String> twice = new ArrayList<>();
        double oneLine;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> {
                for (String id : List.of("where", "hint-vespera.working-dir")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    before.put(id, placed(text));
                    asLabels.put(id, placedAsALabel(root, text));
                }
            });
            oneLine = window.read(root -> {
                Text measure = new Text("Where");
                measure.setFont(WindowForTests.node(root, "hint-vespera.working-dir", TextInputControl.class).getFont());
                return measure.getLayoutBounds().getHeight();
            });
            window.act(root -> {
                for (String id : List.of("where", "hint-vespera.working-dir")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    String longer = text.getText() + " " + text.getText();
                    twice.add(longer);
                    text.setText(longer);
                }
            });
            window.act(root -> {
                for (String id : List.of("where", "hint-vespera.working-dir")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    after.put(id, placed(text));
                    afterAsLabels.put(id, placedAsALabel(root, text));
                }
            });
        }

        claim("the hint of the working directory wraps in the sidebar at the window's 1500 by 960, so that its height is"
                        + " more than one line's",
                () -> assertThat(before.get("hint-vespera.working-dir").height()).as(hint).isGreaterThan(oneLine * 1.5));
        claim("where and the hint are each as wide and as high as a label of the same text, style classes and wrapping,"
                        + " and in the same place, within a pixel",
                () -> assertSame(before, asLabels));
        claim("and after each text is changed to one twice as long, they still are",
                () -> {
                    assertThat(twice).hasSize(2);
                    assertThat(after.get("hint-vespera.working-dir").height())
                            .isGreaterThan(before.get("hint-vespera.working-dir").height());
                    assertSame(after, afterAsLabels);
                });
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("Where space is tight a selectable text shrinks to the width a label would, shows its start again once it is let go, and copies the whole text")
    void whereSpaceIsTightASelectableTextShrinksAsTheLabelWould(@TempDir Path folder) throws Exception {
        List<String> beside = List.of("where", "child", "outcome", "theme-toggle", "run");
        Map<String, Placed> asText = new LinkedHashMap<>();
        Map<String, Placed> asLabel = new LinkedHashMap<>();
        double whereWidth;
        double textWidth;
        String copied;
        String shownAfterSelectAll;
        Map<String, Double> startOfTheText = new LinkedHashMap<>();
        int[] caretAndSelection = new int[3];
        int[] caretAfterTheChange = new int[3];
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> {
                window.stage.setWidth(TIGHT_WIDTH);
                WindowForTests.node(root, "vespera.corpus-root", javafx.scene.control.TextField.class)
                        .setText(A_LONG_FOLDER);
            });
            window.act(root -> {
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                for (String id : beside) {
                    asText.put(id, placed(WindowForTests.node(root, id, Node.class)));
                }
                Placed twin = placedAsALabel(root, where);
                asLabel.put("where", twin);
                for (String id : beside.subList(1, beside.size())) {
                    asLabel.put(id, placedWithLabelInPlaceOf(root, where, id));
                }
            });
            whereWidth = asText.get("where").width();
            textWidth = window.read(root -> {
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                Text measure = new Text(where.getText());
                measure.setFont(where.getFont());
                return measure.getLayoutBounds().getWidth();
            });
            copied = window.read(root -> {
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                where.requestFocus();
                where.selectAll();
                where.copy();
                return Clipboard.getSystemClipboard().getString();
            });
            shownAfterSelectAll = window.read(root -> {
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                startOfTheText.put("selected", offsetOfTheText(where));
                return where.getSelectedText();
            });
            window.act(root -> {
                WindowForTests.node(root, "run", Button.class).requestFocus();
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                caretAndSelection[0] = where.getCaretPosition();
                caretAndSelection[1] = where.getSelection().getLength();
                startOfTheText.put("let go", offsetOfTheText(where));
                where.requestFocus();
                where.selectAll();
                where.setText(where.getText() + " and more");
                caretAfterTheChange[0] = where.getCaretPosition();
                caretAfterTheChange[1] = where.getSelection().getLength();
            });
        }

        claim("the path in where is wider than the window of 700 leaves it, so that where is shrunk",
                () -> assertThat(whereWidth).isLessThan(textWidth));
        claim("where is as wide as a label of the same text and style laid out there, within a pixel",
                () -> assertThat(asText.get("where").width()).isCloseTo(asLabel.get("where").width(),
                        offset(ONE_PIXEL)));
        claim("the parts beside and below it, the child, the outcome, the theme switch and Run, lie in the same places"
                        + " as with the label",
                () -> beside.forEach(id -> {
                    assertThat(asText.get(id).x()).as("%s x", id).isCloseTo(asLabel.get(id).x(), offset(ONE_PIXEL));
                    assertThat(asText.get(id).y()).as("%s y", id).isCloseTo(asLabel.get(id).y(), offset(ONE_PIXEL));
                }));
        claim("Select All then Copy puts the whole text of where on the clipboard",
                () -> assertThat(copied).isEqualTo(shownAfterSelectAll).startsWith("Corpus root: " + A_LONG_FOLDER));
        claim("after Select All the end of the text is shown, so that the claim below has a start to put back",
                () -> assertThat(startOfTheText.get("selected")).isLessThan(-ONE_PIXEL));
        claim("with the focus moved to Run, the caret of where is at 0, nothing is selected and the start of its text"
                        + " is in view",
                () -> {
                    assertThat(caretAndSelection[0]).isZero();
                    assertThat(caretAndSelection[1]).isZero();
                    assertThat(startOfTheText.get("let go")).isCloseTo(0, offset(ONE_PIXEL));
                });
        claim("and where, its text changed while selected, has its caret at 0 and nothing selected",
                () -> {
                    assertThat(caretAfterTheChange[0]).isZero();
                    assertThat(caretAfterTheChange[1]).isZero();
                });
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("A selectable text is a line or a wrapping one, found by the id its label had")
    void aSelectableTextIsALineOrAWrappingAndCarriesTheLabelsId(@TempDir Path folder) throws Exception {
        Map<String, Class<?>> found = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> {
                for (String id : List.of("where", "outcome", "hint-vespera.working-dir")) {
                    found.put(id, WindowForTests.node(root, id, TextInputControl.class).getClass());
                }
            });
        }

        claim("where, the outcome and a hint are each a selectable text",
                () -> assertThat(found.values()).allMatch(SelectableText.class::isAssignableFrom));
        claim("where is a line, which a text field is",
                () -> assertThat(found.get("where")).isEqualTo(SelectableText.Line.class));
        claim("the outcome and the hint, which wrap, are wrapping texts, which a text area is",
                () -> {
                    assertThat(found.get("outcome")).isEqualTo(SelectableText.Wrapping.class);
                    assertThat(found.get("hint-vespera.working-dir")).isEqualTo(SelectableText.Wrapping.class);
                    assertThat(TextArea.class).isAssignableFrom(SelectableText.Wrapping.class);
                });
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("A wheel turned over a description in the Profile tab scrolls the tab, and not the description")
    void aWheelOverASelectableTextScrollsTheTabItLiesIn(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        double tabBefore;
        double tabAfter;
        double textBefore;
        double textAfter;
        int heardByTheText;
        int handedToTheParent;
        try (WindowForTests window = WindowForTests.shown(state)) {
            double[] seen = new double[4];
            int[] heard = new int[2];
            window.act(root -> {
                SelectableText.Wrapping description = WindowForTests.every(root,
                        "profile-row-" + ProfileKey.GENERATION_MODEL.key(), SelectableText.Wrapping.class).getFirst();
                ScrollPane tab = tabScroll(root);
                seen[0] = tab.getVvalue();
                seen[2] = description.getScrollTop();
                description.addEventHandler(ScrollEvent.SCROLL, each -> heard[0]++);
                description.getParent().addEventFilter(ScrollEvent.SCROLL, each -> {
                    if (each.getTarget() == description.getParent()) {
                        heard[1]++;
                    }
                });
                ScrollEvent wheel = new ScrollEvent(ScrollEvent.SCROLL, 5, 5, 5, 5, false, false, false, false, false,
                        false, 0, -40, 0, -40, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, new PickResult(description, 5, 5));
                Event.fireEvent(description, wheel);
                seen[1] = tabScroll(root).getVvalue();
                seen[3] = description.getScrollTop();
            });
            tabBefore = seen[0];
            tabAfter = seen[1];
            textBefore = seen[2];
            textAfter = seen[3];
            heardByTheText = heard[0];
            handedToTheParent = heard[1];
        }

        claim("a wheel turned downward over a profile row's description moves the Profile tab's scroll pane down",
                () -> assertThat(tabAfter).isGreaterThan(tabBefore));
        claim("and the description's own scroll does not move",
                () -> assertThat(textAfter).isEqualTo(textBefore));
        claim("and the description consumes the wheel's turn before its own handlers hear it, and hands the parent a copy"
                        + " of it",
                () -> {
                    assertThat(heardByTheText).isZero();
                    assertThat(handedToTheParent).isEqualTo(1);
                });
    }

    /** The scroll pane the Profile tab's rows lie in. */
    private static ScrollPane tabScroll(Parent root) {
        Node content = HeadlessToolkit.tabWithId(root, "profile").orElseThrow().getContent();
        return (ScrollPane) HeadlessToolkit.everyNodeUnder(content).stream().filter(ScrollPane.class::isInstance)
                .findFirst().orElseThrow(() -> new AssertionError("the Profile tab holds no scroll pane"));
    }

    // ---- what the walk reaches

    /** The nodes of the window the record's walk reaches, in order. */
    private static List<Node> outsideAControl(Parent root) {
        List<Node> reached = new ArrayList<>();
        reach(root, reached);
        return reached;
    }

    private static void reach(Node node, List<Node> reached) {
        if (node == null || reached.stream().anyMatch(seen -> seen == node)) {
            return;
        }
        reached.add(node);
        if (node instanceof Control control) {
            if (control instanceof ScrollPane scroll) {
                reach(scroll.getContent(), reached);
            } else if (control instanceof SplitPane split) {
                split.getItems().forEach(item -> reach(item, reached));
            } else if (control instanceof TabPane tabs) {
                tabs.getTabs().forEach(tab -> reach(tab.getContent(), reached));
            } else if (control instanceof Accordion accordion) {
                accordion.getPanes().forEach(pane -> reach(pane, reached));
            } else if (control instanceof TitledPane titled) {
                reach(titled.getContent(), reached);
            }
        } else if (node instanceof Parent parent && !(node instanceof Chart)) {
            parent.getChildrenUnmodifiable().forEach(child -> reach(child, reached));
        }
    }

    /** The texts of the selectable texts among the reached nodes that lie under the reached node whose id is {@code id}. */
    private static List<String> texts(List<Node> reached, String id) {
        Node under = reached.stream().filter(node -> id.equals(node.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("the walk reaches no node whose id is " + id));
        List<Node> below = HeadlessToolkit.everyNodeUnder(under);
        return reached.stream().filter(SelectableText.class::isInstance).filter(node -> below.stream()
                .anyMatch(each -> each == node)).map(node -> ((TextInputControl) node).getText()).toList();
    }

    // ---- where a node lies, and where a label would

    private static Placed placed(Node node) {
        Bounds at = node.localToScene(node.getLayoutBounds());
        return new Placed(at.getMinX(), at.getMinY(), at.getWidth(), at.getHeight());
    }

    private static void assertSame(Map<String, Placed> text, Map<String, Placed> label) {
        text.forEach((id, at) -> {
            Placed twin = label.get(id);
            assertThat(at.width()).as("%s width: %s, as a label %s", id, at, twin).isCloseTo(twin.width(),
                    offset(ONE_PIXEL));
            assertThat(at.height()).as("%s height: %s, as a label %s", id, at, twin).isCloseTo(twin.height(),
                    offset(ONE_PIXEL));
            assertThat(at.x()).as("%s x: %s, as a label %s", id, at, twin).isCloseTo(twin.x(), offset(ONE_PIXEL));
            assertThat(at.y()).as("%s y: %s, as a label %s", id, at, twin).isCloseTo(twin.y(), offset(ONE_PIXEL));
        });
    }

    /** A label of {@code text}'s text, style classes and wrapping in its place for a layout, put back after. */
    private static Label labelFor(TextInputControl text) {
        Label label = new Label(text.getText());
        text.getStyleClass().stream()
                .filter(each -> !List.of("text-input", "text-field", "text-area", SelectableText.STYLE_CLASS).contains(each))
                .forEach(label.getStyleClass()::add);
        label.setWrapText(text instanceof TextArea);
        return label;
    }

    /** Where a label of {@code text}'s text, style classes and wrapping lies when it stands where {@code text} stands. */
    private static Placed placedAsALabel(Parent root, TextInputControl text) {
        return withALabelInPlaceOf(root, text, label -> placed(label));
    }

    /** Where the node whose id is {@code id} lies when a label stands where {@code text} stands. */
    private static Placed placedWithLabelInPlaceOf(Parent root, TextInputControl text, String id) {
        return withALabelInPlaceOf(root, text, label -> placed(WindowForTests.node(root, id, Node.class)));
    }

    private static Placed withALabelInPlaceOf(Parent root, TextInputControl text, Function<Label, Placed> reading) {
        Pane parent = (Pane) text.getParent();
        int at = parent.getChildren().indexOf(text);
        Label label = labelFor(text);
        parent.getChildren().set(at, label);
        try {
            root.applyCss();
            root.layout();
            return reading.apply(label);
        } finally {
            parent.getChildren().set(at, text);
            root.applyCss();
            root.layout();
        }
    }

    /** Where the left edge of {@code text}'s drawn text lies against its own left edge: 0 where its start is shown. */
    private static double offsetOfTheText(TextInputControl text) {
        Text drawn = (Text) text.lookup(".text");
        Bounds shown = drawn.localToScene(drawn.getBoundsInLocal());
        return shown.getMinX() - text.localToScene(text.getLayoutBounds()).getMinX();
    }
}
