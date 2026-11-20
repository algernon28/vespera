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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Run waits for the containers (ADR-430 §6): it is disabled until the Docker daemon, where it is asked, Docling and
 * Ollama show OK, and its caption says what it waits for (§8), the first check, a start and a shut-down among them; a
 * model that is KO holds it only where Command options'
 * {@code vespera.models.pull} is not ticked, since a run downloads the model itself where it is; and Auto Label is held
 * by the same rule for Ollama and the generative model alone.
 *
 * <p>The window is given a {@link RecordingDockerControl} whose check the test sets; the window is shown over a corpus
 * root and a working directory, so that nothing but the check holds Run.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class RunAndAutoLabelWaitForTheContainersTest {

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    private static final DockerCheck.Mark NOT_ASKED = DockerCheck.Mark.NOT_ASKED;

    /** What Run and Auto Label read: whether each is enabled, and Run's caption. */
    private record Held(boolean runEnabled, boolean autoLabelEnabled, String runCaption) {
    }

    @TempDir
    Path folder;

    @Test
    @Story("Run waits for the containers")
    @DisplayName("Run is off until the Docker daemon, Docling and Ollama show OK, its caption saying what it waits for, and on where the daemon is not asked")
    void runIsOffUntilTheDaemonDoclingAndOllamaShowOkAndItsCaptionSaysWhy() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(OK, KO, OK, OK, OK));
        Held doclingDown;
        Held twoDown;
        Held allUp;
        Held elsewhere;
        try (WindowForTests window = shown(docker)) {
            doclingDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Docling"));
            docker.checkAnswering(RecordingDockerControl.everyRow(KO, KO, OK, OK, OK));
            twoDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Docker daemon"));
            docker.checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, OK, OK));
            allUp = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held, Held::runEnabled);
            docker.checkAnswering(RecordingDockerControl.bothElsewhere("http://gpu-box.lan:5001", OK,
                    "http://gpu-box.lan:11434", OK, OK, OK));
            elsewhere = window.awaiting(root -> WindowForTests.node(root, "check-docker-daemon-mark", Labeled.class)
                    .getText().equals(TheWindowChecksAndStartsNothingAsItOpensTest.DASH) ? held(root) : null,
                    read -> read != null);
        }

        claim("with Docling KO, Run is off and its caption reads: Off until Docling shows OK.",
                () -> assertThat(doclingDown).isEqualTo(new Held(false, true, "Off until Docling shows OK.")));
        claim("with the daemon and Docling KO, its caption names both, the last joined by and",
                () -> assertThat(twoDown.runCaption()).isEqualTo("Off until Docker daemon and Docling show OK."));
        claim("with all three OK, Run is on and its caption says what it starts",
                () -> assertThat(allUp).isEqualTo(new Held(true, true, "Starts vespera run.")));
        claim("and with both containers on another machine, the daemon not asked and both OK, Run is on",
                () -> assertThat(elsewhere).isNotNull().extracting(Held::runEnabled).isEqualTo(true));
    }

    @Test
    @Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
    @Issue("891")
    @Story("Run waits for the containers")
    @DisplayName("Run is off until the first check has come back, while Docker and the containers start and while Docker shuts down, its caption saying which, before the rows that are not OK")
    void runsCaptionSaysItWaitsForTheFirstCheckAndForAStartOrAShutDown() throws Exception {
        CountDownLatch firstCheck = new CountDownLatch(1);
        CountDownLatch startReleased = new CountDownLatch(1);
        CountDownLatch shutDownReleased = new CountDownLatch(1);
        // the daemon KO throughout, so that Start Docker can be pressed and a row not OK would hold Run anyway
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(KO, OK, OK, OK, OK))
                .holdingChecksUntil(firstCheck).holdingStartsUntil(startReleased)
                .holdingShutDownsUntil(shutDownReleased);
        Held beforeTheFirstCheck;
        Held daemonDown;
        Held starting;
        Held shuttingDown;
        try (WindowForTests window = shown(docker)) {
            try {
                WindowForTests.within(() -> !docker.checks.isEmpty());
                beforeTheFirstCheck = window.read(RunAndAutoLabelWaitForTheContainersTest::held);
            } finally {
                firstCheck.countDown();
            }
            daemonDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Docker daemon"));
            window.awaiting(root -> !WindowForTests.node(root, "start-docker", Button.class).isDisabled(),
                    Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.of("start").isEmpty());
            starting = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("start."));
            startReleased.countDown();
            window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Docker daemon"));
            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
            WindowForTests.within(() -> !docker.of("shut-down").isEmpty());
            shuttingDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("shuts down"));
            shutDownReleased.countDown();
        }

        claim("before the first check has come back, Run is off and its caption reads: Off until Docling and Ollama"
                        + " have been checked.",
                () -> assertThat(List.of(beforeTheFirstCheck.runEnabled(), beforeTheFirstCheck.runCaption()))
                        .containsExactly(false, "Off until Docling and Ollama have been checked."));
        claim("once it has, the daemon KO, its caption names the daemon",
                () -> assertThat(daemonDown.runCaption()).isEqualTo("Off until Docker daemon shows OK."));
        claim("while Start Docker's start runs, Run is off and its caption reads: Off while Docker and the containers"
                        + " start., ahead of the daemon's row",
                () -> assertThat(List.of(starting.runEnabled(), starting.runCaption()))
                        .containsExactly(false, "Off while Docker and the containers start."));
        claim("and while a shut-down runs: Off while Docker shuts down.",
                () -> assertThat(List.of(shuttingDown.runEnabled(), shuttingDown.runCaption()))
                        .containsExactly(false, "Off while Docker shuts down."));
    }

    @Test
    @Story("Run waits for the containers")
    @DisplayName("A model Ollama has not holds Run only where vespera.models.pull is unticked, since a run downloads it where it is ticked")
    void aMissingModelHoldsRunOnlyWhereModelsPullIsOff() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, KO, OK));
        Held pullOn;
        Held pullOff;
        try (WindowForTests window = shown(docker)) {
            pullOn = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held, Held::runEnabled);
            window.act(root -> WindowForTests.node(root, "vespera.models.pull", CheckBox.class).setSelected(false));
            pullOff = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held, read -> !read.runEnabled());
        }

        claim("the embedding model KO and vespera.models.pull ticked, Run is on",
                () -> assertThat(pullOn.runEnabled()).isTrue());
        claim("unticked, Run is off, its caption saying a run would not download the models",
                () -> assertThat(pullOff).isEqualTo(new Held(false, true, "Off until the models are downloaded:"
                        + " vespera.models.pull is off, so a run would not download them.")));
    }

    @Test
    @Story("Run waits for the containers")
    @DisplayName("Auto Label is held by Ollama and the generative model alone: Docling KO holds Run and not Auto Label, and Ollama KO holds both")
    void autoLabelIsHeldByOllamaAndTheGenerativeModelAlone() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(OK, KO, OK, OK, OK));
        Held doclingDown;
        Held ollamaDown;
        Held embeddingMissing;
        Held generativeMissing;
        try (WindowForTests window = shown(docker)) {
            window.act(root -> WindowForTests.node(root, "vespera.models.pull", CheckBox.class).setSelected(false));
            doclingDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Docling"));
            docker.checkAnswering(RecordingDockerControl.everyRow(OK, OK, KO, NOT_ASKED, NOT_ASKED));
            ollamaDown = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("Ollama"));
            docker.checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, KO, OK));
            embeddingMissing = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> read.runCaption().contains("downloaded"));
            docker.checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, OK, KO));
            generativeMissing = window.awaiting(RunAndAutoLabelWaitForTheContainersTest::held,
                    read -> !read.autoLabelEnabled());
        }

        claim("Docling KO holds Run and leaves Auto Label on",
                () -> assertThat(List.of(doclingDown.runEnabled(), doclingDown.autoLabelEnabled()))
                        .containsExactly(false, true));
        claim("Ollama KO holds both", () -> assertThat(List.of(ollamaDown.runEnabled(), ollamaDown.autoLabelEnabled()))
                .containsExactly(false, false));
        claim("with vespera.models.pull unticked, the embedding model KO holds Run and leaves Auto Label on",
                () -> assertThat(List.of(embeddingMissing.runEnabled(), embeddingMissing.autoLabelEnabled()))
                        .containsExactly(false, true));
        claim("and the generative model KO holds Auto Label",
                () -> assertThat(generativeMissing.autoLabelEnabled()).isFalse());
    }

    /** The window over a corpus root and a working directory, Docker being {@code docker}. */
    private WindowForTests shown(RecordingDockerControl docker) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return WindowForTests.shown(state);
    }

    private static Held held(Parent root) {
        return new Held(!WindowForTests.node(root, "run", Button.class).isDisabled(),
                !WindowForTests.node(root, "label-auto", Button.class).isDisabled(),
                WindowForTests.caption(root, "run-captioned"));
    }
}
