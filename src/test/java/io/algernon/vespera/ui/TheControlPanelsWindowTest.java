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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's window, as the operator laid it out (ADR-344 §1): Run and Stop side by side; the job's steps as
 * a chart of a bar each, beside the output; the progress page's three charts; Profile and Inclusions as tabs; and every
 * control the table of the jar's arguments names, there to set it (ADR-344 §4). The two label commands, by the names the
 * operator chose, are a section of the sidebar, and the window calls a relevance label a label (ADR-413).
 *
 * <p>The tests of the arguments draw the window from a control panel's state that names no launch value, as on a first
 * start; the tests of the label commands show it as the control panel shows it, over a corpus root and a working
 * directory. Each shows it on a stage on the Headless platform (ADR-342 §6) and finds its parts by their ids. The words
 * are read only where a record fixes them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheControlPanelsWindowTest {

    /** The window's size as it opens, so that the parts are laid out as the operator sees them. */
    private static final int WIDTH = 1500;

    private static final int HEIGHT = 960;

    @TempDir
    static Path temporary;

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Every control the table of the jar's arguments names is a control of the window")
    void everyControlTheArgumentsTableNamesIsAControlOfTheWindow() throws Exception {
        List<String> named = ChildCommand.ARGUMENTS.stream()
                .map(ChildCommand.Argument::control)
                .filter(control -> !control.isEmpty())
                .distinct()
                .toList();
        List<String> missing = inTheWindow(root -> named.stream()
                .filter(id -> HeadlessToolkit.nodeWithId(root, id).isEmpty())
                .toList());

        claim("the table names controls at all, so the check below reads something",
                () -> assertThat(named).contains("vespera.corpus-root", "label-auto"));
        claim("and each control the table names is a control of the window, found by its id",
                () -> assertThat(missing).as("controls the table names that the window does not hold").isEmpty());
    }

    /** ADR-377 §10: Command options offers the five presets and no label file; Save my labels is ADR-413's name. */
    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Command options offers no label file, and Save my labels' command line names none")
    @Issue("808")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void theCommandOptionsOfferNoLabelFile() throws Exception {
        record Read(boolean field, boolean browse, boolean heading, String commandLine) {
        }
        Read read = inTheWindow(root -> new Read(
                HeadlessToolkit.nodeWithId(root, "label-file").isPresent(),
                HeadlessToolkit.nodeWithId(root, "label-file.browse").isPresent(),
                HeadlessToolkit.everyNodeUnder(root).stream().anyMatch(node -> "Label file".equals(textOf(node))),
                HeadlessToolkit.nodeWithId(root, "command-line-label").map(TheControlPanelsWindowTest::textOf)
                        .orElse("")));

        claim("the window holds no label file field and no Browse for one",
                () -> assertThat(List.of(read.field(), read.browse())).containsOnly(false));
        claim("no text of the window reads \"Label file\"",
                () -> assertThat(read.heading()).isFalse());
        claim("and Save my labels' command line reads vespera label --db-dir=<working directory>, and nothing after",
                () -> assertThat(read.commandLine()).matches("vespera label --db-dir=(\\S+|\"[^\"]+\")"));
    }

    /** The model the label model's field is given, so that Auto Label's caption names it. */
    private static final String A_LABEL_MODEL = "qwen3:8b";

    /**
     * The Labels section's parts, by their ids, in the order the section holds them: each button's wrapper, which carries
     * its caption (ADR-430 §8), then the button.
     */
    private static final List<String> THE_LABEL_PARTS = List.of("label-captioned", "label", "label-auto-captioned",
            "label-auto");

    private static final String THE_LABELS_SECTIONS_TITLE = "Labels";

    private static final String SAVE_MY_LABELS = "Save my labels";

    private static final String AUTO_LABEL = "Auto Label";

    /** Save my labels' caption. */
    private static final String SAVE_MY_LABELS_WORDS = "Reads relevance-labels.yaml in the working directory, where"
            + " each document's relevant is set to true or false, and saves those labels for the next Run. It reads no"
            + " document and sets no label itself.";

    /** Auto Label's caption while the label model's field is blank. */
    private static final String AUTO_LABEL_WORDS = "The local model reads the documents, sets their labels and the"
            + " cut-off, and never replaces a label you set.";

    /** Auto Label's caption once the label model's field names {@link #A_LABEL_MODEL}. */
    private static final String AUTO_LABEL_WORDS_NAMING_THE_MODEL = "The local model, " + A_LABEL_MODEL + ", reads the"
            + " documents, sets their labels and the cut-off, and never replaces a label you set.";

    /**
     * ADR-413 §1 and §2: the two label commands are a section of the sidebar of their own, by the names the operator
     * chose, each with its caption, and start the commands they started before. The window is shown over a corpus root
     * and a working directory, starting {@link SleepingJar}'s child, so that a press starts one.
     */
    @Test
    @Story("The label commands stand in the sidebar")
    @DisplayName("Save my labels and Auto Label, each with its caption, are the sidebar's Labels section and nowhere else, and start vespera label and vespera label --auto")
    @Issue("857")
    @Link(name = "ADR-413", url = Adr.THE_LABEL_COMMANDS_ARE_A_LABELS_SECTION_OF_THE_SIDEBAR, type = "adr")
    void theLabelCommandsAreTheSidebarsLabelsSection(@TempDir Path folder) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        record Section(boolean ofTheSidebar, String title, List<String> parts, Map<String, Long> timesInTheWindow,
                List<String> buttons, String saveWords) {
        }
        Section section;
        String autoWordsBlank;
        String autoWordsNamed;
        String afterSave;
        String saveStarted;
        boolean enabledAgain;
        String afterAuto;
        String autoStarted;
        try (WindowForTests window = WindowForTests.shown(state)) {
            section = window.read(root -> {
                TitledPane labels = WindowForTests.node(root, "labels", TitledPane.class);
                List<Node> everyNode = HeadlessToolkit.everyNodeUnder(root);
                return new Section(
                        WindowForTests.node(root, "sidebar", Accordion.class).getPanes().contains(labels),
                        labels.getText(),
                        HeadlessToolkit.everyNodeUnder(labels.getContent()).stream().map(Node::getId)
                                .filter(id -> id != null && THE_LABEL_PARTS.contains(id)).toList(),
                        THE_LABEL_PARTS.stream().collect(Collectors.toMap(id -> id,
                                id -> everyNode.stream().filter(node -> id.equals(node.getId())).count())),
                        List.of(WindowForTests.node(root, "label", Button.class).getText(),
                                WindowForTests.node(root, "label-auto", Button.class).getText()),
                        WindowForTests.caption(root, "label-captioned"));
            });
            autoWordsBlank = window.read(root -> {
                WindowForTests.node(root, "vespera.label.model", TextInputControl.class).setText("");
                return WindowForTests.caption(root, "label-auto-captioned");
            });
            autoWordsNamed = window.read(root -> {
                WindowForTests.node(root, "vespera.label.model", TextInputControl.class).setText(A_LABEL_MODEL);
                return WindowForTests.caption(root, "label-auto-captioned");
            });
            window.act(root -> WindowForTests.node(root, "label", Button.class).fire());
            afterSave = window.read(root -> textOf(WindowForTests.node(root, "outcome", Node.class)));
            saveStarted = state.childCommand();
            state.stop();
            enabledAgain = window.awaiting(
                    root -> !WindowForTests.node(root, "label-auto", Button.class).isDisabled(), Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "label-auto", Button.class).fire());
            afterAuto = window.read(root -> textOf(WindowForTests.node(root, "outcome", Node.class)));
            autoStarted = state.childCommand();
            state.stop();
        }

        claim("the sidebar holds a section titled " + THE_LABELS_SECTIONS_TITLE,
                () -> {
                    assertThat(section.ofTheSidebar()).isTrue();
                    assertThat(section.title()).isEqualTo(THE_LABELS_SECTIONS_TITLE);
                });
        claim("it holds Save my labels on the wrapper carrying its caption, then Auto Label on its own, in that order",
                () -> assertThat(section.parts()).containsExactlyElementsOf(THE_LABEL_PARTS));
        claim("and each of the four stands once in the whole window, so that none stands beside Run as well",
                () -> assertThat(section.timesInTheWindow()).containsOnlyKeys(THE_LABEL_PARTS)
                        .allSatisfy((id, times) -> assertThat(times).as("%s's nodes in the window", id).isOne()));
        claim("the two buttons read " + SAVE_MY_LABELS + " and " + AUTO_LABEL + ", in that order",
                () -> assertThat(section.buttons()).containsExactly(SAVE_MY_LABELS, AUTO_LABEL));
        claim("Save my labels' caption says it reads relevance-labels.yaml in the working directory and saves the labels"
                        + " set there, reading no document",
                () -> assertThat(section.saveWords()).isEqualTo(SAVE_MY_LABELS_WORDS));
        claim("Auto Label's caption says the local model sets the labels and the cut-off and replaces none set by hand,"
                        + " naming the model only once the label model's field names one",
                () -> assertThat(List.of(autoWordsBlank, autoWordsNamed))
                        .containsExactly(AUTO_LABEL_WORDS, AUTO_LABEL_WORDS_NAMING_THE_MODEL));
        claim("pressed, Save my labels says it started, by its name, and starts vespera label",
                () -> {
                    assertThat(afterSave).matches("(?s)" + Pattern.quote(SAVE_MY_LABELS + " started") + "(?:; .+)?");
                    assertThat(saveStarted).isEqualTo("label");
                });
        claim("and Auto Label, enabled again once that child is stopped, says it started, by its name, and starts vespera"
                        + " label --auto",
                () -> {
                    assertThat(enabledAgain).isTrue();
                    assertThat(afterAuto).matches("(?s)" + Pattern.quote(AUTO_LABEL + " started") + "(?:; .+)?");
                    assertThat(autoStarted).isEqualTo("label --auto");
                });
    }

    /** The folders the test lists as not relevant, the first with no label file, the second over one. */
    private static final String FIRST_FOLDER_NOT_RELEVANT = "a";

    private static final String SECOND_FOLDER_NOT_RELEVANT = "b";

    /**
     * A label file of three made-up documents, one in each of the folders a, b and c, so that with a and b listed two
     * are labelled not relevant and one relevant.
     */
    private static final String A_LABEL_FILE = """
            ---
            generatedUnderRun: "run-of-the-sample"
            generatedUnderEmbedder: "embedder@digest"
            generatedUnderSeedSet: "/seeds"
            documents:
            - path: "a/first.pdf"
              score: 0.83
              band: 2
              closestSeed: "seed/one.docx"
              relevant: true
            - path: "b/second.txt"
              score: 0.1
              band: 0
              closestSeed: "seed/one.docx"
              relevant: null
            - path: "c/third.txt"
              score: 0.5
              band: 1
              closestSeed: "seed/two.docx"
              relevant: false
            """;

    /** The words beneath the Inclusions tab's tree, over the list of folders not relevant. */
    private static final String THE_FOLDERS_NOT_RELEVANT_WORDS = "Folders not relevant are still read: the documents"
            + " of relevance-labels.yaml below them are labelled not relevant, and no run changes.";

    /** The label model's hint, beneath its field in Services. */
    private static final String THE_LABEL_MODELS_HINT = "The local model that sets the labels when you press Auto Label.";

    /** A working directory's next value where its last report says the labels are to be saved next. */
    private static final String LABELS_TO_SAVE = "labels to save";

    /** A form of the word answer, as a whole word in any case. */
    private static final Pattern AN_ANSWER =
            Pattern.compile("(?<![\\w-])answer(?:s|ed|ing)?(?![\\w-])", Pattern.CASE_INSENSITIVE);

    /** The label commands' former names. */
    private static final List<String> THE_FORMER_NAMES = List.of("Record my answers", "Let the local model answer");

    /**
     * ADR-413 §3: the texts of the window that called a relevance label an answer say label. The window is shown over a
     * corpus root and a working directory; a folder is added as not relevant there with no label file, and another once
     * the test has written one.
     */
    @Test
    @Story("The label commands stand in the sidebar")
    @DisplayName("The folders not relevant, the label model's hint and a working directory's next value say label where they said answer, and no text names Record my answers or Let the local model answer")
    @Issue("857")
    @Link(name = "ADR-413", url = Adr.THE_LABEL_COMMANDS_ARE_A_LABELS_SECTION_OF_THE_SIDEBAR, type = "adr")
    void theWindowSaysLabelWhereItSaidAnswer(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        Path listed = working.resolve(NotRelevantFolders.FILE_NAME);
        List<String> beneathTheTree;
        String hint;
        String withNoLabelFile;
        String overALabelFile;
        List<String> formerNames;
        try (WindowForTests window = WindowForTests.shown(state)) {
            beneathTheTree = window.read(root -> {
                Node tab = HeadlessToolkit.tabWithId(root, "inclusions").orElseThrow().getContent();
                List<String> said = new java.util.ArrayList<>(textsUnder(tab));
                said.add(WindowForTests.caption(HeadlessToolkit.nodeWithId((Parent) tab, "not-relevant-list")
                        .orElseThrow()));
                return said;
            });
            hint = window.read(root -> WindowForTests.caption(root, "field-vespera.label.model"));
            withNoLabelFile = addedAsNotRelevant(window, FIRST_FOLDER_NOT_RELEVANT);
            Files.writeString(working.resolve(LabelAnswers.FILE_NAME), A_LABEL_FILE, StandardCharsets.UTF_8);
            overALabelFile = addedAsNotRelevant(window, SECOND_FOLDER_NOT_RELEVANT);
            formerNames = window.read(root -> textsUnder(root).stream()
                    .filter(text -> THE_FORMER_NAMES.stream().anyMatch(text::contains)).toList());
        }
        Path elsewhere = Files.createDirectories(folder.resolve("elsewhere"));
        Files.writeString(elsewhere.resolve(InvocationReportFile.FILE_NAME), aReportWantingTheLabelsSaved(),
                StandardCharsets.UTF_8);
        String next = WorkingDirectoryRow.read(elsewhere, ZoneOffset.UTC).next();

        claim("in the Inclusions tab, the caption of the list of folders not relevant says they label not relevant the"
                        + " documents of relevance-labels.yaml below them",
                () -> assertThat(beneathTheTree).contains(THE_FOLDERS_NOT_RELEVANT_WORDS));
        claim("and no text of the tab says answer, in any of its forms",
                () -> assertThat(beneathTheTree).noneMatch(text -> AN_ANSWER.matcher(text).find()));
        claim("a folder added as not relevant with no label file says relevance-labels.yaml holds no document to label",
                () -> assertThat(withNoLabelFile).isEqualTo("the folders not relevant are saved in " + listed
                        + "; relevance-labels.yaml holds no document to label yet"));
        claim("a second added over a label file of three documents, one in each of the folders a, b and c, says the"
                        + " three were labelled, the two in a and b not relevant and the one in c relevant",
                () -> assertThat(overALabelFile).isEqualTo("the folders not relevant are saved in " + listed
                        + ", and the 3 documents of relevance-labels.yaml labelled: 2 not relevant, 1 relevant"));
        claim("the label model's hint, the caption of its field, says it sets the labels when Auto Label is pressed",
                () -> assertThat(hint).isEqualTo(THE_LABEL_MODELS_HINT));
        claim("a working directory whose last report wants the labels saved next shows " + LABELS_TO_SAVE
                        + " as its next value",
                () -> assertThat(next).isEqualTo(LABELS_TO_SAVE));
        claim("and no text of the window names a label command by " + THE_FORMER_NAMES,
                () -> assertThat(formerNames).isEmpty());
    }

    /** Types {@code typed} as a folder not relevant, presses Add, and answers the line the pane then says. */
    private static String addedAsNotRelevant(WindowForTests window, String typed) throws Exception {
        window.act(root -> {
            WindowForTests.node(root, "not-relevant-folder", TextInputControl.class).setText(typed);
            WindowForTests.node(root, "not-relevant-add", Button.class).fire();
        });
        return window.read(root -> textOf(WindowForTests.node(root, "not-relevant-outcome", Node.class)));
    }

    /** Every text shown under {@code node}, a label's or a text control's, none blank. */
    private static List<String> textsUnder(Node node) {
        return HeadlessToolkit.everyNodeUnder(node).stream().map(TheControlPanelsWindowTest::textOf)
                .filter(text -> text != null && !text.isBlank()).toList();
    }

    /**
     * A {@code report.json} of an ended {@code vespera run}, in ADR-362 §3's shape, whose next invocation is a {@code
     * vespera label} with labels to save and no value wanted.
     */
    private static String aReportWantingTheLabelsSaved() {
        String at = "2026-11-17T08:00:00.000Z";
        String legs = Stream.of("deduplication", "relevance", "clustering", "generation")
                .map(leg -> "{\"name\":\"" + leg + "\",\"state\":\"not started\",\"open\":false}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"format\":" + InvocationReportFile.FORMAT + ",\"invocation\":{\"id\":\"20261117T080000Z\",\"pid\":4242,"
                + "\"command\":\"run\",\"started\":\"" + at + "\",\"ended\":\"" + at + "\",\"state\":\"ended\","
                + "\"outcome\":\"stopped\",\"exitCode\":0,\"writtenAt\":\"" + at + "\",\"interval\":1},"
                + "\"places\":{\"workingDirectory\":\"/work/elsewhere\",\"corpusRoot\":null,\"deliverableRoot\":null},"
                + "\"steps\":[],\"legs\":" + legs + ",\"arrivedAt\":[],\"next\":{\"invocation\":\"label\",\"wants\":[],"
                + "\"answer\":true,\"arrangement\":null}}";
    }

    /** The text a node shows, where it is a text of the window, a label's or a text control's. */
    private static String textOf(Node node) {
        return node instanceof javafx.scene.control.TextInputControl text ? text.getText()
                : node instanceof javafx.scene.control.Labeled labeled ? labeled.getText() : null;
    }

    /** Draws the window from a first start's state, shows it, and answers what {@code reading} reads of it. */
    private static <T> T inTheWindow(Function<Parent, T> reading) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(temporary.resolve("java"), temporary.resolve("vespera.jar")),
                temporary.resolve("launch-values.properties"),
                temporary.resolve("corpus-root-locks"),
                "tester");
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, WIDTH, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                return reading.apply(root);
            } finally {
                stage.close();
            }
        });
    }

    /** The nearest node of {@code kind} above {@code node}. */
    private static <K extends Parent> Optional<K> above(Node node, Class<K> kind) {
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (kind.isInstance(parent)) {
                return Optional.of(kind.cast(parent));
            }
        }
        return Optional.empty();
    }
}
