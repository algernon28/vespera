package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The model that names the topics is shown in the Services section and not set there (ADR-407 §10): beneath the label
 * model's field, the heading <i>Naming model</i> and one line of selectable text, {@code in-use-vespera.naming.model},
 * saying the value {@code effective-values.properties} names and where it came from in ADR-377 §4's words, or that the
 * jar has not said yet, read again whenever another working directory is chosen and whenever the window's own child
 * ends; and no field holding {@code vespera.naming.model}, since it is no launch value.
 *
 * <p>The window is shown on the Headless platform over a corpus root and a working directory of the test's own, the
 * file written in the working directory as the jar writes it, before the window opens.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("829")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TheNamingModelIsShownAndNotSetTest {

    /** The property, which no field of the window may hold as its id. */
    private static final String NAMING_MODEL = "vespera.naming.model";

    /** The line saying the model in use. */
    private static final String THE_LINE = "in-use-" + NAMING_MODEL;

    /** The label model's field, which the line stands beneath. */
    private static final String THE_LABEL_MODELS_FIELD = "vespera.label.model";

    /** The sidebar's section the line stands in. */
    private static final String SERVICES = "services";

    /** The heading above the line. */
    private static final String HEADING = "Naming model";

    /** A naming model of the test's own, said by the file to come from the environment. */
    private static final String A_NAMING_MODEL = "a-naming-model:4b";

    /** What the line says of it, the source in the words the profile's values in use are said in. */
    private static final String IN_USE_FROM_THE_ENVIRONMENT =
            "In use: " + A_NAMING_MODEL + ", from this computer's environment.";

    /** A second naming model, said by a second working directory's file to be the code's own. */
    private static final String ANOTHER_NAMING_MODEL = "another-naming-model:8b";

    /** What the line says of it. */
    private static final String ANOTHER_IN_USE_FROM_THE_CODE =
            "In use: " + ANOTHER_NAMING_MODEL + ", from Vespera's built-in value.";

    private static final String USER = System.getProperty("user.name");

    /** What the line says where the working directory holds no file. */
    private static final String NOT_SAID_YET =
            "The jar has not said yet what it would use; run an invocation to see it.";

    /** What the window shows of the naming model. */
    private record Shown(String text, boolean selectableText, boolean editable, Optional<Node> field,
            int fieldsPlace, int linesPlace, List<String> textsBesideTheLine) {
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("The Services section says which model names the topics and where that came from, beneath the label model's field, with no field to change it")
    void theServicesSectionSaysTheNamingModelInUse(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve(EffectiveValues.FILE_NAME),
                NAMING_MODEL + "=" + A_NAMING_MODEL + "\n" + NAMING_MODEL + ".source=environment\n",
                StandardCharsets.UTF_8);

        Shown shown = shownOver(folder, IN_USE_FROM_THE_ENVIRONMENT);

        claim("the line says the model the file names and that it comes from this computer's environment",
                () -> assertThat(shown.text()).isEqualTo(IN_USE_FROM_THE_ENVIRONMENT));
        claim("the line is selectable text, which cannot be edited",
                () -> assertThat(List.of(shown.selectableText(), shown.editable())).containsExactly(true, false));
        claim("no node of the window holds the naming model's property as its id, so there is no field to change it",
                () -> assertThat(shown.field()).isEmpty());
        claim("the line stands in the Services section after the label model's field, under its heading",
                () -> {
                    assertThat(shown.fieldsPlace()).as("the label model's field's place in the section").isNotNegative();
                    assertThat(shown.linesPlace()).as("the line's place in the section").isGreaterThan(shown.fieldsPlace());
                    assertThat(shown.textsBesideTheLine()).containsSubsequence(HEADING, IN_USE_FROM_THE_ENVIRONMENT);
                });
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("With no file of the values in use, the line for the model that names the topics says the jar has not said yet")
    void withNoFileTheLineSaysTheJarHasNotSaidYet(@TempDir Path folder) throws Exception {
        Files.createDirectories(folder.resolve("working"));

        Shown shown = shownOver(folder, NOT_SAID_YET);

        claim("with no file in the working directory, the line says the jar has not said yet what it would use",
                () -> assertThat(shown.text()).isEqualTo(NOT_SAID_YET));
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("Choosing another working directory shows the model that names the topics as that directory's own file says it, and the jar has not said where it has none")
    void anotherWorkingDirectoryChosenIsReadAgain(@TempDir Path folder) throws Exception {
        Path first = Files.createDirectories(folder.resolve("first"));
        Path second = Files.createDirectories(folder.resolve("second"));
        Path withNoFile = Files.createDirectories(folder.resolve("with-no-file"));
        says(first, A_NAMING_MODEL, "environment");
        says(second, ANOTHER_NAMING_MODEL, "code");
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(second, withNoFile), line -> { });
        state.hold(launchValues(folder, first));

        String asItOpened;
        String inTheSecond;
        String inTheOneWithNoFile;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asItOpened = window.awaiting(TheNamingModelIsShownAndNotSetTest::theLine, IN_USE_FROM_THE_ENVIRONMENT::equals);
            choose(window, second);
            inTheSecond = window.awaiting(TheNamingModelIsShownAndNotSetTest::theLine,
                    ANOTHER_IN_USE_FROM_THE_CODE::equals);
            choose(window, withNoFile);
            inTheOneWithNoFile = window.awaiting(TheNamingModelIsShownAndNotSetTest::theLine, NOT_SAID_YET::equals);
        }

        claim("as the window opens on the first working directory, the line says the model its file names",
                () -> assertThat(asItOpened).isEqualTo(IN_USE_FROM_THE_ENVIRONMENT));
        claim("the second chosen, the line says the model the second's own file names, and where that came from",
                () -> assertThat(inTheSecond).isEqualTo(ANOTHER_IN_USE_FROM_THE_CODE));
        claim("and a working directory with no file chosen, the line says the jar has not said yet, and not the model"
                        + " of the directory chosen before it",
                () -> assertThat(inTheOneWithNoFile).isEqualTo(NOT_SAID_YET));
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("When the window's own command ends, the line for the model that names the topics says what the working directory's file then names")
    void theWindowsOwnChildEndedIsReadAgain(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        state.hold(launchValues(folder, working));

        String asItOpened;
        String afterItEnded;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asItOpened = window.awaiting(TheNamingModelIsShownAndNotSetTest::theLine, NOT_SAID_YET::equals);
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the sleeping child is started").isEmpty();
            // as an invocation of the jar writes the file while it holds the working directory
            says(working, A_NAMING_MODEL, "environment");
            state.stop();
            assumeThat(state.ending().map(Ending::howItEnded)).as("the child ended as stopped")
                    .contains(HowItEnded.STOPPED);
            afterItEnded = window.awaiting(TheNamingModelIsShownAndNotSetTest::theLine,
                    IN_USE_FROM_THE_ENVIRONMENT::equals);
        }

        claim("the window having opened on no file, the line says the jar has not said yet",
                () -> assertThat(asItOpened).isEqualTo(NOT_SAID_YET));
        claim("and once its own child has ended, the line says the model the file then names",
                () -> assertThat(afterItEnded).isEqualTo(IN_USE_FROM_THE_ENVIRONMENT));
    }

    /** The window over {@code folder}'s corpus root and working directory, read once its line says {@code awaited}. */
    private static Shown shownOver(Path folder, String awaited) throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), folder.resolve("working").toString(), "", "", "", "", "", ""));
        try (WindowForTests window = WindowForTests.shown(state)) {
            return window.awaiting(TheNamingModelIsShownAndNotSetTest::read, seen -> awaited.equals(seen.text()));
        }
    }

    private static Shown read(Parent root) {
        TextInputControl line = WindowForTests.node(root, THE_LINE, TextInputControl.class);
        Node section = WindowForTests.section(root, SERVICES).getContent();
        List<Node> parts = section instanceof Parent parent ? List.copyOf(parent.getChildrenUnmodifiable()) : List.of();
        int fieldsPlace = placeHolding(parts, THE_LABEL_MODELS_FIELD);
        int linesPlace = placeHolding(parts, THE_LINE);
        List<String> beside = linesPlace < 0 ? List.of() : HeadlessToolkit.everyNodeUnder(parts.get(linesPlace)).stream()
                .filter(SelectableText.class::isInstance)
                .map(node -> ((TextInputControl) node).getText())
                .toList();
        return new Shown(line.getText(), line instanceof SelectableText, line.isEditable(),
                HeadlessToolkit.nodeWithId(root, NAMING_MODEL), fieldsPlace, linesPlace, beside);
    }

    /** The place among {@code parts} of the one holding the node whose id is {@code id}, or -1 where none does. */
    private static int placeHolding(List<Node> parts, String id) {
        for (int place = 0; place < parts.size(); place++) {
            if (HeadlessToolkit.everyNodeUnder(parts.get(place)).stream().anyMatch(node -> id.equals(node.getId()))) {
                return place;
            }
        }
        return -1;
    }

    /** The line's text. */
    private static String theLine(Parent root) {
        return WindowForTests.node(root, THE_LINE, TextInputControl.class).getText();
    }

    /** Writes {@code folder}'s file of the values in use as the jar writes it, naming {@code model} from {@code source}. */
    private static void says(Path folder, String model, String source) throws Exception {
        Files.writeString(folder.resolve(EffectiveValues.FILE_NAME),
                NAMING_MODEL + "=" + model + "\n" + NAMING_MODEL + ".source=" + source + "\n", StandardCharsets.UTF_8);
    }

    /** Chooses {@code folder}'s row in the list of working directories, as the operator does. */
    @SuppressWarnings("unchecked")
    private static void choose(WindowForTests window, Path folder) throws Exception {
        window.act(root -> {
            ListView<Path> list = WindowForTests.node(root, "working-directories", ListView.class);
            Path row = list.getItems().stream()
                    .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), folder.toString()))
                    .findFirst().orElseThrow(() -> new AssertionError("the list holds no row for " + folder));
            list.getSelectionModel().select(row);
        });
    }

    /** The launch values of a corpus root under {@code folder} and the working directory {@code working}. */
    private static LaunchValues launchValues(Path folder, Path working) throws Exception {
        return new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(), "",
                "", "", "", "", "");
    }

    /**
     * A state as {@link WindowForTests#aState} makes it, its machine's folder {@code <folder>/machine/.vespera} as
     * {@code vespera.working-dir} set on the JVM names it.
     */
    private static ControlPanelState aStateOver(Path folder, ChildRuntime runtime) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, runtime);
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", folder.resolve("machine").resolve(".vespera").toString()), Map.of()));
        return state;
    }

    /** A runtime whose java is not there, for a test that starts no child. */
    private static ChildRuntime notStarting(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }
}
