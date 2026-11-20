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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * As the control panel's window opens it starts nothing and checks five things instead, the Docker daemon, the Docling
 * and Ollama containers and the two models in force (ADR-430 §1, §3); the check is a list of two columns in the header,
 * between the titles and the theme switch, each row OK, KO or a dash where it is not asked, and the status line gets
 * nothing from it (§2); and it is done again every 5 seconds, and at once after a start, a shut-down and the window's own
 * child ending (§4).
 *
 * <p>The window is given a {@link RecordingDockerControl}, so nothing is run or asked: what the check runs is {@code
 * TheCheckOfTheDockerDaemonStartsNothingTest}'s. The models' names are the test's own, named in the sources the window
 * starts from and in the working directory's {@code profile.yaml}, never a model the jar ships.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheWindowChecksAndStartsNothingAsItOpensTest {

    /** The models the sources name, as {@code application.yaml} beside the jar would. */
    static final String EMBEDDING_MODEL = "an-embedding-model:1";

    static final String GENERATIVE_MODEL = "a-generative-model:2";

    /** The generative model the working directory's {@code profile.yaml} states, which comes before the sources'. */
    private static final String THE_PROFILES_MODEL = "the-profiles-model:3";

    /** The rows of the list, by the ids of their names, in the order the columns hold them. */
    static final List<String> ROWS = List.of("check-docker-daemon", "check-docling", "check-ollama",
            "check-embedding-model", "check-generative-model");

    private static final List<String> NAMES = List.of("Docker daemon", "Docling", "Ollama", "Embedding model",
            "Generative model");

    /** The mark of a row not asked. */
    static final String DASH = "–";

    /** How often the check is done again while the window is open. */
    private static final Duration EVERY = Duration.ofSeconds(5);

    /** How far a regular check may fall from {@link #EVERY} on a busy machine and still be the clock's. */
    private static final Duration LEEWAY = Duration.ofMillis(1500);

    /** Within this of a start, a shut-down or the child's ending, a check is the one done at once, not the clock's. */
    private static final Duration AT_ONCE = Duration.ofMillis(2500);

    @TempDir
    Path folder;

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("As the window opens it checks the addresses its Services fields hold and the models in force, starts nothing, and says nothing in its status line")
    void asItOpensTheWindowChecksWhatItsServicesFieldsHoldAndTheModelsInForceAndStartsNothing() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl();
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("profile.yaml"), "generationModel:\n  value: " + THE_PROFILES_MODEL
                + "\n  provenance: named by the test\n", StandardCharsets.UTF_8);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "http://localhost:15001", "http://localhost:21434", "", ""));
        state.useSources(withModels());

        String said;
        List<String> fields;
        try (WindowForTests window = WindowForTests.shown(state)) {
            WindowForTests.within(() -> !docker.checks.isEmpty());
            said = window.read(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText());
            fields = window.read(TheWindowAsksWhatItsServicesFieldsHoldTest::servicesFields);
        }

        claim("the window checked Docling's and Ollama's addresses and the converter's image as its Services fields"
                        + " hold them, which start from the launch values held",
                () -> assertThat(docker.checks).isNotEmpty().first()
                        .extracting(first -> TheWindowAsksWhatItsServicesFieldsHoldTest.services(first.values()))
                        .isEqualTo(fields).satisfies(asked -> assertThat(asked.subList(0, 2))
                                .containsExactly("http://localhost:15001", "http://localhost:21434")));
        claim("and the embedding model the sources name and the generative model profile.yaml states over theirs",
                () -> assertThat(docker.checks.getFirst().models())
                        .isEqualTo(new DockerControl.Models(EMBEDDING_MODEL, THE_PROFILES_MODEL)));
        claim("and Docker was asked to start, shut down or stop nothing", () -> assertThat(docker.calls).isEmpty());
        claim("and the status line says nothing of the check", () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("The check is a list in the header, between the titles and the theme switch: five rows in two columns, each OK in green or KO in red, no taller than the titles")
    void theCheckIsAListOfFiveRowsInTwoColumnsInTheHeaderBetweenTheTitlesAndTheThemeSwitch() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(RecordingDockerControl.everyRow(
                DockerCheck.Mark.OK, DockerCheck.Mark.KO, DockerCheck.Mark.OK, DockerCheck.Mark.OK, DockerCheck.Mark.KO));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        record Header(List<String> order, List<String> names, List<Integer> columns, List<String> marks,
                List<String> classes, double listHeight, double titlesHeight) {
        }
        Header header;
        try (WindowForTests window = WindowForTests.shown(state)) {
            header = window.awaiting(root -> {
                Node list = WindowForTests.node(root, "docker-check", Node.class);
                Parent holder = list.getParent();
                Node titles = holder.getChildrenUnmodifiable().stream()
                        .filter(child -> HeadlessToolkit.everyNodeUnder(child).stream()
                                .anyMatch(each -> "where".equals(each.getId()))).findFirst().orElseThrow();
                List<String> order = holder.getChildrenUnmodifiable().stream().map(child -> child == list ? "list"
                        : child == titles ? "titles"
                        : HeadlessToolkit.everyNodeUnder(child).stream()
                                .anyMatch(each -> "theme-toggle".equals(each.getId())) ? "theme switch" : "other")
                        .toList();
                return new Header(order,
                        ROWS.stream().map(id -> WindowForTests.node(root, id, Labeled.class).getText()).toList(),
                        ROWS.stream().map(id -> GridPane.getColumnIndex(WindowForTests.node(root, id, Node.class)))
                                .map(index -> index == null ? 0 : index).toList(),
                        ROWS.stream().map(id -> WindowForTests.node(root, id + "-mark", Labeled.class).getText())
                                .toList(),
                        ROWS.stream().map(id -> markClass(WindowForTests.node(root, id + "-mark", Node.class))).toList(),
                        list.getLayoutBounds().getHeight(), titles.getLayoutBounds().getHeight());
            }, read -> read.marks().contains("KO"));
        }

        claim("the header holds the titles, then the list, then the theme switch",
                () -> assertThat(header.order()).containsSubsequence("titles", "list", "theme switch"));
        claim("its rows read Docker daemon, Docling and Ollama, then Embedding model and Generative model",
                () -> assertThat(header.names()).containsExactlyElementsOf(NAMES));
        claim("the first three in one column and the two models in the next",
                () -> assertThat(header.columns().subList(0, 3)).containsOnly(header.columns().getFirst())
                        .doesNotContain(header.columns().get(3)));
        claim("and the two models in the same column",
                () -> assertThat(header.columns().get(4)).isEqualTo(header.columns().get(3)));
        claim("each row reads OK where it answered and KO where it did not, as the check said: Docling and the"
                        + " generative model KO",
                () -> assertThat(header.marks()).containsExactly("OK", "KO", "OK", "OK", "KO"));
        claim("an OK carries the class drawn green, a KO the class drawn red",
                () -> assertThat(header.classes()).containsExactly("ok", "ko", "ok", "ok", "ko"));
        claim("and the list is no taller than the titles, so the header grows no taller",
                () -> assertThat(header.listHeight()).isLessThanOrEqualTo(header.titlesHeight()));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("A row not asked reads a dash: the Docker daemon where both containers are on another machine, and the models while Ollama is KO")
    void aRowNotAskedReadsADash() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(RecordingDockerControl.bothElsewhere(
                "http://gpu-box.lan:5001", DockerCheck.Mark.OK, "http://gpu-box.lan:11434", DockerCheck.Mark.KO,
                DockerCheck.Mark.NOT_ASKED, DockerCheck.Mark.NOT_ASKED));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        List<String> marks;
        List<String> classes;
        try (WindowForTests window = WindowForTests.shown(state)) {
            marks = window.awaiting(root -> ROWS.stream()
                    .map(id -> WindowForTests.node(root, id + "-mark", Labeled.class).getText()).toList(),
                    read -> read.contains("KO"));
            classes = window.read(root -> ROWS.stream()
                    .map(id -> markClass(WindowForTests.node(root, id + "-mark", Node.class))).toList());
        }

        claim("the daemon, not asked, reads a dash; Docling OK; Ollama KO; and both models, not asked while Ollama is"
                        + " KO, a dash",
                () -> assertThat(marks).containsExactly(DASH, "OK", "KO", DASH, DASH));
        claim("each dash carries the class of a row not asked",
                () -> assertThat(classes).containsExactly("not-asked", "ok", "ko", "not-asked", "not-asked"));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("The window checks again every 5 seconds, and at once after a start, a shut-down and its own invocation ending")
    void itChecksAgainEveryFiveSecondsAndAtOnceAfterAStartAShutDownAndAnInvocationEnding() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(RecordingDockerControl.everyRow(
                DockerCheck.Mark.KO, DockerCheck.Mark.KO, DockerCheck.Mark.KO, DockerCheck.Mark.NOT_ASKED,
                DockerCheck.Mark.NOT_ASKED));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        docker.watching(state);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        List<Duration> regular = new ArrayList<>();
        Duration afterTheStart;
        Duration afterTheShutDown;
        Duration afterTheEnding;
        try (WindowForTests window = WindowForTests.shown(state)) {
            WindowForTests.within(() -> docker.checks.size() >= 3);
            List<Instant> first = docker.checks.stream().map(RecordingDockerControl.Checked::at).toList();
            regular.add(Duration.between(first.get(0), first.get(1)));
            regular.add(Duration.between(first.get(1), first.get(2)));

            // each act follows a check of the clock's at once, so the clock's next is about five seconds away
            window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.startsReturned.isEmpty());
            afterTheStart = firstCheckAfter(docker, docker.startsReturned.getFirst());

            firstCheckAfter(docker, Instant.now());
            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.shutDownsReturned.isEmpty());
            afterTheShutDown = firstCheckAfter(docker, docker.shutDownsReturned.getFirst());

            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            window.awaiting(root -> WindowForTests.node(root, "child", TextInputControl.class).getText(),
                    text -> text.startsWith("Running"));
            firstCheckAfter(docker, Instant.now());
            state.stop();
            afterTheEnding = firstCheckAfter(docker, Instant.now());
        }

        claim("with nothing happening, the checks came " + EVERY.toSeconds() + " seconds apart",
                () -> assertThat(regular).allSatisfy(gap -> assertThat(gap)
                        .isBetween(EVERY.minus(LEEWAY), EVERY.plus(LEEWAY))));
        claim("a check came at once after Start Docker returned, within " + AT_ONCE.toMillis() + " ms",
                () -> assertThat(afterTheStart).isLessThan(AT_ONCE));
        claim("another at once after Shut down Docker returned",
                () -> assertThat(afterTheShutDown).isLessThan(AT_ONCE));
        claim("and another at once after the window's own invocation ended",
                () -> assertThat(afterTheEnding).isLessThan(AT_ONCE));
    }

    /** How long after {@code instant} the first check after it came, waiting for one within the tests' bound. */
    static Duration firstCheckAfter(RecordingDockerControl docker, Instant instant) throws InterruptedException {
        WindowForTests.within(() -> !docker.checksAfter(instant).isEmpty());
        return docker.checksAfter(instant).stream().findFirst()
                .map(check -> Duration.between(instant, check.at())).orElse(Duration.ofDays(1));
    }

    /** The sources the window starts from, {@code application.yaml} beside the jar naming the test's two models. */
    static StartingValues.Sources withModels() {
        return new StartingValues.Sources(Map.of(), Map.of(), Map.of(), Map.of(
                "spring.ai.ollama.embedding.options.model", EMBEDDING_MODEL,
                "spring.ai.ollama.chat.options.model", GENERATIVE_MODEL));
    }

    /** Which of the three classes of a mark {@code mark} carries: {@code ok}, {@code ko} or {@code not-asked}. */
    static String markClass(Node mark) {
        return mark.getStyleClass().stream().filter(List.of("ok", "ko", "not-asked")::contains).findFirst()
                .orElse("none");
    }
}
