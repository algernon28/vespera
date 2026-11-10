package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The API token, as the window starts a publish to Confluence (ADR-359 §3): Publish with Confluence's box ticked asks
 * for it in a masked field each time, starts nothing where the field is cancelled or left blank, and passes what is
 * typed to the one child it starts, in that child's environment alone, writing it nowhere and showing it nowhere. With
 * the website's box alone ticked, nothing is asked.
 *
 * <p>The child that sees the token is a jar of the tests' own, {@link ATokenCheckingChildForTests}, which prints nothing
 * and says by its exit code what it was given.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
@Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
class PublishToConfluenceAsksForTheTokenTest {

    /** A generation run's folder, as the trees under the working directory's {@code deliverable} folder are named. */
    private static final String A_RUN = "abcdef0123456789";

    private static final String TOKEN = ATokenCheckingChildForTests.TOKEN;

    /** The jar of {@link ATokenCheckingChildForTests}, written once for the test JVM outside every test's folder. */
    private static Path tokenCheckingJar;

    @Test
    @Story("The control panel passes the API token to its child alone")
    @DisplayName("Publish to Confluence asks once for the token in a masked field, its child sees the token typed in its environment and in no argument, and no file, no text of the window and nothing the control panel holds names it")
    void theTokenTypedReachesTheChildAndIsWrittenNowhere(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("confluence"), "pages.json");
        ControlPanelState state = aStateOver(folder, workingDirectory, new ChildRuntime(java(), tokenCheckingJar()));

        List<String> asked;
        boolean ended;
        Optional<Ending> ending;
        List<String> shown;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.dialogs.typed = Optional.of(TOKEN);
            window.awaiting(PublishToConfluenceAsksForTheTokenTest::boxes, drawn -> drawn.contains("confluence"));
            window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
            ended = WindowForTests.within(() -> state.ending().isPresent());
            ending = state.ending();
            asked = List.copyOf(window.dialogs.maskedAsked);
            shown = window.read(PublishToConfluenceAsksForTheTokenTest::everyText);
        }

        claim("Publish asked once, in a masked field, for the token",
                () -> assertThat(asked).hasSize(1).allSatisfy(sentence -> assertThat(sentence).isNotBlank()));
        claim("the child it started ended",
                () -> assertThat(ended).isTrue());
        claim("having found the token typed in its environment as VESPERA_CONFLUENCE_TOKEN, been told the control panel"
                        + " started it, and found the token in none of its arguments: it exits "
                        + ATokenCheckingChildForTests.AS_EXPECTED + " where it was given all three, "
                        + ATokenCheckingChildForTests.NOT_THE_TOKEN_TYPED + " where the token was not the one typed, "
                        + ATokenCheckingChildForTests.THE_TOKEN_IN_AN_ARGUMENT + " where an argument held it and "
                        + ATokenCheckingChildForTests.NOT_TOLD_THE_CONTROL_PANEL_STARTED_IT + " where it was not told",
                () -> assertThat(ending.map(Ending::exitCode)).contains(ATokenCheckingChildForTests.AS_EXPECTED));
        claim("no file under the test's folder holds the token: not the launch values' file, not profile.yaml, not a file"
                        + " of the working directory",
                () -> assertThat(everyFileUnder(folder)).isNotEmpty().allSatisfy(file -> assertThat(
                                new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                        .as(file.toString()).doesNotContain(TOKEN)));
        claim("no text of the window holds it",
                () -> assertThat(shown).isNotEmpty().noneMatch(text -> text.contains(TOKEN)));
        claim("and nothing the control panel holds of the start names it: its launch values, the command it says it"
                        + " started and the child's output",
                () -> assertThat(List.of(String.valueOf(state.values()), state.childCommand(),
                        String.join("\n", state.tail()))).noneMatch(text -> text.contains(TOKEN)));
    }

    @Test
    @Story("The control panel passes the API token to its child alone")
    @DisplayName("With the token's field cancelled, or answered with blanks, Publish to Confluence starts nothing and says so in one line")
    void cancelledOrBlankPublishStartsNothing(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("confluence"), "pages.json");
        ControlPanelState state = aStateOver(folder, workingDirectory, EndingJar.runtime());

        List<Optional<String>> cancelledThenBlank = List.of(Optional.<String>empty(), Optional.of("   "));
        List<String> said = new ArrayList<>();
        List<Boolean> started = new ArrayList<>();
        List<String> asked;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(PublishToConfluenceAsksForTheTokenTest::boxes, drawn -> drawn.contains("confluence"));
            for (Optional<String> typed : cancelledThenBlank) {
                window.dialogs.typed = typed;
                window.act(root -> WindowForTests.node(root, "outcome", TextInputControl.class).setText(""));
                window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
                said.add(window.read(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText()));
                started.add(state.childWasStarted());
            }
            asked = List.copyOf(window.dialogs.maskedAsked);
        }

        claim("the field was asked each time Publish was pressed, once cancelled and once answered with blanks",
                () -> assertThat(asked).hasSameSizeAs(cancelledThenBlank));
        claim("neither press started a child",
                () -> assertThat(started).containsExactly(false, false));
        claim("and each said why in one line",
                () -> assertThat(said).hasSameSizeAs(cancelledThenBlank)
                        .allSatisfy(line -> assertThat(line).isNotBlank().doesNotContain("\n")));
    }

    @Test
    @Story("The control panel passes the API token to its child alone")
    @DisplayName("Publish to the website alone asks for no token")
    void aPublishToTheWebsiteAloneAsksForNoToken(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("website"), "files.json");
        ControlPanelState state = aStateOver(folder, workingDirectory, EndingJar.runtime());

        List<String> asked;
        boolean started;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(PublishToConfluenceAsksForTheTokenTest::boxes, drawn -> drawn.contains("website"));
            window.act(root -> WindowForTests.node(root, "publish-to-website", CheckBox.class).setSelected(true));
            window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
            started = state.childWasStarted();
            asked = List.copyOf(window.dialogs.maskedAsked);
        }

        claim("Publish started the publish to the website", () -> assertThat(started).isTrue());
        claim("and asked for no token", () -> assertThat(asked).isEmpty());
    }

    /** The destinations of the boxes {@code publish-to} holds, ticked or not, in order. */
    private static List<String> boxes(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "publish-to")
                .map(HeadlessToolkit::everyNodeUnder)
                .orElse(List.of())
                .stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .map(box -> box.getId().substring("publish-to-".length()))
                .toList();
    }

    /** Every text the window shows: of each label, button, box and field. */
    private static List<String> everyText(Parent root) {
        List<String> texts = new ArrayList<>();
        for (Node node : HeadlessToolkit.everyNodeUnder(root)) {
            if (node instanceof Labeled labeled && labeled.getText() != null) {
                texts.add(labeled.getText());
            } else if (node instanceof TextInputControl field && field.getText() != null) {
                texts.add(field.getText());
            }
        }
        return texts;
    }

    private static List<Path> everyFileUnder(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    /** A rendering's list, {@code name}, in {@code rendering}, which says the rendering is drawn (ADR-339 §3). */
    private static void aListAt(Path rendering, String name) throws IOException {
        Files.writeString(Files.createDirectories(rendering).resolve(name), "{}", StandardCharsets.UTF_8);
    }

    /** A state holding a corpus root and {@code workingDirectory} under {@code folder} as launch values, starting {@code runtime}. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory, ChildRuntime runtime)
            throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(folder, runtime);
        state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }

    private static Path java() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    }

    private static synchronized Path tokenCheckingJar() throws IOException {
        if (tokenCheckingJar == null) {
            Path jarFolder = Files.createTempDirectory("vespera-token-checking-jar");
            jarFolder.toFile().deleteOnExit();
            tokenCheckingJar = SleepingJar.write(jarFolder.resolve("token-checking.jar"), ATokenCheckingChildForTests.class);
            tokenCheckingJar.toFile().deleteOnExit();
        }
        return tokenCheckingJar;
    }
}
