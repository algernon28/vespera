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
import java.util.Map;
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
 *
 * <p>A publish whose parent's title names no page or folder of the space, or several, ends with an exit code of its own
 * where the control panel started it, and the window warns, asking for a different title (ADR-383): a title entered is
 * saved as {@code confluenceFolderTitle} and the publish is started again, the token asked for again; Cancel, or a
 * blank title, saves nothing and starts nothing. The child is {@link ATitleCheckingChildForTests}, which reads the title
 * in {@code profile.yaml} as the jar does and exits as the jar would.
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

    /** The jar of {@link ATitleCheckingChildForTests}, written once for the test JVM outside every test's folder. */
    private static Path titleCheckingJar;

    /** The space's key the profile names. */
    private static final String SPACE = "KT";

    /** The provenance the profile states for the two values the test writes in it. */
    private static final String THE_TESTS_PROVENANCE = "set by the test";

    /** The provenance the window saves a value it changed with (ADR-343 §2). */
    private static final String SET_IN_THE_CONTROL_PANEL = "set in the control panel";

    /** The warning's title, and its sentences, the parent's title naming no page or folder, and several (ADR-383 §3). */
    private static final String WARNING_TITLE = "The parent's title";

    private static final String WARNING_OF_NO_PAGE = "No page or folder of the space " + SPACE + " is titled "
            + ATitleCheckingChildForTests.TITLE_OF_NO_PAGE + ", so the publish to Confluence sent nothing. Enter a"
            + " different title for the parent the pages are published under: it is saved as confluenceFolderTitle in"
            + " the Profile window, and the publish starts again.";

    private static final String WARNING_OF_SEVERAL = "Several pages or folders of the space " + SPACE + " are titled "
            + ATitleCheckingChildForTests.TITLE_OF_SEVERAL + ", so the publish to Confluence sent nothing. Enter a"
            + " different title for the parent the pages are published under: it is saved as confluenceFolderTitle in"
            + " the Profile window, and the publish starts again.";

    /** The window's outcome line where the warning is cancelled, or answered with blanks (ADR-383 §5, ADR-388 §6). */
    private static final String LEFT_AS_IT_WAS = "the publish to Confluence sent nothing: confluenceFolderTitle is left"
            + " as profile.yaml states it; name the parent by confluenceFolderTitle or confluenceFolderId in the Profile"
            + " window, and publish again";

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
    @Story("A parent's title naming no page or several asks for another")
    @Link(name = "ADR-383", url = Adr.A_PARENT_TITLE_NAMING_NO_PAGE_OR_SEVERAL_ASKS_FOR_ANOTHER_IN_A_WARNING, type = "adr")
    @Link(name = "ADR-388", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
    @DisplayName("A publish whose parent's title names no page of the space opens one warning naming the space and the title; the title entered is saved in profile.yaml as set in the control panel, and the publish starts again, asking for the token again")
    void aTitleNamingNoPageAsksForAnotherSavesItAndPublishesAgain(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("confluence"), "pages.json");
        Path profile = aProfileTitling(workingDirectory, ATitleCheckingChildForTests.TITLE_OF_NO_PAGE);
        ControlPanelState state = aStateOver(folder, workingDirectory, new ChildRuntime(java(), titleCheckingJar()));

        boolean endedAgain;
        Optional<Ending> ending;
        List<AnsweringDialogs.Warned> warned;
        List<String> asked;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.dialogs.typed = Optional.of(TOKEN);
            window.dialogs.entered = Optional.of(ATitleCheckingChildForTests.TITLE_ENTERED);
            window.awaiting(PublishToConfluenceAsksForTheTokenTest::boxes, drawn -> drawn.contains("confluence"));
            window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
            endedAgain = WindowForTests.within(() -> state.ending().filter(
                    ended -> ended.exitCode() != ATitleCheckingChildForTests.NO_PAGE_SO_TITLED).isPresent());
            ending = state.ending();
            warned = List.copyOf(window.dialogs.warned);
            asked = List.copyOf(window.dialogs.maskedAsked);
        }

        claim("the publish ending " + ATitleCheckingChildForTests.NO_PAGE_SO_TITLED + " opened one warning, naming the"
                        + " space and the title no page holds, its field holding that title",
                () -> assertThat(warned).containsExactly(new AnsweringDialogs.Warned(WARNING_TITLE, WARNING_OF_NO_PAGE,
                        ATitleCheckingChildForTests.TITLE_OF_NO_PAGE)));
        claim("profile.yaml now states the title entered as the parent's, saved as set in the control panel, and the"
                        + " space as it was",
                () -> assertThat(ProfileEdit.read(Files.readString(profile, StandardCharsets.UTF_8))).isEqualTo(Map.of(
                        ProfileKey.CONFLUENCE_SPACE_KEY, new ProfileEdit.Stated(SPACE, THE_TESTS_PROVENANCE),
                        ProfileKey.CONFLUENCE_FOLDER_TITLE, new ProfileEdit.Stated(
                                ATitleCheckingChildForTests.TITLE_ENTERED, SET_IN_THE_CONTROL_PANEL))));
        claim("the token was asked for again, once for each publish started",
                () -> assertThat(asked).hasSize(2));
        claim("and the publish started again ended " + ATitleCheckingChildForTests.AS_EXPECTED + ", having read the"
                        + " title entered and been given the token typed",
                () -> assertThat(List.of(endedAgain, ending.map(Ending::exitCode)))
                        .containsExactly(true, Optional.of(ATitleCheckingChildForTests.AS_EXPECTED)));
    }

    @Test
    @Story("A parent's title naming no page or several asks for another")
    @Link(name = "ADR-383", url = Adr.A_PARENT_TITLE_NAMING_NO_PAGE_OR_SEVERAL_ASKS_FOR_ANOTHER_IN_A_WARNING, type = "adr")
    @Link(name = "ADR-388", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
    @DisplayName("A publish whose parent's title names several pages warns so; the warning cancelled, or answered with blanks, saves nothing, starts nothing and says the title is left as it was")
    void aTitleNamingSeveralAsksTooAndCancelOrBlanksSaveAndStartNothing(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("confluence"), "pages.json");
        Path profile = aProfileTitling(workingDirectory, ATitleCheckingChildForTests.TITLE_OF_SEVERAL);
        byte[] asWritten = Files.readAllBytes(profile);
        ControlPanelState state = aStateOver(folder, workingDirectory, new ChildRuntime(java(), titleCheckingJar()));

        List<String> outcomes = new ArrayList<>();
        List<AnsweringDialogs.Warned> warned;
        List<String> asked;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.dialogs.typed = Optional.of(TOKEN);
            window.awaiting(PublishToConfluenceAsksForTheTokenTest::boxes, drawn -> drawn.contains("confluence"));
            for (Optional<String> answer : List.of(Optional.<String>empty(), Optional.of("   "))) {
                int before = window.dialogs.warned.size();
                window.dialogs.entered = answer;
                window.awaiting(root -> WindowForTests.node(root, "publish", Button.class).isDisabled(),
                        disabled -> !disabled);
                window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
                WindowForTests.within(() -> window.dialogs.warned.size() > before);
                outcomes.add(window.read(
                        root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText()));
            }
            warned = List.copyOf(window.dialogs.warned);
            asked = List.copyOf(window.dialogs.maskedAsked);
        }

        claim("each publish ending " + ATitleCheckingChildForTests.SEVERAL_SO_TITLED + " opened one warning, saying"
                        + " several pages hold the title, its field holding that title",
                () -> assertThat(warned).containsExactly(
                        new AnsweringDialogs.Warned(WARNING_TITLE, WARNING_OF_SEVERAL,
                                ATitleCheckingChildForTests.TITLE_OF_SEVERAL),
                        new AnsweringDialogs.Warned(WARNING_TITLE, WARNING_OF_SEVERAL,
                                ATitleCheckingChildForTests.TITLE_OF_SEVERAL)));
        claim("cancelled, then answered with blanks, it said each time that the title is left as it was",
                () -> assertThat(outcomes).containsExactly(LEFT_AS_IT_WAS, LEFT_AS_IT_WAS));
        claim("no publish was started again: the token was asked for once for each Publish pressed and no more",
                () -> assertThat(asked).hasSize(2));
        claim("and profile.yaml is byte for byte as the test wrote it",
                () -> assertThat(Files.readAllBytes(profile)).isEqualTo(asWritten));
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

    /**
     * The working directory's {@code profile.yaml}, naming the space {@link #SPACE} and the parent by {@code title},
     * each with {@link #THE_TESTS_PROVENANCE}, as the Profile window writes a value.
     */
    private static Path aProfileTitling(Path workingDirectory, String title) throws IOException {
        String text = ProfileEdit.rewrite(Optional.empty(), ProfileKey.CONFLUENCE_SPACE_KEY, SPACE, THE_TESTS_PROVENANCE);
        text = ProfileEdit.rewrite(Optional.of(text), ProfileKey.CONFLUENCE_FOLDER_TITLE, title, THE_TESTS_PROVENANCE);
        return Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text, StandardCharsets.UTF_8);
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

    private static synchronized Path titleCheckingJar() throws IOException {
        if (titleCheckingJar == null) {
            Path jarFolder = Files.createTempDirectory("vespera-title-checking-jar");
            jarFolder.toFile().deleteOnExit();
            titleCheckingJar = SleepingJar.write(jarFolder.resolve("title-checking.jar"),
                    ATitleCheckingChildForTests.class);
            titleCheckingJar.toFile().deleteOnExit();
        }
        return titleCheckingJar;
    }
}
