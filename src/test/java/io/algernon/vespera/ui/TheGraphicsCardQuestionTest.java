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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where <i>Start Docker</i>'s start is refused because the graphics card cannot be used, the window asks the operator
 * (ADR-445 §5): to use the CPU image, which sets the converter's image field and starts again for it (§7), to install the
 * NVIDIA Container Toolkit where an NVIDIA card is there, listing every command it runs as root, or to cancel, which
 * shows the refusal's line. On Linux the install first asks for the password {@code sudo} wants, in a secret field, and
 * hands it to the install alone; nothing the window writes or shows holds it (§8). The output panel holds the press's
 * lines, the install's among them, and its line last (§5, ADR-437 §4). The Preflight section says in a row whether
 * Docker has the nvidia runtime, asked once while the daemon answers (§5, ADR-440), and the Services section's choices
 * say the GPU image is for NVIDIA's cards (§9).
 *
 * <p>The window is given a {@link RecordingDockerControl}, so nothing is run, installed or asked of Docker: what the start
 * and the install run is {@code TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest}'s and {@code
 * TheNvidiaToolkitIsInstalledAsRootTest}'s. Its dialogs are {@link AnsweringDialogs}. Every sentence is read whole.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("860")
@Link(name = "ADR-445", url = Adr.THE_GPU_IMAGE_STARTS_ONLY_WHERE_DOCKER_HAS_THE_NVIDIA_RUNTIME, type = "adr")
class TheGraphicsCardQuestionTest {

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    private static final DockerCheck.Mark NOT_ASKED = DockerCheck.Mark.NOT_ASKED;

    private static final String TITLE = "The graphics card";

    private static final String USE_THE_CPU_IMAGE = "Use the CPU image";

    private static final String INSTALL_THE_TOOLKIT = "Install the toolkit";

    private static final String CANCEL = "Cancel";

    /** The first sentence of the line of the nvidia runtime missing, on Windows and on Linux (ADR-445 §2). */
    private static final String WHY_ON_WINDOWS = "The graphics card cannot be used: the Docker engine in WSL's default"
            + " distribution answers but has no nvidia runtime, which the NVIDIA Container Toolkit gives it.";

    private static final String WHY_ON_LINUX = "The graphics card cannot be used: the Docker engine on this machine answers"
            + " but has no nvidia runtime, which the NVIDIA Container Toolkit gives it.";

    /** The rest of that line, which the window shows where the question is cancelled. */
    private static final String HOW_TO_GO_ON = " Install the toolkit, which Start Docker in the control panel offers to do,"
            + " or as NVIDIA's guide at https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html"
            + " has it; or set VESPERA_DOCLING_IMAGE to " + StartingValues.DOCLING_IMAGE_CPU + ", which converts on the"
            + " processor and starts new runs from extraction on; and run the same command again.";

    private static final String REFUSED_ON_WINDOWS = WHY_ON_WINDOWS + HOW_TO_GO_ON;

    private static final String REFUSED_ON_LINUX = WHY_ON_LINUX + HOW_TO_GO_ON;

    /** The line of an AMD card, and its first sentence (ADR-445 §3). */
    private static final String WHY_AN_AMD_CARD = "The graphics card cannot be used: this machine's graphics card is AMD's,"
            + " and Vespera runs the containers on NVIDIA cards alone.";

    private static final String REFUSED_FOR_AN_AMD_CARD = WHY_AN_AMD_CARD + " Set VESPERA_DOCLING_IMAGE to "
            + StartingValues.DOCLING_IMAGE_CPU + ", which converts on the processor and starts new runs from extraction on,"
            + " and run the same command again.";

    /** The install's commands as the confirmation lists them (ADR-445 §6). */
    private static final List<String> THE_COMMANDS = List.of(
            "apt-get update",
            "apt-get install -y --no-install-recommends ca-certificates curl gnupg2",
            "bash -c \"set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | gpg --batch"
                    + " --yes --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg\"",
            "bash -c \"set -euo pipefail; curl -fsSL"
                    + " https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | sed"
                    + " 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg]"
                    + " https://#g' > /etc/apt/sources.list.d/nvidia-container-toolkit.list\"",
            "apt-get update",
            "apt-get install -y nvidia-container-toolkit=1.20.2-1 nvidia-container-toolkit-base=1.20.2-1"
                    + " libnvidia-container-tools=1.20.2-1 libnvidia-container1=1.20.2-1",
            "nvidia-ctk runtime configure --runtime=docker",
            "systemctl restart docker");

    private static final String WHAT_THEY_DO = "They add NVIDIA's package source and its signing key, install the NVIDIA"
            + " Container Toolkit, write its nvidia runtime into /etc/docker/daemon.json and restart Docker. Restarting"
            + " Docker stops every container running on it, Vespera's and any other, and only those set to restart come"
            + " back.";

    private static final String THE_CPU_IMAGE = "Use the CPU image runs the converter on the processor instead, and"
            + " remembers that choice. The CPU image converts documents differently, so it is another converter: every"
            + " stage from extraction on starts new runs, and a document it has not converted before is converted again.";

    private static final DockerControl.CardRefused THE_TOOLKIT_ON_WINDOWS = new DockerControl.CardRefused(
            true, WHY_ON_WINDOWS, THE_COMMANDS, "in WSL's default distribution", false);

    private static final DockerControl.CardRefused THE_TOOLKIT_ON_LINUX = new DockerControl.CardRefused(
            true, WHY_ON_LINUX, THE_COMMANDS, "on this machine", true);

    private static final DockerControl.CardRefused AN_AMD_CARD = new DockerControl.CardRefused(
            false, WHY_AN_AMD_CARD, List.of(), "", false);

    private static final String INSTALLED =
            "The NVIDIA Container Toolkit 1.20.2-1 is installed, and Docker was restarted with its nvidia runtime.";

    /** A password with a space and a backslash, which nothing the window writes or shows may hold. */
    private static final String PASSWORD = "c0rrect horse\\battery";

    /** The lines a start tells, as a start that brings the containers up tells them (ADR-437 §1). */
    private static final List<String> A_STARTS_LINES = List.of(
            "Starting the containers: docker compose -p vespera -f compose.yaml -f compose.gpu.yaml up -d --build"
                    + " --no-recreate",
            "Container vespera-docling-1  Started");

    /** The lines an install tells: a command before it runs and what it printed (ADR-445 §6). */
    private static final List<String> THE_INSTALLS_LINES = List.of(
            "Installing the NVIDIA Container Toolkit: apt-get update",
            "Hit:1 http://archive.ubuntu.com/ubuntu noble InRelease",
            "Installing the NVIDIA Container Toolkit: systemctl restart docker");

    /** The Preflight row's words where Docker has no nvidia runtime, and where it has one (ADR-445 §5). */
    private static final String NOT_LISTED =
            "Docker has no nvidia runtime; Start Docker offers to install the NVIDIA Container Toolkit";

    private static final String LISTED = "Docker has the nvidia runtime";

    /** A Docling address the operator types while a start runs: another port of this machine. */
    private static final String AN_EDITED_DOCLING = "http://localhost:5002";

    private static final String NOT_LISTED_CAPTION = "The GPU image starts only where Docker has the nvidia runtime,"
            + " which the NVIDIA Container Toolkit gives it.";

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    /** Every row of the header's check OK: the Docker daemon answers here. */
    private static final DockerCheck EVERYTHING_ANSWERS = RecordingDockerControl.everyRow(OK, OK, OK, OK, OK);

    /** The daemon does not answer, the containers neither. */
    private static final DockerCheck NOTHING_ANSWERS = RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED);

    private static final String A_WARNING = "NVIDIA GeForce GTX 1080 has compute capability 6.1, and the GPU image's"
            + " converter needs 7.5 or later, a Turing card (GeForce RTX 20 or GTX 16) or newer, so it may not convert on"
            + " the card: choose the CPU image if it does not.";

    @TempDir
    Path folder;

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("A start refused for the missing nvidia runtime asks under The graphics card, listing the install's commands and what the CPU image costs; closed with no answer, the refusal's line is shown and nothing more is run")
    void aStartRefusedForTheMissingRuntimeAsksWithTheCommandsAndBothWaysOn() throws Exception {
        RecordingDockerControl docker = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_WINDOWS))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_WINDOWS)
                        ? Optional.of(THE_TOOLKIT_ON_WINDOWS) : Optional.empty());
        String shown;
        AnsweringDialogs dialogs;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "windows"))) {
            dialogs = window.dialogs;
            dialogs.chosen = Optional.empty();
            chooseTheGpuImage(window);
            pressStartDocker(window);
            shown = window.awaiting(TheGraphicsCardQuestionTest::outcome, REFUSED_ON_WINDOWS::equals);
        }

        claim("the window asked once, under The graphics card, this sentence, whole, with Use the CPU image, Install the"
                        + " toolkit and Cancel",
                () -> assertThat(dialogs.choices).singleElement().isEqualTo(new AnsweringDialogs.Chose(TITLE,
                        WHY_ON_WINDOWS + "\n\nInstall the toolkit runs these commands as root in WSL's default"
                                + " distribution:\n\n" + String.join("\n", THE_COMMANDS) + "\n\n" + WHAT_THEY_DO
                                + "\n\n" + THE_CPU_IMAGE,
                        List.of(USE_THE_CPU_IMAGE, INSTALL_THE_TOOLKIT, CANCEL))));
        claim("closed with no answer, the refusal's line is shown where the window says what happened",
                () -> assertThat(shown).isEqualTo(REFUSED_ON_WINDOWS));
        claim("and Docker was asked to start once, and nothing was installed",
                () -> assertThat(docker.calls).extracting(RecordingDockerControl.Call::kind).containsExactly("start"));
        claim("and no password was asked for", () -> assertThat(dialogs.secrets).isEmpty());
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("Use the CPU image sets the converter's image field to the CPU image and starts again for it, installing nothing")
    void useTheCpuImageSetsTheImageFieldAndStartsAgainForIt() throws Exception {
        RecordingDockerControl docker = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_WINDOWS), new DockerControl.Said(true, ""))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_WINDOWS)
                        ? Optional.of(THE_TOOLKIT_ON_WINDOWS) : Optional.empty());
        String image;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "cpu"))) {
            window.dialogs.chosen = Optional.of(USE_THE_CPU_IMAGE);
            chooseTheGpuImage(window);
            pressStartDocker(window);
            WindowForTests.within(() -> docker.startsReturned.size() == 2);
            image = window.awaiting(root -> field(root, "vespera.docling.image").getText(),
                    StartingValues.DOCLING_IMAGE_CPU::equals);
        }

        claim("the converter's image field reads the CPU image",
                () -> assertThat(image).isEqualTo(StartingValues.DOCLING_IMAGE_CPU));
        claim("Docker was asked to start twice, and nothing was installed",
                () -> assertThat(docker.calls).extracting(RecordingDockerControl.Call::kind)
                        .containsExactly("start", "start"));
        claim("the first start for the GPU image, the second for the CPU image",
                () -> assertThat(docker.of("start")).extracting(call -> call.values().orElseThrow().doclingImage())
                        .containsExactly(StartingValues.DOCLING_IMAGE_GPU, StartingValues.DOCLING_IMAGE_CPU));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("The start after Use the CPU image is for what the Services fields hold then, a Docling address edited while the first start ran among them")
    void theStartAfterUseTheCpuImageIsForWhatTheServicesFieldsHoldThen() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        RecordingDockerControl docker = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_WINDOWS), new DockerControl.Said(true, ""))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_WINDOWS)
                        ? Optional.of(THE_TOOLKIT_ON_WINDOWS) : Optional.empty())
                .holdingStartsUntil(released);
        try (WindowForTests window = WindowForTests.shown(aState(docker, "edited"))) {
            window.dialogs.chosen = Optional.of(USE_THE_CPU_IMAGE);
            chooseTheGpuImage(window);
            try {
                pressStartDocker(window);
                WindowForTests.within(() -> docker.of("start").size() == 1);
                window.act(root -> field(root, "vespera.docling.base-url").setText(AN_EDITED_DOCLING));
            } finally {
                released.countDown();
            }
            WindowForTests.within(() -> docker.startsReturned.size() == 2);
        }

        claim("Docker was asked to start twice",
                () -> assertThat(docker.of("start")).hasSize(2));
        claim("the first start was for the GPU image and the Docling address the field held at the press, not the one"
                        + " it was edited to",
                () -> assertThat(docker.of("start").getFirst().values().orElseThrow())
                        .extracting(LaunchValues::doclingImage, LaunchValues::doclingBaseUrl)
                        .doesNotContain(AN_EDITED_DOCLING).first().isEqualTo(StartingValues.DOCLING_IMAGE_GPU));
        claim("the second was for the CPU image and the Docling address the field was edited to while the first ran",
                () -> assertThat(docker.of("start").get(1).values().orElseThrow())
                        .extracting(LaunchValues::doclingImage, LaunchValues::doclingBaseUrl)
                        .containsExactly(StartingValues.DOCLING_IMAGE_CPU, AN_EDITED_DOCLING));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("On Windows, Install the toolkit installs with no password asked and starts again once installed; a start refused again for the card is not asked about twice")
    void installTheToolkitOnWindowsAsksNoPasswordAndStartsAgainOnceInstalled() throws Exception {
        RecordingDockerControl installed = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_WINDOWS), new DockerControl.Said(true, ""))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_WINDOWS)
                        ? Optional.of(THE_TOOLKIT_ON_WINDOWS) : Optional.empty())
                .installAnswering(new DockerControl.Said(true, INSTALLED))
                .tellingOnEachStart(A_STARTS_LINES)
                .tellingOnEachInstall(THE_INSTALLS_LINES);
        String shown;
        String output;
        AnsweringDialogs dialogs;
        try (WindowForTests window = WindowForTests.shown(aState(installed, "installed"))) {
            dialogs = window.dialogs;
            dialogs.chosen = Optional.of(INSTALL_THE_TOOLKIT);
            chooseTheGpuImage(window);
            pressStartDocker(window);
            WindowForTests.within(() -> installed.startsReturned.size() == 2);
            shown = window.awaiting(TheGraphicsCardQuestionTest::outcome, INSTALLED::equals);
            output = window.awaiting(TheGraphicsCardQuestionTest::output, text -> text.endsWith(INSTALLED + "\n"));
        }

        RecordingDockerControl refusedAgain = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_WINDOWS))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_WINDOWS)
                        ? Optional.of(THE_TOOLKIT_ON_WINDOWS) : Optional.empty())
                .installAnswering(new DockerControl.Said(true, INSTALLED));
        String shownAgain;
        AnsweringDialogs dialogsAgain;
        try (WindowForTests window = WindowForTests.shown(aState(refusedAgain, "again"))) {
            dialogsAgain = window.dialogs;
            dialogsAgain.chosen = Optional.of(INSTALL_THE_TOOLKIT);
            chooseTheGpuImage(window);
            pressStartDocker(window);
            WindowForTests.within(() -> refusedAgain.startsReturned.size() == 2);
            shownAgain = window.awaiting(TheGraphicsCardQuestionTest::outcome, REFUSED_ON_WINDOWS::equals);
        }

        claim("Install the toolkit: Docker was asked to start, then to install the toolkit, then to start again",
                () -> assertThat(installed.calls).extracting(RecordingDockerControl.Call::kind)
                        .containsExactly("start", "install", "start"));
        claim("the install was given no password, and none was asked for",
                () -> assertThat(List.of(installed.installsGiven, dialogs.secrets)).containsExactly(
                        java.util.Collections.singletonList(null), List.of()));
        claim("and the install's line is shown, the second start having said nothing",
                () -> assertThat(shown).isEqualTo(INSTALLED));
        claim("the output held exactly the first start's lines, the install's, the second start's and the install's line"
                        + " last, and not the refusal",
                () -> assertThat(output).isEqualTo(Stream.of(A_STARTS_LINES, THE_INSTALLS_LINES, A_STARTS_LINES,
                                List.of(INSTALLED)).flatMap(List::stream).map(line -> line + "\n")
                        .collect(java.util.stream.Collectors.joining())));
        claim("a start refused again for the card after the install shows its line",
                () -> assertThat(shownAgain).isEqualTo(REFUSED_ON_WINDOWS));
        claim("and the question was asked once, not a second time",
                () -> assertThat(dialogsAgain.choices).hasSize(1));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("On Linux, Install the toolkit first asks for the password sudo wants in a secret field, hands it to the install alone, and nothing the window writes or shows holds it")
    void onLinuxTheInstallAsksThePasswordInASecretFieldAndKeepsItNowhere() throws Exception {
        RecordingDockerControl docker = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_LINUX), new DockerControl.Said(true, ""))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_LINUX)
                        ? Optional.of(THE_TOOLKIT_ON_LINUX) : Optional.empty())
                .installAnswering(new DockerControl.Said(true, INSTALLED));
        AnsweringDialogs dialogs;
        List<String> everyText;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "linux"))) {
            dialogs = window.dialogs;
            dialogs.chosen = Optional.of(INSTALL_THE_TOOLKIT);
            dialogs.secretTyped = Optional.of(PASSWORD);
            chooseTheGpuImage(window);
            pressStartDocker(window);
            WindowForTests.within(() -> docker.startsReturned.size() == 2);
            window.awaiting(TheGraphicsCardQuestionTest::outcome, INSTALLED::equals);
            everyText = window.read(TheGraphicsCardQuestionTest::everyText);
        }
        List<Path> holding = filesHolding(folder, PASSWORD);

        RecordingDockerControl cancelled = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_ON_LINUX))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_ON_LINUX)
                        ? Optional.of(THE_TOOLKIT_ON_LINUX) : Optional.empty());
        String shownCancelled;
        try (WindowForTests window = WindowForTests.shown(aState(cancelled, "cancelled"))) {
            window.dialogs.chosen = Optional.of(INSTALL_THE_TOOLKIT);
            window.dialogs.secretTyped = Optional.empty();
            chooseTheGpuImage(window);
            pressStartDocker(window);
            shownCancelled = window.awaiting(TheGraphicsCardQuestionTest::outcome, REFUSED_ON_LINUX::equals);
        }

        claim("the question's sentence says the commands run as root on this machine, asking for the sudo password first",
                () -> assertThat(dialogs.choices).singleElement().extracting(AnsweringDialogs.Chose::sentence)
                        .isEqualTo(WHY_ON_LINUX + "\n\nInstall the toolkit runs these commands as root on this machine,"
                                + " asking for your password for sudo first:\n\n" + String.join("\n", THE_COMMANDS)
                                + "\n\n" + WHAT_THEY_DO + "\n\n" + THE_CPU_IMAGE));
        claim("the password was asked once, in a secret field, with this title, sentence and answers, whole",
                () -> assertThat(dialogs.secrets).singleElement().isEqualTo(new AnsweringDialogs.Secret(
                        "Install the NVIDIA Container Toolkit",
                        "Your password, for sudo. Vespera hands it to sudo for this install alone and keeps it nowhere.",
                        "Install", CANCEL)));
        claim("the install was given what was typed, once",
                () -> assertThat(docker.installsGiven).containsExactly(PASSWORD));
        claim("and then Docker was asked to start again",
                () -> assertThat(docker.calls).extracting(RecordingDockerControl.Call::kind)
                        .containsExactly("start", "install", "start"));
        claim("no text the window shows holds the password",
                () -> assertThat(everyText).noneMatch(text -> text.contains(PASSWORD)));
        claim("and no file under the test's folder, the launch values and the working directory among them, holds it",
                () -> assertThat(holding).isEmpty());
        claim("the password's field cancelled, nothing was installed and the refusal's line is shown",
                () -> assertThat(List.of(cancelled.installsGiven.size(), shownCancelled))
                        .containsExactly(0, REFUSED_ON_LINUX));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("A card that is not NVIDIA's is asked about with the CPU image and Cancel alone")
    void aCardThatIsNotNvidiasOffersTheCpuImageAlone() throws Exception {
        RecordingDockerControl docker = daemonDown()
                .startsAnswering(new DockerControl.Said(false, REFUSED_FOR_AN_AMD_CARD))
                .cardRefusedReading(refusal -> refusal.equals(REFUSED_FOR_AN_AMD_CARD)
                        ? Optional.of(AN_AMD_CARD) : Optional.empty());
        AnsweringDialogs dialogs;
        String shown;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "amd"))) {
            dialogs = window.dialogs;
            dialogs.chosen = Optional.empty();
            chooseTheGpuImage(window);
            pressStartDocker(window);
            shown = window.awaiting(TheGraphicsCardQuestionTest::outcome, REFUSED_FOR_AN_AMD_CARD::equals);
        }

        claim("the window asked, under The graphics card, the line's first sentence and what the CPU image costs, whole,"
                        + " with Use the CPU image and Cancel alone",
                () -> assertThat(dialogs.choices).singleElement().isEqualTo(new AnsweringDialogs.Chose(TITLE,
                        WHY_AN_AMD_CARD + "\n\n" + THE_CPU_IMAGE, List.of(USE_THE_CPU_IMAGE, CANCEL))));
        claim("closed with no answer, the refusal's line is shown",
                () -> assertThat(shown).isEqualTo(REFUSED_FOR_AN_AMD_CARD));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("The Preflight section says, for the GPU image while Docker answers, whether Docker has the nvidia runtime, each warning of the card a row beneath, asking once and again only after Docker stopped answering, and says nothing of the card for the CPU image")
    void thePreflightSectionSaysWhetherDockerHasTheNvidiaRuntimeAskingOnceWhileTheDaemonAnswers() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(EVERYTHING_ANSWERS)
                .graphicsCardAnswering(new DockerControl.CardCheck(Optional.of(false), List.of(A_WARNING)));
        List<Row> forTheGpuImage;
        int askedOverTwoLaterChecks;
        List<Row> forTheCpuImage;
        List<Row> onceDockerAnsweredAgain;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "preflight"))) {
            window.act(root -> WindowForTests.section(root, "preflight").setExpanded(true));
            chooseTheGpuImage(window);
            forTheGpuImage = window.awaiting(TheGraphicsCardQuestionTest::preflightRows,
                    rows -> rows.stream().anyMatch(row -> row.id().equals("graphics-card-warning-1")));
            Instant drawn = Instant.now();
            WindowForTests.within(() -> docker.checksAfter(drawn).size() >= 2);
            askedOverTwoLaterChecks = docker.cardChecks.size();

            window.act(root -> WindowForTests.node(root, "image-cpu", RadioButton.class).fire());
            forTheCpuImage = window.awaiting(TheGraphicsCardQuestionTest::preflightRows,
                    rows -> rows.stream().noneMatch(row -> row.id().startsWith("graphics-card")));

            docker.checkAnswering(NOTHING_ANSWERS);
            window.awaiting(root -> WindowForTests.node(root, "check-docker-daemon-mark", Labeled.class).getText(),
                    "KO"::equals);
            docker.graphicsCardAnswering(new DockerControl.CardCheck(Optional.of(true), List.of()))
                    .checkAnswering(EVERYTHING_ANSWERS);
            chooseTheGpuImage(window);
            onceDockerAnsweredAgain = window.awaiting(TheGraphicsCardQuestionTest::preflightRows,
                    rows -> rows.stream().anyMatch(row -> row.id().equals("graphics-card") && row.mark().equals("OK")));
        }

        claim("for the GPU image with Docker answering, the section's rows were the two folders', then the graphics"
                        + " card's and one warning's",
                () -> assertThat(forTheGpuImage).extracting(Row::id).containsExactly("corpus-root", "working-directory",
                        "graphics-card", "graphics-card-warning-1"));
        claim("the graphics card's row reads KO, that Docker has no nvidia runtime and that Start Docker offers the"
                        + " install, its caption what the GPU image needs; the warning's row reads KO and the warning,"
                        + " whole, its caption the same",
                () -> assertThat(forTheGpuImage.subList(2, 4)).containsExactly(
                        new Row("graphics-card", "Graphics card", "KO", "ko", NOT_LISTED, NOT_LISTED_CAPTION),
                        new Row("graphics-card-warning-1", "Warning", "KO", "ko", A_WARNING, A_WARNING)));
        claim("the card was asked of Docker once, for the GPU image, though the header checked twice more",
                () -> assertThat(askedOverTwoLaterChecks).isEqualTo(1));
        claim("for the CPU image, no row says anything of the card",
                () -> assertThat(forTheCpuImage).extracting(Row::id)
                        .containsExactly("corpus-root", "working-directory"));
        claim("once a check found Docker not answering and it answered again, the card was asked a second time, each"
                        + " time for the GPU image",
                () -> assertThat(docker.cardChecks).extracting(LaunchValues::doclingImage)
                        .containsExactly(StartingValues.DOCLING_IMAGE_GPU, StartingValues.DOCLING_IMAGE_GPU));
        claim("and the row then read OK, that Docker has the nvidia runtime, with no caption and no warning's row",
                () -> assertThat(onceDockerAnsweredAgain).filteredOn(row -> row.id().startsWith("graphics-card"))
                        .containsExactly(new Row("graphics-card", "Graphics card", "OK", "ok", LISTED, null)));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("An answer of the card still being asked when a check finds Docker not answering is not kept, and the card is asked again once Docker answers")
    void anAnswerOfTheCardStillBeingAskedWhenDockerStopsAnsweringIsNotKept() throws Exception {
        CountDownLatch answered = new CountDownLatch(1);
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(EVERYTHING_ANSWERS)
                .graphicsCardAnswering(new DockerControl.CardCheck(Optional.of(false), List.of(A_WARNING)))
                .holdingCardChecksUntil(answered);
        List<Row> afterTheAnswerCameBack;
        List<Row> onceDockerAnsweredAgain;
        try (WindowForTests window = WindowForTests.shown(aState(docker, "dropped"))) {
            window.act(root -> WindowForTests.section(root, "preflight").setExpanded(true));
            chooseTheGpuImage(window);
            try {
                WindowForTests.within(() -> docker.cardChecks.size() == 1);
                docker.checkAnswering(NOTHING_ANSWERS);
                window.awaiting(root -> WindowForTests.node(root, "check-docker-daemon-mark", Labeled.class).getText(),
                        "KO"::equals);
            } finally {
                answered.countDown();
            }
            // Docker stays not answering: watched for the whole bound, no row of the card may show at any reading.
            afterTheAnswerCameBack = window.awaiting(TheGraphicsCardQuestionTest::preflightRows,
                    rows -> rows.stream().anyMatch(row -> row.id().startsWith("graphics-card")));

            docker.graphicsCardAnswering(new DockerControl.CardCheck(Optional.of(true), List.of()))
                    .checkAnswering(EVERYTHING_ANSWERS);
            onceDockerAnsweredAgain = window.awaiting(TheGraphicsCardQuestionTest::preflightRows,
                    rows -> rows.stream().anyMatch(row -> row.id().equals("graphics-card") && row.mark().equals("OK")));
        }

        claim("the answer asked while Docker answered came back after a check found it not answering, and while Docker"
                        + " still did not answer no row said anything of the card",
                () -> assertThat(afterTheAnswerCameBack).extracting(Row::id)
                        .containsExactly("corpus-root", "working-directory"));
        claim("once Docker answered again, the card was asked a second time",
                () -> assertThat(docker.cardChecks).hasSize(2));
        claim("and the row read that second answer: OK, Docker has the nvidia runtime, no warning's row",
                () -> assertThat(onceDockerAnsweredAgain).filteredOn(row -> row.id().startsWith("graphics-card"))
                        .containsExactly(new Row("graphics-card", "Graphics card", "OK", "ok", LISTED, null)));
    }

    @Test
    @Story("The control panel asks before it takes the CPU image or installs NVIDIA's toolkit")
    @DisplayName("The Services section offers the GPU image for an NVIDIA card with NVIDIA's toolkit in Docker, and the CPU image for any machine, AMD and Intel cards included")
    void theServicesSectionSaysTheGpuImageIsForNvidiasCardsAlone() throws Exception {
        List<String> words;
        try (WindowForTests window = WindowForTests.shown(aState(daemonDown(), "words"))) {
            words = window.read(root -> List.of(WindowForTests.node(root, "image-gpu", RadioButton.class).getText(),
                    WindowForTests.node(root, "image-cpu", RadioButton.class).getText()));
        }

        claim("the two choices read, whole, the GPU's and the CPU's",
                () -> assertThat(words).containsExactly(
                        "GPU: an NVIDIA graphics card, with NVIDIA's toolkit in Docker. Much faster.",
                        "CPU: any machine, AMD and Intel cards included. Slower."));
    }

    /** A control whose check finds the daemon and both containers down, so that Start Docker is on. */
    private static RecordingDockerControl daemonDown() {
        return new RecordingDockerControl().checkAnswering(RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED));
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

    private static void chooseTheGpuImage(WindowForTests window) throws Exception {
        window.act(root -> WindowForTests.node(root, "image-gpu", RadioButton.class).fire());
    }

    private static void pressStartDocker(WindowForTests window) throws Exception {
        window.awaiting(root -> !WindowForTests.node(root, "start-docker", Button.class).isDisabled(), Boolean::booleanValue);
        window.act(root -> WindowForTests.node(root, "start-docker", Button.class).fire());
    }

    private static String outcome(Parent root) {
        return WindowForTests.node(root, "outcome", TextInputControl.class).getText();
    }

    private static TextInputControl field(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class);
    }

    /** The output panel's whole text. */
    private static String output(Parent root) {
        return WindowForTests.node(root, "output", TextArea.class).getText();
    }

    /** One row of the Preflight section: its id, name, mark, the mark's class, its words and their caption. */
    record Row(String id, String name, String mark, String markClass, String words, String caption) {
    }

    /** The Preflight grid's rows, by their row index: a node {@code preflight-<row>} with its {@code -mark} and {@code -words}. */
    private static List<Row> preflightRows(Parent root) {
        GridPane grid = WindowForTests.node(root, "preflight-list", GridPane.class);
        List<Node> names = new ArrayList<>();
        for (Node child : grid.getChildren()) {
            String id = child.getId();
            if (id != null && id.startsWith("preflight-") && !id.endsWith("-mark") && !id.endsWith("-words")) {
                names.add(child);
            }
        }
        names.sort(Comparator.comparingInt(node -> Objects.requireNonNullElse(GridPane.getRowIndex(node), 0)));
        List<Row> rows = new ArrayList<>();
        for (Node name : names) {
            Node mark = WindowForTests.node(root, name.getId() + "-mark", Node.class);
            Node words = WindowForTests.node(root, name.getId() + "-words", Node.class);
            rows.add(new Row(name.getId().substring("preflight-".length()), text(name), text(mark),
                    TheWindowChecksAndStartsNothingAsItOpensTest.markClass(mark), text(words),
                    WindowForTests.caption(words)));
        }
        return rows;
    }

    private static String text(Node node) {
        return node instanceof TextInputControl input ? input.getText() : ((Labeled) node).getText();
    }

    /** Every text a label or a text control of the window shows. */
    private static List<String> everyText(Parent root) {
        return HeadlessToolkit.everyNodeUnder(root).stream()
                .map(node -> node instanceof Labeled labeled ? labeled.getText()
                        : node instanceof TextInputControl input ? input.getText() : null)
                .filter(text -> text != null)
                .toList();
    }

    /** The files under {@code under} whose bytes, read as UTF-8, hold {@code text}. */
    private static List<Path> filesHolding(Path under, String text) throws Exception {
        try (Stream<Path> files = Files.walk(under)) {
            return files.filter(Files::isRegularFile).filter(file -> {
                try {
                    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(text);
                } catch (java.io.IOException unreadable) {
                    throw new java.io.UncheckedIOException(unreadable);
                }
            }).toList();
        }
    }
}
