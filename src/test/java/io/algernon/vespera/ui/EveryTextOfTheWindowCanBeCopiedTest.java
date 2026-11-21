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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.control.ToggleButton;
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
 * accordion's panes and a titled pane's content, never its header; and the Profile window and the Reports window, each
 * a window of its own, from its root the same way (ADR-389 §7, ADR-414 §6). The context menu's items are found by their
 * English names, which JavaFX gives them in the tests' locale, pinned to {@code en_US} on every machine (ADR-386).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("777")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
@Link(name = "ADR-386", url = Adr.THE_TESTS_RUN_IN_ONE_LOCALE_ON_EVERY_MACHINE, type = "adr")
@Link(name = "ADR-389", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
@Link(name = "ADR-414", url = Adr.THE_ANALYSIS_REPORTS_OPEN_IN_A_REPORTS_WINDOW_OF_THEIR_OWN, type = "adr")
class EveryTextOfTheWindowCanBeCopiedTest {

    /** How far apart two sizes or places may be and still be the same: a pixel. */
    private static final double ONE_PIXEL = 1.0;

    /** The report whose JSON the working directory holds in the first window, opened there in the Reports window. */
    private static final String A_REPORT_WRITTEN = "document-frequency";

    /** That report's one section, by its id, and its one paragraph. */
    private static final String ITS_SECTION = "about";

    private static final String ITS_PARAGRAPH = "One paragraph of the report.";

    /** A folder name long enough that the header's {@code where} is wider than a window of 700. */
    private static final String A_LONG_FOLDER = "/archive/" + "a-folder-with-a-long-name/".repeat(9) + "corpus";

    /** The window's width where space is tight. */
    private static final double TIGHT_WIDTH = 700;

    /** A sentence the outcome is given, and how many times, so that it is wider on one line than the window. */
    private static final String A_SENTENCE = "the folders are measured and the containers are checked before anything"
            + " is started here";

    private static final int SAID_TIMES = 8;

    /** Where a node lies in the scene, and how large it is. */
    private record Placed(double x, double y, double width, double height) {
    }

    @Test
    @Story("Every text of the window can be copied")
    @DisplayName("Every text the window shows outside a control can be selected and copied but not edited, and is laid out as the label it replaces, and the texts of controls stay as they are")
    void everyTextShownOutsideAControlIsSelectableText(@TempDir Path folder) throws Exception {
        // One test method for the seven claims of this record, each over its own window: the suite holds at most
        // 700 test methods (ADR-368), and a claim of a window drawn by the same code is paid for by none of its own.
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("papers"));
        Path elsewhere = Files.createDirectories(folder.resolve("elsewhere"));
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.of(ProfileEdit.rewrite(
                Optional.empty(), ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured on the seed set")),
                ProfileKey.CODE_SET_FOLDERS, elsewhere.toString(), "folders outside the corpus root"),
                StandardCharsets.UTF_8);
        Files.writeString(working.resolve(Reports.jsonOf(A_REPORT_WRITTEN)), "{\"format\":"
                + AnalysisReportFile.FORMATS.get(A_REPORT_WRITTEN) + ",\"analysisReport\":\"" + A_REPORT_WRITTEN
                + "\",\"title\":\"" + Reports.TITLES.get(A_REPORT_WRITTEN) + "\",\"writtenAt\":\"2026-11-18T10:00:00Z\","
                + "\"headline\":[{\"key\":\"files\",\"label\":\"Files\",\"value\":3,\"unit\":\"count\"}],"
                + "\"sections\":[{\"id\":\"" + ITS_SECTION + "\",\"title\":\"About\",\"text\":[\"" + ITS_PARAGRAPH
                + "\"],\"chart\":null,\"table\":null}]}", StandardCharsets.UTF_8);
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=boilerplateDocumentFrequencyFloor\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        Path otherFolder = Files.createDirectories(folder.resolve("other"));
        Path otherWorking = Files.createDirectories(otherFolder.resolve("working"));
        Files.writeString(otherWorking.resolve(ProfileEdit.FILE_NAME),
                ProfileEdit.rewrite(Optional.empty(), ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured")
                        .replace("generationModel:\n", "generationModel: # a comment the jar does not write\n"),
                StandardCharsets.UTF_8);
        ControlPanelState otherState = WindowForTests.aState(otherFolder, SleepingJar.runtime());
        otherState.hold(new LaunchValues(corpus.toString(), otherWorking.toString(), "", "", "", "", "", ""));

        boolean ended;
        List<Node> reached;
        List<Node> reachedWhenNotInForm;
        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child that ends at once is started").isEmpty();
            // the wanted rows are drawn as the window opens (ADR-404 §1), so only the ending says the child has ended
            ended = WindowForTests.within(() -> state.ending().isPresent());
            boolean drawn = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-boilerplateDocumentFrequencyFloor").isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn").isTrue();
            reached = new ArrayList<>(window.read(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl));
            // the profile's form lies in a window of its own, walked from its root as the window is (ADR-389 §7),
            // every profile row opened, so that the walk reaches the texts behind each toggle
            window.openProfile();
            reached.addAll(window.readProfile(root -> {
                HeadlessToolkit.everyNodeUnder(root).stream().filter(ToggleButton.class::isInstance)
                        .map(ToggleButton.class::cast)
                        .filter(toggle -> toggle.getStyleClass().contains("more") && !toggle.isSelected())
                        .forEach(ToggleButton::fire);
                root.applyCss();
                root.layout();
                return outsideAControl(root);
            }));
            // the reports lie in a window of their own, walked from its root as the window is (ADR-414 section 6)
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", A_REPORT_WRITTEN));
            reached.addAll(window.readAnalysisReports(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl));
        }
        try (WindowForTests window = WindowForTests.shown(otherState)) {
            reachedWhenNotInForm = new ArrayList<>(window.read(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl));
            window.openProfile();
            reachedWhenNotInForm.addAll(window.readProfile(EveryTextOfTheWindowCanBeCopiedTest::outsideAControl));
        }
        List<Node> every = new ArrayList<>(reached);
        every.addAll(reachedWhenNotInForm);
        List<String> labelsWithWords = every.stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> label.getText() != null && !label.getText().isBlank())
                // the percent drawn on a bar is the bar's own reading, a control's text (ADR-364 §8, ADR-370 §1)
                .filter(label -> !label.getStyleClass().contains("bar-percent")).map(Label::getText).toList();
        List<Node> selectable = every.stream().filter(SelectableText.class::isInstance).toList();
        List<String> reading = selectable.stream().filter(node -> ((TextInputControl) node).isEditable()
                || node.isFocusTraversable()).map(Node::getId).toList();

        claim("the child that ends at once has ended",
                () -> assertThat(ended).isTrue());
        claim("the walk reaches no label whose text is not blank",
                () -> assertThat(labelsWithWords).isEmpty());
        claim("every selectable text it reaches is a text input control that is not editable and is not reached by"
                        + " the Tab key",
                () -> assertThat(reading).isEmpty());
        claim("the walk reaches the header's where and child, the outcome, a step's state, a profile row's title, a"
                        + " preflight row's name, a wanted value's title, a profile problem's line, and a report's headline"
                        + " and paragraph in the Reports window, so that a walk reaching nothing cannot pass",
                () -> {
                    assertThat(reached).filteredOn(node -> "where".equals(node.getId())).hasSize(1);
                    assertThat(reached).filteredOn(node -> "child".equals(node.getId())).hasSize(1);
                    assertThat(reached).filteredOn(node -> "outcome".equals(node.getId())).hasSize(1);
                    assertThat(texts(reached, "step-census")).as("the row of the census step").contains("waiting");
                    assertThat(texts(reached, "profile-row-" + ProfileKey.GENERATION_MODEL.key()))
                            .contains(ProfileKey.GENERATION_MODEL.title());
                    // a row's name, mark and words are three texts (ADR-440 §2)
                    assertThat(texts(reached, "preflight-list")).contains("Corpus root");
                    assertThat(texts(reached, "wanted-row-boilerplateDocumentFrequencyFloor")).isNotEmpty();
                    assertThat(texts(reached, "code-sets")).contains(elsewhere.toString());
                    assertThat(texts(reached, "headline-" + A_REPORT_WRITTEN)).contains("Files");
                    assertThat(texts(reached, "section-" + A_REPORT_WRITTEN + "-" + ITS_SECTION))
                            .contains(ITS_PARAGRAPH);
                    assertThat(reachedWhenNotInForm).filteredOn(node -> "profile-problem".equals(node.getId()))
                            .hasSize(1);
                    assertThat(texts(reachedWhenNotInForm, "profile-problem"))
                            .anyMatch(line -> line.startsWith("line "));
                });
        aSelectableTextIsSelectedAndNotEdited(Files.createDirectories(folder.resolve("aSelectableTextIsSelectedAndNotEdited")));
        theTextsOfControlsStayAsTheyAre(Files.createDirectories(folder.resolve("theTextsOfControlsStayAsTheyAre")));
        aSelectableTextIsLaidOutAsTheLabelItReplaces(Files.createDirectories(folder.resolve("aSelectableTextIsLaidOutAsTheLabelItReplaces")));
        whereSpaceIsTightASelectableTextShrinksAsTheLabelWould(Files.createDirectories(folder.resolve("whereSpaceIsTightASelectableTextShrinksAsTheLabelWould")));
        aSelectableTextIsALineOrAWrappingAndCarriesTheLabelsId(Files.createDirectories(folder.resolve("aSelectableTextIsALineOrAWrappingAndCarriesTheLabelsId")));
        aWheelOverASelectableTextScrollsTheFormItLiesIn(Files.createDirectories(folder.resolve("aWheelOverASelectableTextScrollsTheFormItLiesIn")));
    }

    private void aSelectableTextIsSelectedAndNotEdited(Path folder) throws Exception {
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
        claim("the tests run in English as used in the United States, the language the context menu's items are named in",
                () -> assertThat(Locale.getDefault()).isEqualTo(Locale.US));
        claim("Copy and Select All are enabled in the context menu the outcome carries",
                () -> assertThat(enabled).contains("Copy", "Select All"));
        claim("a character typed over the selected text changes nothing",
                () -> assertThat(afterTyping).isEqualTo(line));
        claim("text pasted over the selected text changes nothing",
                () -> assertThat(afterPasting).isEqualTo(line));
    }

    private void theTextsOfControlsStayAsTheyAre(Path folder) throws Exception {
        List<String> tabTitles;
        List<Class<?>> buttons;
        List<String> sections;
        Map<String, Class<?>> legTexts = new LinkedHashMap<>();
        Map<String, Boolean> legTextsInTheHeader = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            tabTitles = window.read(WindowForTests::tabIds);
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

        claim("the window holds no tab, so no tab's title stands among its texts",
                () -> assertThat(tabTitles).isEmpty());
        claim("Run, Stop and Save my labels are buttons", () -> assertThat(buttons).containsOnly(Button.class));
        claim("the sidebar's section titles are the titled panes' own",
                () -> assertThat(sections).containsExactly("Profile", "Preflight", "Folders", "Services",
                        "Seed folder", "Topics", "Labels", "Code sets", "Outputs", "Command options", "Reports"));
        claim("each leg's name and state are labels, and lie in the header of the leg's titled pane",
                () -> {
                    assertThat(legTexts).hasSize(2 * Legs.ALL.size());
                    assertThat(legTexts.values()).containsOnly(Label.class);
                    assertThat(legTextsInTheHeader.values()).containsOnly(true);
                });
    }

    private void aSelectableTextIsLaidOutAsTheLabelItReplaces(Path folder) throws Exception {
        // the outcome is the wrapping text every window shows (ADR-430 §8 took the hints beneath the fields away)
        String line = String.join(" ", Collections.nCopies(SAID_TIMES, A_SENTENCE));
        Map<String, Placed> before = new LinkedHashMap<>();
        Map<String, Placed> asLabels = new LinkedHashMap<>();
        Map<String, Placed> after = new LinkedHashMap<>();
        Map<String, Placed> afterAsLabels = new LinkedHashMap<>();
        List<String> twice = new ArrayList<>();
        double oneLine;
        double unwrapped;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> WindowForTests.node(root, "outcome", TextInputControl.class).setText(line));
            window.act(root -> {
                for (String id : List.of("where", "outcome")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    before.put(id, placed(text));
                    asLabels.put(id, placedAsALabel(root, text));
                }
            });
            oneLine = window.read(root -> {
                Text measure = new Text("Where");
                measure.setFont(WindowForTests.node(root, "outcome", TextInputControl.class).getFont());
                return measure.getLayoutBounds().getHeight();
            });
            unwrapped = window.read(root -> {
                Text measure = new Text(line);
                measure.setFont(WindowForTests.node(root, "outcome", TextInputControl.class).getFont());
                return measure.getLayoutBounds().getWidth();
            });
            window.act(root -> {
                for (String id : List.of("where", "outcome")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    String longer = text.getText() + " " + text.getText();
                    twice.add(longer);
                    text.setText(longer);
                }
            });
            window.act(root -> {
                for (String id : List.of("where", "outcome")) {
                    TextInputControl text = WindowForTests.node(root, id, TextInputControl.class);
                    after.put(id, placed(text));
                    afterAsLabels.put(id, placedAsALabel(root, text));
                }
            });
        }

        claim("the outcome, a sentence said " + SAID_TIMES + " times, is wider on one line than the window's 1500, and"
                        + " wraps beneath the header, so that its height is more than one line's",
                () -> {
                    assertThat(unwrapped).as(line).isGreaterThan(1500);
                    assertThat(before.get("outcome").height()).as(line).isGreaterThan(oneLine * 1.5);
                });
        claim("where and the outcome are each as wide and as high as a label of the same text, style classes and"
                        + " wrapping, and in the same place, within a pixel",
                () -> assertSame(before, asLabels));
        claim("and after each text is changed to one twice as long, they still are, the outcome higher than before",
                () -> {
                    assertThat(twice).hasSize(2);
                    assertThat(after.get("outcome").height()).isGreaterThan(before.get("outcome").height());
                    assertSame(after, afterAsLabels);
                });
    }

    private void whereSpaceIsTightASelectableTextShrinksAsTheLabelWould(Path folder) throws Exception {
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
        boolean[] ellipsisShown = new boolean[1];
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> {
                window.stage.setWidth(TIGHT_WIDTH);
                WindowForTests.node(root, "vespera.corpus-root", javafx.scene.control.TextField.class)
                        .setText(A_LONG_FOLDER);
            });
            window.act(root -> {
                TextInputControl where = WindowForTests.node(root, "where", TextInputControl.class);
                ellipsisShown[0] = ((SelectableText.Line) where).ellipsis().isVisible();
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
        claim("and where, shrunk, shows its ellipsis over its end", () -> assertThat(ellipsisShown[0]).isTrue());
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

    private void aSelectableTextIsALineOrAWrappingAndCarriesTheLabelsId(Path folder) throws Exception {
        Map<String, Class<?>> found = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            window.act(root -> {
                for (String id : List.of("where", "status-vespera.corpus-root", "outcome", "seed-folder-outcome")) {
                    found.put(id, WindowForTests.node(root, id, TextInputControl.class).getClass());
                }
            });
        }

        claim("where, a field's status, the outcome and the seed folder's outcome are each a selectable text",
                () -> assertThat(found).hasSize(4).allSatisfy((id, type) -> assertThat(type).as(id)
                        .matches(SelectableText.class::isAssignableFrom, "a selectable text")));
        claim("where and a field's status are lines, which a text field is",
                () -> {
                    assertThat(found.get("where")).isEqualTo(SelectableText.Line.class);
                    assertThat(found.get("status-vespera.corpus-root")).isEqualTo(SelectableText.Line.class);
                });
        claim("the outcome and the seed folder's outcome, which wrap, are wrapping texts, which a text area is",
                () -> {
                    assertThat(found.get("outcome")).isEqualTo(SelectableText.Wrapping.class);
                    assertThat(found.get("seed-folder-outcome")).isEqualTo(SelectableText.Wrapping.class);
                    assertThat(TextArea.class).isAssignableFrom(SelectableText.Wrapping.class);
                });
    }

    private void aWheelOverASelectableTextScrollsTheFormItLiesIn(Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        double formBefore;
        double formAfter;
        double textBefore;
        double textAfter;
        int heardByTheText;
        int handedToTheParent;
        try (WindowForTests window = WindowForTests.shown(state)) {
            double[] seen = new double[4];
            int[] heard = new int[2];
            String row = "profile-" + ProfileKey.GENERATION_MODEL.key();
            window.openProfile();
            window.actOnProfile(root -> WindowForTests.node(root, row + ".more", ToggleButton.class).fire());
            window.actOnProfile(root -> {
                SelectableText.Wrapping description = WindowForTests.node(root, row + ".short",
                        SelectableText.Wrapping.class);
                ScrollPane form = formScroll(root);
                seen[0] = form.getVvalue();
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
                seen[1] = formScroll(root).getVvalue();
                seen[3] = description.getScrollTop();
            });
            formBefore = seen[0];
            formAfter = seen[1];
            textBefore = seen[2];
            textAfter = seen[3];
            heardByTheText = heard[0];
            handedToTheParent = heard[1];
        }

        claim("a wheel turned downward over a profile row's description moves the Profile window's scroll pane down",
                () -> assertThat(formAfter).isGreaterThan(formBefore));
        claim("and the description's own scroll does not move",
                () -> assertThat(textAfter).isEqualTo(textBefore));
        claim("and the description consumes the wheel's turn before its own handlers hear it, and hands the parent a copy"
                        + " of it",
                () -> {
                    assertThat(heardByTheText).isZero();
                    assertThat(handedToTheParent).isEqualTo(1);
                });
    }

    /** The scroll pane the Profile window's rows lie in, {@code root} being the window's root. */
    private static ScrollPane formScroll(Parent root) {
        return (ScrollPane) HeadlessToolkit.everyNodeUnder(root).stream().filter(ScrollPane.class::isInstance)
                .findFirst().orElseThrow(() -> new AssertionError("the Profile window holds no scroll pane"));
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
