package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <i>Start Docker</i> (ADR-430 §5): it stands in the action row immediately before <i>Shut down Docker</i>, starts what
 * {@code vespera run}'s start starts, once for the addresses and the image the Services fields hold, and the window
 * checks at once after; it is
 * disabled until the first check has come back, while everything on this machine answers, while a start, a download or
 * a shut-down runs, while the window's own child runs, and where both containers are on another machine; and after a
 * start, a model the check finds missing is downloaded where
 * Command options' {@code vespera.models.pull} is ticked, and not where it is not.
 *
 * <p>The window is given a {@link RecordingDockerControl}, so nothing is run: what the start runs is {@code
 * EachContainerIsStartedOnlyWhereItIsOnThisMachineTest}'s and {@code SidecarStartTest}'s.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheStartDockerButtonTest {

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    private static final DockerCheck.Mark NOT_ASKED = DockerCheck.Mark.NOT_ASKED;

    /** The action row's wrappers, in the order it holds them (ADR-430 §5, §8). */
    private static final List<String> THE_ROW = List.of("run-captioned", "stop-captioned", "start-docker-captioned",
            "shut-down-docker-captioned");

    /** Within this of a start's return, a check is the one done at once, not the clock's, which comes every 5 s. */
    private static final Duration AT_ONCE = Duration.ofMillis(2500);

    @TempDir
    Path folder;

    @Test
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("Start Docker stands immediately before Shut down Docker, starts once for what the Services fields hold, and the window checks at once after")
    void itStandsImmediatelyBeforeShutDownDockerAndStartsOnceForWhatTheServicesFieldsHold() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED));
        ControlPanelState state = aState(docker);
        String text;
        List<String> row;
        List<String> fields;
        Duration checkedAfter;
        try (WindowForTests window = WindowForTests.shown(state)) {
            text = window.read(root -> WindowForTests.node(root, "start-docker", Button.class).getText());
            fields = window.read(TheWindowAsksWhatItsServicesFieldsHoldTest::servicesFields);
            row = window.read(root -> WindowForTests.node(root, "actions", Parent.class).getChildrenUnmodifiable()
                    .stream().map(Node::getId).filter(THE_ROW::contains).toList());
            window.awaiting(root -> !WindowForTests.node(root, "start-docker", Button.class).isDisabled(),
                    Boolean::booleanValue);
            TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, java.time.Instant.now());
            window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.startsReturned.isEmpty());
            checkedAfter = TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker,
                    docker.startsReturned.getFirst());
        }

        claim("the button reads Start Docker", () -> assertThat(text).isEqualTo("Start Docker"));
        claim("and the action row holds Run, Stop, Start Docker and Shut down Docker in that order",
                () -> assertThat(row).containsExactlyElementsOf(THE_ROW));
        claim("pressed, Docker was asked to start once, with no invocation running, and nothing else",
                () -> assertThat(docker.calls).singleElement()
                        .extracting(RecordingDockerControl.Call::kind, RecordingDockerControl.Call::childRan)
                        .containsExactly("start", false));
        claim("for Docling's and Ollama's addresses and the converter's image as the Services fields hold them",
                () -> assertThat(TheWindowAsksWhatItsServicesFieldsHoldTest.services(
                        docker.calls.getFirst().values().orElseThrow())).isEqualTo(fields));
        claim("and the window checked within " + AT_ONCE.toMillis() + " ms of the start's return",
                () -> assertThat(checkedAfter).isLessThan(AT_ONCE));
    }

    @Test
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("Start Docker is off until the first check, while everything on this machine answers, while it runs, while a download runs, while a shut-down runs, while an invocation runs, and where both containers are on another machine")
    void itIsOffWhileEverythingHereAnswersWhileItRunsWhileAnInvocationRunsAndForContainersOnAnotherMachine()
            throws Exception {
        CountDownLatch firstCheck = new CountDownLatch(1);
        CountDownLatch startReleased = new CountDownLatch(1);
        CountDownLatch shutDownReleased = new CountDownLatch(1);
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(KO, OK, OK, OK, OK))
                .holdingChecksUntil(firstCheck).holdingStartsUntil(startReleased)
                .holdingShutDownsUntil(shutDownReleased);
        ControlPanelState state = aState(docker);
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        String daemonBeforeTheFirstCheck;
        try (WindowForTests window = WindowForTests.shown(state)) {
            try {
                WindowForTests.within(() -> !docker.checks.isEmpty());
                daemonBeforeTheFirstCheck = window.read(root -> WindowForTests.node(root, "check-docker-daemon-mark",
                        javafx.scene.control.Labeled.class).getText());
                enabled.put("before the first check has come back, the daemon KO",
                        window.read(TheStartDockerButtonTest::startEnabled));
            } finally {
                firstCheck.countDown();
            }
            enabled.put("the daemon KO", onceTheDaemonReads(window, "KO"));
            docker.checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, OK, OK));
            enabled.put("everything answering", onceTheDaemonReads(window, "OK"));
            docker.checkAnswering(RecordingDockerControl.bothElsewhere("http://gpu-box.lan:5001", KO,
                    "http://gpu-box.lan:11434", KO, NOT_ASKED, NOT_ASKED));
            enabled.put("both containers on another machine, both KO",
                    onceTheDaemonReads(window, TheWindowChecksAndStartsNothingAsItOpensTest.DASH));
            docker.checkAnswering(RecordingDockerControl.everyRow(KO, OK, OK, OK, OK));
            window.awaiting(TheStartDockerButtonTest::startEnabled, on -> on);
            window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
            enabled.put("while its start runs", window.awaiting(TheStartDockerButtonTest::startEnabled, on -> !on));
            startReleased.countDown();
            window.awaiting(TheStartDockerButtonTest::startEnabled, on -> on);

            // the daemon KO and the generative model missing: its KO clicked, the download runs until it is cancelled
            docker.checkAnswering(RecordingDockerControl.everyRow(KO, OK, OK, OK, KO));
            window.awaiting(root -> "KO".equals(WindowForTests.textUnder(root, "check-generative-model-mark")),
                    Boolean::booleanValue);
            window.awaiting(TheStartDockerButtonTest::startEnabled, on -> on);
            window.act(root -> WindowForTests.click(WindowForTests.node(root, "check-generative-model-mark",
                    Node.class)));
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            enabled.put("while a download runs", window.awaiting(TheStartDockerButtonTest::startEnabled, on -> !on));
            window.act(root -> WindowForTests.node(root, "download-cancel", Button.class).fire());
            docker.checkAnswering(RecordingDockerControl.everyRow(KO, OK, OK, OK, OK));
            window.awaiting(TheStartDockerButtonTest::startEnabled, on -> on);

            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.of("shut-down").isEmpty());
            enabled.put("while a shut-down runs", window.awaiting(TheStartDockerButtonTest::startEnabled, on -> !on));
            shutDownReleased.countDown();
            window.awaiting(TheStartDockerButtonTest::startEnabled, on -> on);

            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            enabled.put("while an invocation runs", window.awaiting(TheStartDockerButtonTest::startEnabled, on -> !on));
        }

        claim("before the first check has come back, the daemon's row reads a dash",
                () -> assertThat(daemonBeforeTheFirstCheck).isEqualTo(TheWindowChecksAndStartsNothingAsItOpensTest.DASH));
        claim("Start Docker is off until the first check has come back though it answers the daemon KO, on with the"
                        + " daemon KO, off while the daemon, Docling and Ollama all answer, off with both containers on"
                        + " another machine, off while its start runs, off while a download runs, off while a shut-down"
                        + " runs, and off while an invocation runs",
                () -> assertThat(enabled).containsExactly(
                        Map.entry("before the first check has come back, the daemon KO", false),
                        Map.entry("the daemon KO", true),
                        Map.entry("everything answering", false),
                        Map.entry("both containers on another machine, both KO", false),
                        Map.entry("while its start runs", false),
                        Map.entry("while a download runs", false),
                        Map.entry("while a shut-down runs", false),
                        Map.entry("while an invocation runs", false)));
    }

    @Test
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("After Start Docker, a model the check finds missing is downloaded where vespera.models.pull is ticked, and not where it is unticked")
    void afterAStartAMissingModelIsDownloadedWhereModelsPullIsOn() throws Exception {
        DockerCheck afterTheStart = RecordingDockerControl.everyRow(OK, OK, OK, OK, KO);
        RecordingDockerControl pulling = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED))
                .afterAStartCheckAnswering(afterTheStart);
        List<String> downloaded;
        boolean panelShown;
        try (WindowForTests window = WindowForTests.shown(aState(pulling))) {
            pressStartDocker(window);
            WindowForTests.within(() -> !pulling.downloads.isEmpty());
            downloaded = pulling.downloads.stream().map(each -> each.model).toList();
            panelShown = window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
        }

        RecordingDockerControl notPulling = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED))
                .afterAStartCheckAnswering(afterTheStart);
        boolean panelShownWithoutPull;
        try (WindowForTests window = WindowForTests.shown(aState(notPulling, "unticked"))) {
            window.act(root -> WindowForTests.node(root, "vespera.models.pull", CheckBox.class).setSelected(false));
            pressStartDocker(window);
            WindowForTests.within(() -> !notPulling.startsReturned.isEmpty());
            TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(notPulling, notPulling.startsReturned.getFirst());
            Thread.sleep(500);
            panelShownWithoutPull = window.read(root -> WindowForTests.showing(root, "download-panel"));
        }

        claim("vespera.models.pull ticked, the generative model the check found missing after the start was downloaded,"
                        + " and it alone",
                () -> assertThat(downloaded).containsExactly(TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL));
        claim("in the download panel, shown", () -> assertThat(panelShown).isTrue());
        claim("unticked, nothing was downloaded and no panel shown",
                () -> assertThat(List.of(notPulling.downloads.size(), panelShownWithoutPull))
                        .containsExactly(0, false));
    }

    private ControlPanelState aState(RecordingDockerControl docker) throws Exception {
        return aState(docker, "ticked");
    }

    /** A state over a corpus root and a working directory under {@code folder}/{@code under}, the sources naming the test's models. */
    private ControlPanelState aState(RecordingDockerControl docker, String under) throws Exception {
        Path at = Files.createDirectories(folder.resolve(under));
        ControlPanelState state = WindowForTests.aState(at, SleepingJar.runtime(), docker);
        docker.watching(state);
        state.hold(new LaunchValues(Files.createDirectories(at.resolve("corpus")).toString(),
                Files.createDirectories(at.resolve("working")).toString(), "", "", "", "", "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return state;
    }

    private static void pressStartDocker(WindowForTests window) throws Exception {
        window.awaiting(TheStartDockerButtonTest::startEnabled, Boolean::booleanValue);
        window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
    }

    /** Whether Start Docker is enabled, read once the daemon's row reads {@code mark}, so the check it follows is drawn. */
    private static boolean onceTheDaemonReads(WindowForTests window, String mark) throws Exception {
        Boolean read = window.awaiting(root -> mark.equals(WindowForTests.node(root, "check-docker-daemon-mark",
                javafx.scene.control.Labeled.class).getText()) ? startEnabled(root) : null, each -> each != null);
        if (read == null) {
            throw new AssertionError("the daemon's row never read " + mark);
        }
        return read;
    }

    private static boolean startEnabled(Parent root) {
        return !WindowForTests.node(root, "start-docker", Button.class).isDisabled();
    }
}
