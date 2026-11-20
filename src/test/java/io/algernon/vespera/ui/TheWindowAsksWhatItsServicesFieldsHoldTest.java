package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The check, <i>Start Docker</i> and the window's download ask what the Services section's fields hold as each begins,
 * not the launch values the last start stored (ADR-430 §3, §5, §7): Docling's and Ollama's addresses and the converter's
 * image as their fields read, a blank field standing for what the sources name, the environment, {@code .env} and
 * {@code application.yaml}, and never for the stored value. So an address corrected in its field is asked at the next
 * check, its row reads OK and Run is enabled, with no start in between, which the check would hold. The stop of the
 * containers as the window closes keeps the launch values held (ADR-390 §5).
 *
 * <p>Where the rows are read, the window is given {@link DockerControl#inProcess(DockerControl.Settings,
 * java.util.function.BiFunction)} over {@link LoopbackService}s, the daemon answered by the test, so no {@code wsl},
 * {@code systemctl} or {@code docker} command is run and nothing reaches Docling or Ollama; where what a start, a
 * download and a stop are given is read, a {@link RecordingDockerControl}. The models' names are the test's own.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheWindowAsksWhatItsServicesFieldsHoldTest {

    /** The ids of the inputs of Docling's address, Ollama's address and the converter's image, in that order. */
    static final List<String> SERVICES_FIELDS = List.of("vespera.docling.base-url", "spring.ai.ollama.base-url",
            "vespera.docling.image");

    /** Addresses the test types into the fields where a recording control stands for Docker, so nothing is asked there. */
    private static final String DOCLING_TYPED = "http://127.0.0.1:15001";

    private static final String OLLAMA_TYPED = "http://127.0.0.1:21434";

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    @TempDir
    Path folder;

    private LoopbackService docling;

    private LoopbackService ollama;

    /** Docling's and Ollama's addresses where nothing listens: each a loopback port the system gave and then took back. */
    private URI doclingNowhere;

    private URI ollamaNowhere;

    /** The two addresses the daemon was asked for, each time it was asked. */
    private final List<List<URI>> daemonAsked = new CopyOnWriteArrayList<>();

    @BeforeEach
    void listen() {
        docling = LoopbackService.started();
        ollama = LoopbackService.started();
        docling.answers("/health", LoopbackService.Answer.at(200));
        ollama.answers("/api/version", new LoopbackService.Answer(200, Duration.ZERO, "{\"version\":\"0.0.0\"}"));
        ollama.lists(TheWindowChecksAndStartsNothingAsItOpensTest.EMBEDDING_MODEL,
                TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL);
        try (LoopbackService gone = LoopbackService.started(); LoopbackService alsoGone = LoopbackService.started()) {
            doclingNowhere = gone.baseUrl();
            ollamaNowhere = alsoGone.baseUrl();
        }
    }

    @AfterEach
    void stop() {
        docling.close();
        ollama.close();
    }

    /**
     * The deadlock ADR-430 §3 closes: the stored addresses answer nothing, so Docling and Ollama read KO and hold Run;
     * Start Docker would store new ones, but the operator corrects the fields instead, and the next check asks them.
     */
    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("Docling's and Ollama's addresses corrected in their fields are asked at the next check, their rows read OK and Run is enabled, with nothing started")
    void anAddressCorrectedInItsFieldIsAskedAtTheNextCheckItsRowReadsOkAndRunIsOn() throws Exception {
        ControlPanelState state = aState(inProcess(StartingValues.Sources.none()));
        LaunchValues stored = state.values();
        List<String> before;
        String runBefore;
        List<String> doclingCorrected;
        List<String> bothCorrected;
        List<String> captions;
        List<Boolean> runAndAutoLabel;
        try (WindowForTests window = WindowForTests.shown(state)) {
            before = window.awaiting(TheWindowAsksWhatItsServicesFieldsHoldTest::serviceMarks,
                    marks -> marks.equals(List.of("KO", "KO")));
            runBefore = window.read(root -> WindowForTests.caption(root, "run-captioned"));

            window.act(root -> field(root, "vespera.docling.base-url").setText(docling.baseUrl().toString()));
            doclingCorrected = window.awaiting(TheWindowAsksWhatItsServicesFieldsHoldTest::serviceMarks,
                    marks -> marks.getFirst().equals("OK"));

            window.act(root -> field(root, "spring.ai.ollama.base-url").setText(ollama.baseUrl().toString()));
            runAndAutoLabel = window.awaiting(root -> List.of(
                            !WindowForTests.node(root, "run", Button.class).isDisabled(),
                            !WindowForTests.node(root, "label-auto", Button.class).isDisabled()),
                    both -> !both.contains(false));
            bothCorrected = window.read(TheWindowAsksWhatItsServicesFieldsHoldTest::serviceMarks);
            captions = window.read(root -> List.of(WindowForTests.caption(root, "check-docling"),
                    WindowForTests.caption(root, "check-ollama")));
        }

        claim("at the stored addresses, where nothing listens, Docling and Ollama read KO and Run waits for both",
                () -> assertThat(List.of(before, runBefore)).containsExactly(List.of("KO", "KO"),
                        "Off until Docling and Ollama show OK."));
        claim("Docling's address corrected in its field, the next check asked it there: its row reads OK, Ollama's"
                        + " still KO",
                () -> assertThat(doclingCorrected).containsExactly("OK", "KO"));
        claim("Ollama's corrected too, both read OK, and Run and Auto Label are enabled",
                () -> assertThat(List.of(bothCorrected, runAndAutoLabel)).containsExactly(List.of("OK", "OK"),
                        List.of(true, true)));
        claim("each row's caption names the address its field holds",
                () -> assertThat(captions).containsExactly("Docling: this machine, " + docling.baseUrl(),
                        "Ollama: this machine, " + ollama.baseUrl()));
        claim("the containers at the fields' addresses were asked, Docling at /health and Ollama at /api/version and"
                        + " /api/tags",
                () -> assertThat(List.of(docling.requestsTo("/health").isEmpty(),
                        ollama.requestsTo("/api/version").isEmpty(), ollama.requestsTo("/api/tags").isEmpty()))
                        .containsOnly(false));
        claim("the daemon was asked last for the two addresses the fields hold",
                () -> assertThat(daemonAsked).isNotEmpty().last()
                        .isEqualTo(List.of(docling.baseUrl(), ollama.baseUrl())));
        claim("and nothing was stored: the launch values held still name the addresses where nothing listens",
                () -> assertThat(state.values()).isEqualTo(stored));
    }

    /**
     * A blank field stands for what the sources name, as a blank launch value did (ADR-390 §4): here the environment
     * names the two containers that answer, and the stored launch values name addresses where nothing listens, which a
     * blank field never stands for.
     */
    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("A Services field left blank is checked at the address the environment names, and not at the stored one")
    void aBlankFieldIsCheckedAtWhatTheSourcesNameAndNotAtTheStoredAddress() throws Exception {
        StartingValues.Sources environment = new StartingValues.Sources(Map.of(), Map.of(
                "VESPERA_DOCLING_BASE_URL", docling.baseUrl().toString(),
                "SPRING_AI_OLLAMA_BASE_URL", ollama.baseUrl().toString()), Map.of(), Map.of(
                "spring.ai.ollama.embedding.options.model", TheWindowChecksAndStartsNothingAsItOpensTest.EMBEDDING_MODEL,
                "spring.ai.ollama.chat.options.model", TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL));
        ControlPanelState state = aState(inProcess(environment));
        state.useSources(environment);
        List<String> before;
        List<String> blanked;
        boolean runOn;
        try (WindowForTests window = WindowForTests.shown(state)) {
            before = window.awaiting(TheWindowAsksWhatItsServicesFieldsHoldTest::serviceMarks,
                    marks -> marks.equals(List.of("KO", "KO")));
            window.act(root -> {
                field(root, "vespera.docling.base-url").setText("");
                field(root, "spring.ai.ollama.base-url").setText("");
            });
            blanked = window.awaiting(TheWindowAsksWhatItsServicesFieldsHoldTest::serviceMarks,
                    marks -> marks.equals(List.of("OK", "OK")));
            runOn = window.awaiting(root -> !WindowForTests.node(root, "run", Button.class).isDisabled(),
                    Boolean::booleanValue);
        }

        claim("the fields starting from the stored addresses, where nothing listens, both read KO",
                () -> assertThat(before).containsExactly("KO", "KO"));
        claim("both fields blanked, the check asked the addresses the environment names, and both read OK",
                () -> assertThat(blanked).containsExactly("OK", "OK"));
        claim("and Run is enabled", () -> assertThat(runOn).isTrue());
    }

    /**
     * Start Docker is given what the fields hold as it is pressed, a blank field passed blank for the control to read
     * from the sources; the stop of the containers as the window closes keeps the launch values held (ADR-390 §5), which
     * no start of Start Docker's changes.
     */
    @Test
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("Start Docker is given the addresses and the image the Services fields hold, a blank one blank, and closing stops the containers for the launch values held")
    void startDockerIsGivenWhatTheFieldsHoldAndClosingStopsForTheLaunchValuesHeld() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(
                RecordingDockerControl.everyRow(KO, OK, OK, OK, OK));
        ControlPanelState state = aState(docker);
        docker.watching(state);
        state.hold(new LaunchValues(state.values().corpusRoot(), state.values().workingDirectory(), "",
                StartingValues.DOCLING_IMAGE_CPU, "http://localhost:5001", "http://localhost:11434", "", ""));
        LaunchValues held = state.values();
        List<String> shownAsItOpened;
        boolean closed;
        try (WindowForTests window = WindowForTests.shown(state)) {
            shownAsItOpened = window.read(TheWindowAsksWhatItsServicesFieldsHoldTest::servicesFields);
            window.act(root -> {
                field(root, "vespera.docling.base-url").setText(DOCLING_TYPED);
                field(root, "spring.ai.ollama.base-url").setText(OLLAMA_TYPED);
                WindowForTests.node(root, "image-gpu", RadioButton.class).fire();
            });
            pressStartDocker(window);
            WindowForTests.within(() -> docker.startsReturned.size() == 1);

            window.act(root -> field(root, "spring.ai.ollama.base-url").setText(""));
            pressStartDocker(window);
            WindowForTests.within(() -> docker.startsReturned.size() == 2);

            closed = !window.closedAsTheOperatorDoesAndAwaited();
        }

        claim("as the window opened, the fields showed the launch values held",
                () -> assertThat(shownAsItOpened).containsExactly("http://localhost:5001", "http://localhost:11434",
                        StartingValues.DOCLING_IMAGE_CPU));
        claim("pressed, Start Docker was given the addresses typed and the GPU image chosen, as the fields held them",
                () -> assertThat(docker.of("start")).first().extracting(call -> services(call.values().orElseThrow()))
                        .isEqualTo(List.of(DOCLING_TYPED, OLLAMA_TYPED, StartingValues.DOCLING_IMAGE_GPU)));
        claim("pressed again with Ollama's field blank, it was given Ollama's address blank, for the control to read"
                        + " from the sources, and not the address held",
                () -> assertThat(docker.of("start")).hasSize(2).last()
                        .extracting(call -> services(call.values().orElseThrow()))
                        .isEqualTo(List.of(DOCLING_TYPED, "", StartingValues.DOCLING_IMAGE_GPU)));
        claim("the window closed, and the containers were stopped once, for the launch values held, which neither start"
                        + " changed",
                () -> assertThat(List.of(closed, docker.of("stop-containers"), state.values())).containsExactly(true,
                        List.of(new RecordingDockerControl.Call("stop-containers", Optional.of(held), false)), held));
    }

    /** The window's download goes to the Ollama its field names as the download begins, not the one held (§7). */
    @Test
    @Story("The download panel downloads the missing models")
    @DisplayName("The window's download of a model goes to the Ollama whose address its field holds")
    void theWindowsDownloadGoesToTheOllamaItsFieldHolds() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(
                RecordingDockerControl.everyRow(OK, OK, OK, OK, KO));
        ControlPanelState state = aState(docker);
        state.hold(new LaunchValues(state.values().corpusRoot(), state.values().workingDirectory(), "", "",
                "http://localhost:5001", "http://localhost:11434", "", ""));
        String askedOf;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> field(root, "spring.ai.ollama.base-url").setText(OLLAMA_TYPED));
            window.awaiting(root -> "KO".equals(WindowForTests.textUnder(root, "check-generative-model-mark")),
                    Boolean::booleanValue);
            window.act(root -> WindowForTests.click(WindowForTests.node(root, "check-generative-model-mark",
                    Node.class)));
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            askedOf = docker.downloads.isEmpty() ? null : docker.downloads.getFirst().values.ollamaBaseUrl();
        }

        claim("the generative model was downloaded into the Ollama at the address its field holds",
                () -> assertThat(askedOf).isEqualTo(OLLAMA_TYPED));
    }

    /** What the three Services fields read, stripped, in {@link #SERVICES_FIELDS}' order. */
    static List<String> servicesFields(Parent root) {
        return SERVICES_FIELDS.stream().map(id -> field(root, id).getText().strip()).toList();
    }

    /** Docling's address, Ollama's address and the converter's image {@code values} name, blank where none. */
    static List<String> services(LaunchValues values) {
        return List.of(Objects.toString(values.doclingBaseUrl(), ""), Objects.toString(values.ollamaBaseUrl(), ""),
                Objects.toString(values.doclingImage(), ""));
    }

    private static TextInputControl field(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class);
    }

    /** What Docling's and Ollama's rows read. */
    private static List<String> serviceMarks(Parent root) {
        return List.of(WindowForTests.node(root, "check-docling-mark", Labeled.class).getText(),
                WindowForTests.node(root, "check-ollama-mark", Labeled.class).getText());
    }

    private static void pressStartDocker(WindowForTests window) throws Exception {
        window.awaiting(root -> !WindowForTests.node(root, "start-docker", Button.class).isDisabled(),
                Boolean::booleanValue);
        window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
    }

    /** The window's control, as {@code ControlPanel} builds it over {@code sources}, the daemon answered as running. */
    private DockerControl inProcess(StartingValues.Sources sources) {
        return DockerControl.inProcess(
                new DockerControl.Settings("", Duration.ofSeconds(1), folder, null, null, sources),
                (doclingAsked, ollamaAsked) -> {
                    daemonAsked.add(List.of(doclingAsked, ollamaAsked));
                    return Optional.of(true);
                });
    }

    /**
     * A state over a corpus root and a working directory under the test's folder, the sources naming the test's models,
     * the launch values held naming Docling and Ollama where nothing listens, as a start that stored them would.
     */
    private ControlPanelState aState(DockerControl docker) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", doclingNowhere.toString(),
                ollamaNowhere.toString(), "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return state;
    }
}
