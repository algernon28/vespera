package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * Where the control panel's Docker code takes its settings from (ADR-390 §4): the WSL distribution and {@code
 * vespera.containers.wait-at-most} from the sources the window's fields start from, the environment over {@code .env}
 * over the jar's {@code application.yaml}; and the two addresses from the launch values held, a blank one being the
 * address the environment, {@code .env} or the jar's {@code application.yaml} names.
 *
 * <p>No Docker command is run. The start is given a lock folder that is a file, so that a start that went for the
 * container lock, which it takes before it runs any command, is refused there with the lock's line and runs nothing:
 * the line is what tells a start that did nothing from one that would have run Docker.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("818")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class TheControlPanelsDockerSettingsTest {

    private static final String DISTRIBUTION_VARIABLE = "VESPERA_CONTAINERS_WSL_DISTRIBUTION";

    private static final String WAIT_VARIABLE = "VESPERA_CONTAINERS_WAIT_AT_MOST";

    private static final String DOCLING_VARIABLE = "VESPERA_DOCLING_BASE_URL";

    private static final String DISTRIBUTION_PROPERTY = "vespera.containers.wsl-distribution";

    private static final String WAIT_PROPERTY = "vespera.containers.wait-at-most";

    private static final String OLLAMA_VARIABLE = "SPRING_AI_OLLAMA_BASE_URL";

    private static final String IMAGE_VARIABLE = "VESPERA_DOCLING_IMAGE";

    /** The repository root, where Maven runs the tests from and where the compose files lie, named as their folder. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    /** The compose folder the window's settings name: none, which stands for the folder beside the jar (ADR-430 §12). */
    private static final Path BESIDE_THE_JAR = null;

    /**
     * A converter and Ollama on another machine, as an operator's environment would name them: an address of the range
     * kept for documentation (RFC 5737), which is no machine's own and, written out, is never looked up (ADR-430 §10).
     */
    private static final String DOCLING_ELSEWHERE = "http://192.0.2.10:5001";

    private static final String OLLAMA_ELSEWHERE = "http://192.0.2.10:11434";

    /** The least the wait is: a value under it is raised to it. */
    private static final Duration A_SECOND = Duration.ofSeconds(1);

    /** The wait where no source names one the jar's forms read: the jar's own 180 seconds. */
    private static final Duration THE_DEFAULT_WAIT = Duration.ofSeconds(180);

    /** A value that is a duration in neither the jar's simple form nor ISO-8601. */
    private static final String NO_DURATION = "three minutes";

    /** A bare number of twenty digits, past the largest long. */
    private static final String TOO_LARGE = "99999999999999999999";

    /** Every launch value blank, as a window holds them before the operator has typed any. */
    private static final LaunchValues BLANK = new LaunchValues("", "", "", "", "", "", "", "");

    @TempDir
    Path folder;

    @Test
    @Story("The control panel starts the Docker engine as its window opens")
    @DisplayName("The distribution and the wait are read from the environment, then .env, then the jar's application.yaml")
    void theDistributionAndTheWaitAreReadFromTheSourcesInTheirOrder() {
        Path lockFolder = folder.resolve("root-locks");
        Path profileFolder = folder.resolve("profile");
        StartingValues.Sources everySource = new StartingValues.Sources(Map.of(),
                Map.of(DISTRIBUTION_VARIABLE, "Ubuntu-24.04"),
                Map.of(DISTRIBUTION_VARIABLE, "Debian", WAIT_VARIABLE, "5m"),
                Map.of(DISTRIBUTION_PROPERTY, "", WAIT_PROPERTY, "180s"));

        DockerControl.Settings read = ControlPanel.dockerSettings(everySource, lockFolder, profileFolder);

        claim("the environment's distribution comes before .env's, .env's wait of 5m, three hundred seconds, before"
                        + " application.yaml's 180s, the lock folder and the profile folder are the ones given, and the"
                        + " compose files are the ones beside the jar, which no folder named stands for",
                () -> assertThat(read).isEqualTo(new DockerControl.Settings(
                        "Ubuntu-24.04", Duration.ofMinutes(5), lockFolder, profileFolder, BESIDE_THE_JAR, everySource)));

        StartingValues.Sources theJarsOwn = new StartingValues.Sources(Map.of(), Map.of(), Map.of(),
                Map.of(DISTRIBUTION_PROPERTY, "", WAIT_PROPERTY, "180s"));
        claim("where only the jar's application.yaml names them, the distribution is blank, WSL's default, and the"
                        + " wait is its 180 seconds",
                () -> assertThat(ControlPanel.dockerSettings(theJarsOwn, lockFolder, profileFolder)).isEqualTo(
                        new DockerControl.Settings("", Duration.ofSeconds(180), lockFolder, profileFolder,
                                BESIDE_THE_JAR, theJarsOwn)));
    }

    /**
     * The wait in each form the jar reads it in, through Spring's {@code DurationStyle} (ADR-395 §9, amending ADR-390
     * §4): a bare number is milliseconds, a suffix of a unit is read in any case, ISO-8601 is read, a value under a
     * second is a second, and a value neither form reads, an overflowing number among them, is 180 seconds.
     */
    @Test
    @Link(name = "ADR-395", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
    @Story("The control panel starts the Docker engine as its window opens")
    @DisplayName("The wait is read as the jar reads it: a bare number in milliseconds, a unit in any case, ISO-8601, at least a second, and three minutes where no form reads it")
    void theWaitIsReadInTheJarsFormsAndThreeMinutesWhereNoFormReadsIt() {
        claim("PT3M is three minutes, 5M five minutes, the unit read whatever its case, and 2d two days",
                () -> assertThat(Stream.of("PT3M", "5M", "2d").map(ControlPanel::waitAtMostOf).toList())
                        .containsExactly(Duration.ofMinutes(3), Duration.ofMinutes(5), Duration.ofDays(2)));
        claim("180, a bare number, is 180 milliseconds as the jar reads it, and 500ms is half a second; each is under a"
                        + " second, so each is one second",
                () -> assertThat(Stream.of("180", "500ms").map(ControlPanel::waitAtMostOf).toList())
                        .containsExactly(A_SECOND, A_SECOND));
        claim("a value that is no duration in either form is the default of three minutes",
                () -> assertThat(ControlPanel.waitAtMostOf(NO_DURATION)).isEqualTo(THE_DEFAULT_WAIT));
        claim("and so is a number of " + TOO_LARGE.length() + " digits, too large for a long, rather than an error out"
                        + " of the opening of the window",
                () -> assertThat(catchThrowable(() -> ControlPanel.waitAtMostOf(TOO_LARGE))).isNull());
        claim("that number is read as the default of three minutes",
                () -> assertThat(ControlPanel.waitAtMostOf(TOO_LARGE)).isEqualTo(THE_DEFAULT_WAIT));
    }

    /**
     * Since ADR-430 §5 the window's start is <i>Start Docker</i>'s, which brings the containers on this machine up, and
     * since §10 each container is judged on its own: so both are named, on another machine and then on loopback ports
     * where nothing answers. The compose files are the repository's own, named in the settings (§12), and the image the
     * one {@code compose.yaml} builds, named in the environment, so the start that goes on passes the image's check and
     * is refused at the container lock in the folder that is a file, before any command, in the lock's line.
     */
    @Test
    @Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
    @Issue("889")
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("With the launch values blank and the environment naming both containers on another machine, the window's start runs nothing, and naming them here it is refused at the container lock")
    void aConverterTheEnvironmentNamesOnAnotherMachineStartsNothingWhereTheLaunchValuesAreBlank() throws IOException {
        Path notAFolder = Files.createFile(folder.resolve("a-file-where-the-lock-folder-would-be"));
        Path profileFolder = Files.createDirectory(folder.resolve("profile"));
        StartingValues.Sources remote = new StartingValues.Sources(Map.of(),
                Map.of(DOCLING_VARIABLE, DOCLING_ELSEWHERE, OLLAMA_VARIABLE, OLLAMA_ELSEWHERE), Map.of(), Map.of());

        DockerControl.Said said = DockerControl.inProcess(new DockerControl.Settings("", Duration.ofSeconds(1),
                notAFolder, profileFolder, REPOSITORY, remote)).start(BLANK);

        claim("the start went through and said nothing: the blank addresses were read as the environment's, on another"
                        + " machine, so the container lock was not taken and no command was run",
                () -> assertThat(said).isEqualTo(new DockerControl.Said(true, "")));
        claim("and nothing was written in the profile folder it was given", () -> {
            try (Stream<Path> written = Files.list(profileFolder)) {
                assertThat(written).isEmpty();
            }
        });

        Path anotherProfile = Files.createDirectory(folder.resolve("another-profile"));
        DockerControl.Said here = DockerControl.inProcess(new DockerControl.Settings("", Duration.ofSeconds(1),
                notAFolder, anotherProfile, REPOSITORY, nothingAnsweringHere())).start(BLANK);
        claim("named on this machine where nothing answers, the same start goes on, passes the image's check, and is"
                        + " refused at the container lock in the folder that is a file, in the lock's line, whole",
                () -> assertThat(here).isEqualTo(new DockerControl.Said(false, theLocksRefusal(notAFolder))));
    }

    /**
     * Both containers named in the environment on loopback ports of this machine where nothing answers, and the image
     * the one {@code compose.yaml} builds, so that no starting value of the machine or the jar is read.
     */
    private static StartingValues.Sources nothingAnsweringHere() throws IOException {
        return new StartingValues.Sources(Map.of(), Map.of(DOCLING_VARIABLE, "http://127.0.0.1:" + aFreePort(),
                OLLAMA_VARIABLE, "http://127.0.0.1:" + aFreePort(), IMAGE_VARIABLE, imageComposeYamlBuilds()),
                Map.of(), Map.of());
    }

    /** The line a start refused at a container lock in {@code notAFolder}, a file, gives (ADR-395 §6). */
    private static String theLocksRefusal(Path notAFolder) {
        return "the containers are not answering and Vespera cannot start them: the lock file "
                + notAFolder.resolve("containers.lock").toAbsolutePath() + " cannot be opened (" + notAFolder
                + "). Make its folder writable, or name another with vespera.corpus-root-lock-folder, and run the same"
                + " command again.";
    }

    /** The Docling image the repository's {@code compose.yaml} builds, read from the file as it stands. */
    @SuppressWarnings("unchecked")
    private static String imageComposeYamlBuilds() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(REPOSITORY.resolve("compose.yaml")));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }

    /** A loopback port that was free a moment ago. */
    private static int aFreePort() throws IOException {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    /**
     * The window's start sees to {@code .wslconfig} before anything else (ADR-421 §6), in the profile folder its
     * settings name and never in the machine's own (§7); since ADR-430 §5 the start is <i>Start Docker</i>'s. Guarded to
     * Windows: the system is the one this JVM runs on, and elsewhere nothing is written. The containers are named on
     * loopback ports where nothing answers, the compose files are the repository's and the image the one {@code
     * compose.yaml} builds, and the lock folder is a file, so the start is refused at the lock before any command, and
     * the lock's line is the one line the start gives.
     */
    @Test
    @Link(name = "ADR-421", url = Adr.ON_WINDOWS_VESPERA_WRITES_WSLCONFIG_WHERE_THERE_IS_NONE, type = "adr")
    @Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
    @Issue("881")
    @Story("Start Docker starts Docker and the containers")
    @DisplayName("On Windows the window's start writes .wslconfig in the profile folder it is given before anything else, and a refusal is the line it gives")
    void onWindowsTheWindowsStartWritesItInTheProfileFolderItIsGiven() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").startsWith("Windows"),
                "the system is the one this JVM runs on, and WSL is on Windows alone");
        Path notAFolder = Files.createFile(folder.resolve("a-file-where-the-lock-folder-would-be"));
        Path profileFolder = Files.createDirectory(folder.resolve("profile"));

        DockerControl.Said said = DockerControl.inProcess(new DockerControl.Settings("", Duration.ofSeconds(1),
                notAFolder, profileFolder, REPOSITORY, nothingAnsweringHere())).start(BLANK);

        claim("the profile folder given holds .wslconfig, its two lines [general] and instanceIdleTimeout=-1 each"
                        + " ended by CR LF",
                () -> assertThat(Files.readString(profileFolder.resolve(".wslconfig")))
                        .isEqualTo("[general]\r\ninstanceIdleTimeout=-1\r\n"));
        claim("and the start was refused at the container lock, in the lock's line, whole, which is the one line it gives",
                () -> assertThat(said).isEqualTo(new DockerControl.Said(false, theLocksRefusal(notAFolder))));
    }
}
