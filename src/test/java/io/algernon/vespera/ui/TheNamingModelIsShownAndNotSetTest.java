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
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The model that names the topics is shown in the Services section and not set there (ADR-403 §10): beneath the label
 * model's field, the heading <i>Naming model</i> and one line of selectable text, {@code in-use-vespera.naming.model},
 * saying the value {@code effective-values.properties} names and where it came from in ADR-377 §4's words, or that the
 * jar has not said yet; and no field holding {@code vespera.naming.model}, since it is no launch value.
 *
 * <p>The window is shown on the Headless platform over a corpus root and a working directory of the test's own, the
 * file written in the working directory as the jar writes it, before the window opens.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("829")
@Link(name = "ADR-403", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
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
}
