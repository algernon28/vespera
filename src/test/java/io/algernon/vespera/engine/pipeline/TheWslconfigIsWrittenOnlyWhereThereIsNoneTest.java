package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

/**
 * On Windows, Vespera writes {@code .wslconfig} in the profile folder, holding {@code [general]} and {@code
 * instanceIdleTimeout=-1}, where no file of that name is there, and reads one that is there without ever changing it
 * (ADR-421): {@link WslIdleTimeout} for the file and its lines, and {@link ContainerStarter} for where a start sees to it.
 *
 * <p>Every profile folder here is one of JUnit's temporary folders, named to the code under test; no test reads or sets
 * {@code user.home}, so the machine's own {@code .wslconfig} is never looked at (ADR-421 §7). No Docker is needed: the
 * starter is given a {@link ScriptedDocker} and a scripted health, as in {@code ContainerStartTest}, and the {@link
 * HostSystem} is named, so the claims about Windows hold on every system the suite runs on.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("881")
@Link(name = "ADR-421", url = Adr.ON_WINDOWS_VESPERA_WRITES_WSLCONFIG_WHERE_THERE_IS_NONE, type = "adr")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class TheWslconfigIsWrittenOnlyWhereThereIsNoneTest {

    /** The repository root, where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final String FILE_NAME = ".wslconfig";

    /**
     * The file Vespera writes, byte for byte: the operator's two lines, each ended by CR LF, in ASCII, with no
     * byte-order mark.
     */
    private static final String WRITTEN = "[general]\r\ninstanceIdleTimeout=-1\r\n";

    /** How many bytes that is: nine and two, then twenty-two and two. */
    private static final int WRITTEN_BYTES = 35;

    /** What every line but the first two says the missing setting costs. */
    private static final String THE_COST = "so WSL may shut its distribution down while idle, and Docker and the containers"
            + " with it, in the middle of a run.";

    /** What every line that asks the operator to act ends with: the restart, and what it stops. */
    private static final String THE_RESTART =
            "then run wsl --shutdown, which stops every WSL distribution and the containers in them.";

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    private static final URI DOCLING_ELSEWHERE = URI.create("http://docling.example.org:5001");

    private static final URI OLLAMA_ELSEWHERE = URI.create("http://ollama.example.org:11434");

    /** How long these tests wait for the engine or a container to answer: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Engine checks the engine fails before it answers, in the tests where Vespera starts it. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    /** Run inside WSL's default distribution, as root, with no shell between (ADR-390 §1). */
    private static final List<String> IN_WSL = List.of("wsl", "-u", "root", "--exec");

    private static final List<String> WSL_START = concat(IN_WSL, List.of("systemctl", "start", "docker"));

    /** The Docling image {@code compose.yaml} builds, read from the file as it stands. */
    private static String cpuImage;

    @TempDir
    Path folder;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void readTheImageTheComposeFileBuilds() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(REPOSITORY.resolve("compose.yaml")));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        cpuImage = (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("Where the profile folder has no .wslconfig, one is written holding the two lines [general] and instanceIdleTimeout=-1, and a line says so")
    void whereThereIsNoneItIsWrittenWithTheTwoLinesAndSaysSo() throws IOException {
        Path profile = Files.createDirectory(folder.resolve("profile"));
        Path file = profile.resolve(FILE_NAME);

        Optional<String> line = WslIdleTimeout.keepTheDistributionUp(profile);

        claim("the file holds exactly the " + WRITTEN_BYTES + " bytes of [general] and instanceIdleTimeout=-1, each line"
                        + " ended by CR LF, with nothing before, between or after",
                () -> assertThat(Files.readAllBytes(file)).isEqualTo(WRITTEN.getBytes(StandardCharsets.US_ASCII)));
        claim("and it is the only file written in the profile folder", () -> assertThat(namesIn(profile))
                .containsExactly(FILE_NAME));
        claim("and the one line says where it was written, what it sets, why, and when it takes effect",
                () -> assertThat(line).contains("Vespera wrote " + file + ", setting instanceIdleTimeout=-1 under"
                        + " [general], so that WSL does not shut its distribution down while idle, and Docker and the"
                        + " containers with it. It applies from WSL's next start; if WSL is running now, wsl --shutdown"
                        + " starts it afresh, and stops every WSL distribution and the containers in them."));
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("A .wslconfig that is there is read for instanceIdleTimeout=-1 under [general] and never written: one that sets it says nothing, and one that lacks it gets a line saying what to add")
    void aFileThereIsReadForTheSettingAndNeverWritten() throws IOException {
        Map<String, String> setting = new LinkedHashMap<>();
        setting.put("the two lines Vespera writes, ended by LF alone", "[general]\ninstanceIdleTimeout=-1\n");
        setting.put("the setting among other sections, its key in another case, blanks around it, comments of both"
                        + " kinds and a byte-order mark",
                "﻿# the operator's\r\n[wsl2]\r\nmemory=8GB\r\n\r\n; general\r\n[GENERAL]\r\n"
                        + "  InstanceIdleTimeout = -1  \r\nautoMemoryReclaim=gradual\r\n");
        setting.put("a later value of -1 after another", "[general]\ninstanceIdleTimeout=15000\ninstanceIdleTimeout=-1");

        Map<String, String> lacking = new LinkedHashMap<>();
        lacking.put("an empty file", "");
        lacking.put("other sections alone", "[wsl2]\nmemory=8GB\n");
        lacking.put("WSL's default value", "[general]\ninstanceIdleTimeout=15000\n");
        lacking.put("the key under another section", "[wsl2]\ninstanceIdleTimeout=-1\n");
        lacking.put("the key commented out", "[general]\n# instanceIdleTimeout=-1\n");
        lacking.put("a later value that is not -1", "[general]\ninstanceIdleTimeout=-1\ninstanceIdleTimeout=15000\n");
        lacking.put("a value with more after it", "[general]\ninstanceIdleTimeout=-1 # never\n");

        int folders = 0;
        for (Map.Entry<String, String> sets : setting.entrySet()) {
            Path profile = Files.createDirectory(folder.resolve("sets-" + folders++));
            Path file = profile.resolve(FILE_NAME);
            byte[] before = sets.getValue().getBytes(StandardCharsets.UTF_8);
            Files.write(file, before);

            Optional<String> line = WslIdleTimeout.keepTheDistributionUp(profile);

            claim("a file holding " + sets.getKey() + " sets it, and gives no line",
                    () -> assertThat(line).isEmpty());
            claim("and its bytes are as they were", () -> assertThat(Files.readAllBytes(file)).isEqualTo(before));
            claim("and it is the only file in the folder", () -> assertThat(namesIn(profile))
                    .containsExactly(FILE_NAME));
        }
        for (Map.Entry<String, String> lacks : lacking.entrySet()) {
            Path profile = Files.createDirectory(folder.resolve("lacks-" + folders++));
            Path file = profile.resolve(FILE_NAME);
            byte[] before = lacks.getValue().getBytes(StandardCharsets.UTF_8);
            Files.write(file, before);

            Optional<String> line = WslIdleTimeout.keepTheDistributionUp(profile);

            claim("a file holding " + lacks.getKey() + " lacks it, and the line says so, that Vespera changes no file"
                            + " that is there, and what the operator does",
                    () -> assertThat(line).contains(file + " does not set instanceIdleTimeout=-1 under [general], "
                            + THE_COST + " Vespera changes no file that is there: add it yourself, " + THE_RESTART));
            claim("and its bytes are as they were: nothing was added", () -> assertThat(Files.readAllBytes(file))
                    .isEqualTo(before));
            claim("and no other file was written", () -> assertThat(namesIn(profile)).containsExactly(FILE_NAME));
        }
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("A .wslconfig that cannot be written or read, or a profile folder nobody can name, is one line, and nothing is made")
    void aFileThatCannotBeWrittenOrReadOrNoFolderIsALineAndNothingIsMade() throws IOException {
        Path notThere = folder.resolve("a-profile-folder-that-is-not-there");
        Path unwritten = notThere.resolve(FILE_NAME);

        Optional<String> cannotWrite = WslIdleTimeout.keepTheDistributionUp(notThere);

        claim("a profile folder that is not there gives the line that the file could not be written, with why, and"
                        + " how the operator writes it",
                () -> assertThat(cannotWrite).hasValueSatisfying(line -> assertThat(line).matches(
                        Pattern.quote("Vespera could not write " + unwritten + " (") + ".+"
                                + Pattern.quote("), " + THE_COST + " Create it with the two lines [general] and"
                                        + " instanceIdleTimeout=-1, " + THE_RESTART))));
        claim("and the profile folder was not made", () -> assertThat(notThere).doesNotExist());

        Path profile = Files.createDirectory(folder.resolve("profile"));
        Path aFolder = Files.createDirectory(profile.resolve(FILE_NAME));

        Optional<String> cannotRead = WslIdleTimeout.keepTheDistributionUp(profile);

        claim("a folder named .wslconfig is one that is there, and gives the line that it could not be read, with"
                        + " why, and what the operator does",
                () -> assertThat(cannotRead).hasValueSatisfying(line -> assertThat(line).matches(
                        Pattern.quote("Vespera could not read " + aFolder + " (") + ".+"
                                + Pattern.quote(") to see whether it sets instanceIdleTimeout=-1 under [general];"
                                        + " without it WSL may shut its distribution down while idle, and Docker and"
                                        + " the containers with it, in the middle of a run. See that it does, "
                                        + THE_RESTART))));
        claim("and that folder is left as it was, empty", () -> assertThat(namesIn(aFolder)).isEmpty());

        claim("with no profile folder known, the line says Vespera cannot tell where it is, names the file by"
                        + " %UserProfile%, and says what the operator does",
                () -> assertThat(WslIdleTimeout.keepTheDistributionUp(null)).contains("Vespera cannot tell where the"
                        + " Windows profile folder is, so it cannot see whether %UserProfile%\\.wslconfig sets"
                        + " instanceIdleTimeout=-1 under [general]; without it WSL may shut its distribution down"
                        + " while idle, and Docker and the containers with it, in the middle of a run. See that it does, "
                        + THE_RESTART));
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("On Windows a start of the containers writes .wslconfig before it asks them anything, even where they answer, and then runs the commands it ran before, none restarting WSL")
    void onWindowsAStartWritesItBeforeAskingTheContainersAndRunsWhatItRanBefore() throws IOException {
        Path profile = Files.createDirectory(folder.resolve("answering"));
        WatchingHealth answering = new WatchingHealth(profile.resolve(FILE_NAME), true);
        ScriptedDocker untouched = ScriptedDocker.engineRunning();

        ContainerStarter.Outcome found = starter(here(profile), untouched, answering, HostSystem.WINDOWS).ensureRunning();

        claim("with both containers answering, the file was already there when the first container was asked",
                () -> assertThat(answering.fileThereWhenFirstAsked).isTrue());
        claim("and the start found them answering and ran no command", () -> assertThat(List.of(found,
                untouched.commands())).containsExactly(ContainerStarter.Outcome.ALREADY_ANSWERING, List.of()));
        claim("and the file holds the " + WRITTEN_BYTES + " bytes Vespera writes", () -> assertThat(
                Files.readAllBytes(profile.resolve(FILE_NAME))).isEqualTo(WRITTEN.getBytes(StandardCharsets.US_ASCII)));

        Path down = Files.createDirectory(folder.resolve("engine-down"));
        WatchingHealth starting = new WatchingHealth(down.resolve(FILE_NAME), false);
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(WSL_START, 0, "")
                .whenUpSucceeds(() -> starting.answering = true);

        ContainerStarter.Outcome started = starter(here(down), docker, starting, HostSystem.WINDOWS).ensureRunning();

        claim("with the engine down, the file was there before the containers were first asked, and the containers were"
                        + " started", () -> assertThat(List.of(starting.fileThereWhenFirstAsked, started))
                .containsExactly(true, ContainerStarter.Outcome.STARTED));
        claim("and the commands were, in order and whole, those a start on Windows runs: the engine check, its start,"
                        + " the " + ENGINE_CHECKS_FAILED + " checks of the wait, the checks for compose and buildx, and"
                        + " the up", () -> assertThat(docker.commands()).containsExactlyElementsOf(theWindowsStart()));
        claim("and no command shuts WSL down or terminates a distribution", () -> assertThat(docker.commands())
                .noneMatch(command -> command.contains("--shutdown") || command.contains("--terminate")));
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("Off Windows, or with both containers looked for on another machine, nothing is written and the control panel's call says nothing")
    @Issue("889")
    @Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
    void nothingIsWrittenOffWindowsOrForContainersOnAnotherMachine() throws IOException {
        for (HostSystem system : List.of(HostSystem.LINUX, HostSystem.MAC)) {
            Path profile = Files.createDirectory(folder.resolve(system.name()));
            ContainerStarter starter =
                    starter(here(profile), ScriptedDocker.engineRunning(), new WatchingHealth(null, true), system);

            ContainerStarter.Outcome outcome = starter.ensureRunning();
            Optional<String> said = starter.keepTheWslDistributionUp();

            claim("on " + system + " the start found the containers answering, the control panel's call said nothing,"
                            + " and the profile folder is empty",
                    () -> assertThat(List.of(outcome, said, namesIn(profile))).containsExactly(
                            ContainerStarter.Outcome.ALREADY_ANSWERING, Optional.empty(), List.of()));
        }

        Path profile = Files.createDirectory(folder.resolve("elsewhere"));
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        ContainerStarter elsewhere = new ContainerStarter(new ContainerStartSettings(DOCLING_ELSEWHERE, OLLAMA_ELSEWHERE,
                cpuImage, WAIT_AT_MOST, "", profile), docker, new WatchingHealth(null, false), HostSystem.WINDOWS,
                () -> ComposeFiles.in(REPOSITORY), folder.resolve("locks"), CHECK_EVERY, new ThisMachine(
                        name -> switch (name) {
                            case "docling.example.org" -> new InetAddress[] {InetAddress.ofLiteral("192.0.2.10")};
                            case "ollama.example.org" -> new InetAddress[] {InetAddress.ofLiteral("192.0.2.11")};
                            default -> throw new UnknownHostException(name);
                        }, List::of));

        ContainerStarter.Outcome outcome = elsewhere.ensureRunning();
        Optional<String> said = elsewhere.keepTheWslDistributionUp();

        claim("on Windows with docling-serve at " + DOCLING_ELSEWHERE + " and Ollama at " + OLLAMA_ELSEWHERE + ", both"
                        + " resolving to another machine, the start starts nothing and runs no command, the control"
                        + " panel's call says nothing, and the profile folder is empty (ADR-430 §10)",
                () -> assertThat(List.of(outcome, docker.commands(), said, namesIn(profile))).containsExactly(
                        ContainerStarter.Outcome.ON_ANOTHER_MACHINE, List.of(), Optional.empty(), List.of()));
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("A .wslconfig that cannot be written, or a profile folder nobody can name, refuses no start: the containers are started with the same commands")
    void aWriteThatFailsRefusesNoStart() {
        Map<String, ContainerStartSettings> failing = new LinkedHashMap<>();
        failing.put("a profile folder that is not there", here(folder.resolve("not-there")));
        failing.put("no profile folder known", new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage,
                WAIT_AT_MOST, "", null));

        for (Map.Entry<String, ContainerStartSettings> each : failing.entrySet()) {
            WatchingHealth health = new WatchingHealth(null, false);
            ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                    .answering(WSL_START, 0, "")
                    .whenUpSucceeds(() -> health.answering = true);

            ContainerStarter.Outcome outcome = starter(each.getValue(), docker, health, HostSystem.WINDOWS).ensureRunning();

            claim("with " + each.getKey() + ", the containers were started, nothing refused", () -> assertThat(outcome)
                    .isEqualTo(ContainerStarter.Outcome.STARTED));
            claim("and the commands were those a start on Windows runs, in order and whole",
                    () -> assertThat(docker.commands()).containsExactlyElementsOf(theWindowsStart()));
        }
        claim("and the profile folder that was not there was not made", () -> assertThat(folder.resolve("not-there"))
                .doesNotExist());
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("The control panel's call writes .wslconfig and says so, once; the engine's start alone, the stop of the containers and the shut-down write nothing")
    void theControlPanelsCallWritesItAndTheEnginesStartAndTheStopsWriteNothing() throws IOException {
        Path profile = Files.createDirectory(folder.resolve("window"));
        Path file = profile.resolve(FILE_NAME);
        ContainerStarter window =
                starter(here(profile), ScriptedDocker.engineRunning(), new WatchingHealth(null, false), HostSystem.WINDOWS);

        Optional<String> first = window.keepTheWslDistributionUp();
        Optional<String> second = window.keepTheWslDistributionUp();

        claim("the first call wrote the file and said so, in the line of a file written",
                () -> assertThat(List.of(first.map(line -> line.startsWith("Vespera wrote " + file + ", ")),
                        Files.readString(file, StandardCharsets.US_ASCII)))
                        .containsExactly(Optional.of(true), WRITTEN));
        claim("and the second found it setting instanceIdleTimeout=-1 and said nothing",
                () -> assertThat(second).isEmpty());

        Path untouched = Files.createDirectory(folder.resolve("untouched"));
        ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        ContainerStarter starter = starter(here(untouched), docker, new WatchingHealth(null, true), HostSystem.WINDOWS);

        ContainerStarter.Outcome engine = starter.ensureTheEngineRuns();
        Optional<String> stopped = starter.stopTheContainers();
        ContainerStarter.ShutDown shutDown = starter.shutDown();

        claim("the engine's start alone found it answering, and the stop of the containers and the shut-down went"
                        + " through", () -> assertThat(List.of(engine, stopped, shutDown.done()))
                .containsExactly(ContainerStarter.Outcome.ENGINE_ANSWERING, Optional.empty(), true));
        claim("and none of the three wrote anything in the profile folder", () -> assertThat(namesIn(untouched))
                .isEmpty());
    }

    @Test
    @Story("On Windows, WSL is kept from shutting Docker down while idle")
    @DisplayName("The profile folder is user.home as the environment gives it, none where it is blank or missing, and none where the settings are built without one")
    void theProfileFolderIsUserHomeAsTheEnvironmentGivesIt() {
        Path home = folder.resolve("home");

        claim("the folder is the user.home the environment gives",
                () -> assertThat(ContainerStartSettings.from(new MockEnvironment().withProperty("user.home",
                        home.toString())).profileFolder()).isEqualTo(home));
        claim("a blank user.home, or none, is no folder known",
                () -> assertThat(Stream.of(new MockEnvironment().withProperty("user.home", "  "), new MockEnvironment())
                        .map(environment -> ContainerStartSettings.from(environment).profileFolder()).toList())
                        .containsExactly(null, null));
        claim("and the settings built with four or five values, as the tests that start the containers build them,"
                        + " know no folder",
                () -> assertThat(List.of(Optional.ofNullable(new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE,
                                cpuImage, WAIT_AT_MOST).profileFolder()),
                        Optional.ofNullable(new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST,
                                "Ubuntu").profileFolder())))
                        .containsExactly(Optional.empty(), Optional.empty()));
    }

    /** The commands a start of the containers runs on Windows with the engine down, in order and whole (ADR-390, ADR-410). */
    private static List<List<String>> theWindowsStart() {
        List<String> check = concat(IN_WSL, ScriptedDocker.ENGINE_CHECK);
        return List.of(check, WSL_START, check, check,
                concat(IN_WSL, ScriptedDocker.COMPOSE_CHECK), concat(IN_WSL, ScriptedDocker.BUILDX_CHECK),
                concat(List.of("wsl", "-u", "root", "--cd", REPOSITORY.toString(), "--exec"),
                        List.of("docker", "compose", "-p", "vespera", "-f", "compose.yaml",
                                "up", "-d", "--build", "--no-recreate")));
    }

    private ContainerStarter starter(ContainerStartSettings settings, ScriptedDocker docker, ContainerHealth health,
            HostSystem host) {
        return new ContainerStarter(settings, docker, health, host, () -> ComposeFiles.in(REPOSITORY),
                folder.resolve("locks"), CHECK_EVERY);
    }

    /** Both containers on this machine, the CPU image, and {@code profile} as the profile folder. */
    private static ContainerStartSettings here(Path profile) {
        return new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST, "", profile);
    }

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> names = Files.list(directory)) {
            return names.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return List.copyOf(whole);
    }

    /** Containers that answer or not as set, noting whether {@code file} was there when they were first asked. */
    private static final class WatchingHealth implements ContainerHealth {

        private final Path file;
        volatile boolean answering;
        boolean fileThereWhenFirstAsked;
        private boolean asked;

        WatchingHealth(Path file, boolean answering) {
            this.file = file;
            this.answering = answering;
        }

        @Override
        public boolean doclingAnswers() {
            if (!asked) {
                asked = true;
                fileThereWhenFirstAsked = file != null && Files.exists(file);
            }
            return answering;
        }

        @Override
        public boolean ollamaAnswers() {
            return answering;
        }
    }
}
