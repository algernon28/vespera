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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The label model's field of the Services section shows beneath it, while it is blank, the model the jar would use and
 * where that value came from (ADR-334 §2, ADR-344 §3 item 6): the line {@code in-use-vespera.label.model} reads
 * <i>"In use: ‹value›, from ‹source›."</i>, the source in ADR-377 §4's plain words, the value and its source being what
 * {@code effective-values.properties} in the working directory says of {@code vespera.label.model}; the line ends at
 * the value where ADR-377 §4 gives the source no words, and says that the jar has not said yet where the file is
 * absent. The field itself stays empty, so that it passes nothing. A field of white space alone is blank, since it
 * passes nothing either, and the line stays; a model typed in the field hides the line, the field holding it and the line
 * saying where it started (ADR-334 §2).
 *
 * <p>Each window is opened over a working directory holding the file as the jar writes it for the key, or none, its
 * state naming no label model in any source. The jar the state names is never written, so that the file is not older
 * than it and no word of a stale file is added (ADR-330 §2).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("846")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT,
        type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class AnEmptyLabelModelFieldShowsTheModelInUseTest {

    /** The label model's property, which is also its field's id. */
    private static final String LABEL_MODEL = "vespera.label.model";

    /** The id of the line beneath the field that says the model in use. */
    private static final String IN_USE_LINE = "in-use-" + LABEL_MODEL;

    /** The sidebar's section holding the field. */
    private static final String SERVICES = "services";

    /** The model the file names: the one the shipped settings name. */
    private static final String MODEL = "qwen3:8b";

    /** A source the jar may write that ADR-377 §4 gives no words for. */
    private static final String UNKNOWN = "unknown";

    /** What the line says where the working directory holds no file. */
    private static final String NOT_SAID_YET = "The jar has not said yet what it would use; run an invocation to see it.";

    /** A model the operator types into the field. */
    private static final String TYPED = "a-label-model:4b";

    /** What the field holds when only spaces are typed into it. */
    private static final String SPACES = "   ";

    /** What the window reads of the field and of the line beneath it. */
    private record Read(String field, boolean lineDrawn, String line, boolean shown, boolean inTheServicesSection) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    @Story("An empty field shows the value Vespera would use")
    @DisplayName("An empty label model field shows the model Vespera would use and, in plain words, where it came from, or that it has not said yet, and stays empty")
    void anEmptyLabelModelFieldShowsTheModelInUseAndWhereItCameFrom(String what, Optional<String> source, String expected,
            @TempDir Path folder) throws Exception {
        Read read;
        try (WindowForTests window = windowOver(folder, source)) {
            read = window.read(AnEmptyLabelModelFieldShowsTheModelInUseTest::readOf);
        }

        claim("the label model field stays empty, as no source names a model for it",
                () -> assertThat(read.field()).isEmpty());
        claim("the window draws the label model field's line saying the model in use",
                () -> assertThat(read.lineDrawn()).isTrue());
        claim("with " + what + ", the line reads \"" + expected + "\"",
                () -> assertThat(read.line()).isEqualTo(expected));
        claim("and the line is shown, in the Services section",
                () -> {
                    assertThat(read.shown()).isTrue();
                    assertThat(read.inTheServicesSection()).isTrue();
                });
    }

    /**
     * Each file the working directory may hold, by what the case is, the source written beside the model, empty where
     * there is no file, and the line it gives.
     */
    static Stream<Arguments> files() {
        return Stream.of(
                Arguments.of("the source shipped", Optional.of("shipped"), inUse("Vespera's settings")),
                Arguments.of("the source local-file", Optional.of("local-file"), inUse("your local settings file")),
                Arguments.of("the source command-line", Optional.of("command-line"),
                        inUse("the command that started Vespera")),
                Arguments.of("the source environment", Optional.of("environment"),
                        inUse("this computer's environment")),
                Arguments.of("the source code", Optional.of("code"), inUse("Vespera's built-in value")),
                Arguments.of("the source " + UNKNOWN + ", which has no plain words, the line ending at the model",
                        Optional.of(UNKNOWN), "In use: " + MODEL + "."),
                Arguments.of("no file in the working directory", Optional.empty(), NOT_SAID_YET));
    }

    @Test
    @Story("An empty field shows the value Vespera would use")
    @DisplayName("The label model's line stays while the field holds only spaces, goes once a model is typed, and comes back when the field is emptied")
    void theLineIsShownWhileTheFieldIsBlankAndHiddenOnceAModelIsTyped(@TempDir Path folder) throws Exception {
        Map<String, Read> after = new LinkedHashMap<>();
        try (WindowForTests window = windowOver(folder, Optional.of("shipped"))) {
            for (String typed : new String[] {SPACES, TYPED, ""}) {
                window.act(root -> WindowForTests.node(root, LABEL_MODEL, TextInputControl.class).setText(typed));
                after.put(typed, window.read(AnEmptyLabelModelFieldShowsTheModelInUseTest::readOf));
            }
        }

        claim("with only spaces in the field, which passes nothing, the line is shown and still names the model in use",
                () -> {
                    assertThat(after.get(SPACES).shown()).isTrue();
                    assertThat(after.get(SPACES).line()).isEqualTo(inUse("Vespera's settings"));
                });
        claim("with " + TYPED + " typed in the field, the line is neither visible nor managed",
                () -> assertThat(after.get(TYPED).shown()).isFalse());
        claim("and with the field emptied again, the line is shown again",
                () -> assertThat(after.get("").shown()).isTrue());
    }

    /** The line for the model the file names, from the source in {@code words}. */
    private static String inUse(String words) {
        return "In use: " + MODEL + ", from " + words + ".";
    }

    /**
     * The window over a working directory under {@code folder} whose file names the model from {@code source}, or that
     * holds no file where {@code source} is empty, its Services section expanded.
     */
    private static WindowForTests windowOver(Path folder, Optional<String> source) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        if (source.isPresent()) {
            Files.writeString(working.resolve(EffectiveValues.FILE_NAME),
                    LABEL_MODEL + "=" + MODEL + "\n" + LABEL_MODEL + ".source=" + source.get() + "\n",
                    StandardCharsets.UTF_8);
        }
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("no-jar-written.jar")));
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        WindowForTests window = WindowForTests.shown(state);
        try {
            window.act(root -> WindowForTests.section(root, SERVICES).setExpanded(true));
            return window;
        } catch (Exception | Error failed) {
            window.close();
            throw failed;
        }
    }

    /** What the window shows of the field and its line. */
    private static Read readOf(Parent root) {
        String field = WindowForTests.node(root, LABEL_MODEL, TextInputControl.class).getText();
        return HeadlessToolkit.nodeWithId(root, IN_USE_LINE)
                .map(line -> new Read(field, true, textOf(line), line.isVisible() && line.isManaged(),
                        HeadlessToolkit.everyNodeUnder(WindowForTests.section(root, SERVICES).getContent())
                                .contains(line)))
                .orElse(new Read(field, false, null, false, false));
    }

    /** The text a line of the window shows, a text control's or a label's. */
    private static String textOf(Node node) {
        return node instanceof TextInputControl text ? text.getText()
                : node instanceof Labeled labeled ? labeled.getText() : null;
    }
}
