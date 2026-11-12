package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The process ends when the command does, and its exit code is the command's (ADR-141).
 *
 * <p>This cannot be observed in-process: the defect was a non-daemon thread a dependency starts,
 * which an in-process assertion would share rather than expose. It is pinned by launching the
 * packaged jar as a subprocess and asserting it exits within a bound for two commands — a rejected
 * one (picocli's usage code) and {@code --version} (success) — so both outcomes of the command
 * reach the shell. It needs no Docker daemon — the compose lifecycle is switched off so the jar
 * starts nothing — but it is an integration test all the same, because the packaged jar exists only
 * after {@code package}.
 */
@Epic("Architecture")
@Feature("Process exit")
@Issue("269")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
class CliExitIT {

    /**
     * How long a command may take before the test calls it hung. The defect this pins was
     * indefinite, so any finite bound separates a correct exit from the bug; the number is generous
     * over a real startup of a few seconds.
     */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** A temp working directory, so the launched jar writes its database nowhere the repo tracks. */
    @TempDir
    static Path workingDirectory;

    /** The file a launch's merged output is written to, in the working directory. */
    private static final String OUTPUT = "cli-exit-message.log";

    /** A glass platform JavaFX does not have, so that its toolkit fails as it starts, as with no display. */
    private static final String NO_SUCH_PLATFORM = "-Dglass.platform=NoSuchPlatform";

    /** What the line says before the toolkit's reason, as ADR-342 §2 gives it. */
    private static final String NO_WINDOW =
            "the control panel cannot open a window here, so the jar answers as its command line: ";

    /** What a launch returned: whether it exited within the bound, and its code. */
    private record Launched(boolean finished, int exitCode) {
    }

    @Test
    @Story("A command ends the process")
    @DisplayName("A rejected command exits within a bound, carrying picocli's usage code")
    void aRejectedCommandExitsWithinItsBoundWithItsUsageCode() throws Exception {
        claim("the packaged jar exists, since this test launches it and needs verify to have built it",
                () -> assertThat(Files.exists(EXECUTABLE_JAR)).isTrue());

        Launched launched = launch("--bogus-option");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than staying alive",
                () -> assertThat(launched.finished()).isTrue());
        claim("and its exit code is picocli's usage code for the rejected option, not success",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
        theJarsOwnLinesReadAsTheirWordsAloneOnTheStreamTheyAreFor();
    }

    /** The start of a line in the console's pattern, {@code HH:mm:ss.SSS LEVEL}, which a line of the jar's own lacks. */
    private static final Pattern IN_THE_CONSOLES_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} [A-Z]+ .*");

    /** What a launch returned with its two streams kept apart. */
    private record Streams(
            boolean finished, int exitCode, List<String> standardOutput, List<String> standardError, Path workingDirectory) {
        String lastErrorLine() {
            return standardError.isEmpty() ? "" : standardError.getLast();
        }
    }

    /**
     * The jar's own lines are logged on the operator logger and read, on the stream they were printed on, as their
     * words alone (ADR-364 section 4): a refusal and picocli's refusal of an argument, with its usage, on standard
     * error; the usage of the bare command on standard output; and a closing line that used to be logged in the console's
     * pattern. Held here, among this class's launches, because the suite holds at most seven integration tests and
     * 700 test methods (ADR-368). The usage is not compared with picocli's own rendering, which needs the Spring beans
     * the commands are built from.
     */
    private void theJarsOwnLinesReadAsTheirWordsAloneOnTheStreamTheyAreFor() throws Exception {
        Streams noRoot = launchSplit("run");
        claim("a refusal is the last line of standard error, its words whole with nothing before them, none of it on"
                        + " standard output, and the exit code is picocli's usage code",
                () -> {
                    assertThat(noRoot.finished()).isTrue();
                    assertThat(noRoot.lastErrorLine())
                            .startsWith("vespera run named no root and ")
                            .endsWith("A root is never guessed, because a census of the wrong tree reports success.");
                    assertThat(String.join("\n", noRoot.standardOutput())).doesNotContain("vespera run named no root");
                    assertThat(noRoot.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
                });
        claim("the banner is logged, so vespera.log in that working directory holds Spring Boot's banner line, one"
                        + " event at INFO whichever logger Spring Boot logs it on (ADR-364 section 5)",
                () -> assertThat(Files.readAllLines(noRoot.workingDirectory().resolve("logs").resolve("vespera.log")))
                        .anyMatch(line -> line.contains(":: Spring Boot ::")));

        Streams bogus = launchSplit("run", "--bogus-option");
        claim("picocli's refusal of an argument and the usage it writes with it are on standard error as their words"
                        + " alone, and standard output holds none of them",
                () -> {
                    assertThat(bogus.standardError()).contains("Unknown option: '--bogus-option'");
                    assertThat(bogus.standardError()).anyMatch(line -> line.startsWith("Usage: vespera run"));
                    assertThat(bogus.standardError()).noneMatch(line -> IN_THE_CONSOLES_PATTERN.matcher(line).matches());
                    assertThat(bogus.standardOutput()).noneMatch(line -> line.startsWith("Usage: vespera run"));
                    assertThat(String.join("\n", bogus.standardOutput())).doesNotContain("Unknown option");
                });

        Streams bare = launchSplit("--headless");
        List<String> output = bare.standardOutput();
        int usageStarts = output.indexOf(output.stream().filter(line -> line.startsWith("Usage: vespera")).findFirst().orElse(""));
        claim("the usage of the bare command is on standard output from a first line beginning Usage: vespera, naming"
                        + " the commands, in no line the console's pattern, and ends on no blank line",
                () -> {
                    assertThat(usageStarts).isGreaterThanOrEqualTo(0);
                    List<String> usage = output.subList(usageStarts, output.size());
                    assertThat(usage).noneMatch(line -> IN_THE_CONSOLES_PATTERN.matcher(line).matches());
                    assertThat(usage.getLast()).isNotBlank();
                    for (String command : List.of("run", "label", "publish", "serve")) {
                        assertThat(usage).anyMatch(line -> line.strip().startsWith(command));
                    }
                    assertThat(bare.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
                });

        Path root = Files.createDirectories(workingDirectory.resolve("a-corpus-with-a-document"));
        Files.writeString(root.resolve("a document.txt"), "a document of the corpus");
        Streams stageTwo = launchSplit("run", root.toString());
        claim("a closing line that used to be logged in the console's pattern, stage 2's failure with no converter"
                        + " answering, is the last line of standard error as its words alone, and the exit code is 1",
                () -> {
                    assertThat(stageTwo.lastErrorLine())
                            .startsWith("Stage 2 (extraction) failed and is not recorded as finished")
                            .endsWith("and run the same command again.");
                    assertThat(stageTwo.exitCode()).isEqualTo(1);
                });
        Path logs = stageTwo.workingDirectory().resolve("logs");
        List<Path> outputFiles;
        try (Stream<Path> each = Files.list(logs)) {
            outputFiles = each.filter(path -> path.getFileName().toString().matches("output-\\d{8}T\\d{6}Z(-\\d+)?\\.log"))
                    .toList();
        }
        claim("the invocation wrote one output file in its logs folder, holding every line of its two streams, the"
                        + " banner and the closing line among them, the closing line as its words alone (ADR-364 section 2)",
                () -> {
                    assertThat(outputFiles).hasSize(1);
                    List<String> inTheFile = Files.readAllLines(outputFiles.getFirst(), StandardCharsets.UTF_8);
                    assertThat(inTheFile).anyMatch(line -> line.contains(":: Spring Boot ::"));
                    assertThat(inTheFile).containsAll(stageTwo.standardOutput().stream()
                            .filter(line -> !line.isBlank()).toList());
                    assertThat(inTheFile).containsAll(stageTwo.standardError().stream()
                            .filter(line -> !line.isBlank()).toList());
                    assertThat(inTheFile).contains(stageTwo.lastErrorLine());
                });
    }

    /** As {@link #launch}, standard output and standard error kept in two files, so the stream a line is on is read. */
    private static Streams launchSplit(String... args) throws Exception {
        Path out = Files.createTempFile(workingDirectory, "split", ".out");
        Path err = Files.createTempFile(workingDirectory, "split", ".err");
        Path launchedIn = workingDirectory.resolve("split-" + out.getFileName());
        ProcessBuilder builder = new ProcessBuilder(
                        Stream.of(
                                        Stream.of(
                                                javaExecutable(),
                                                "--enable-native-access=ALL-UNNAMED",
                                                "-Dspring.docker.compose.enabled=false",
                                                "-Dvespera.sidecars.start=false",
                                                "-Dvespera.sidecars.wait-at-most=1s",
                                                "-Dvespera.working-dir=" + launchedIn,
                                                "-jar",
                                                EXECUTABLE_JAR.toString()),
                                        Stream.of(args))
                                .flatMap(each -> each)
                                .toList())
                .redirectOutput(out.toFile())
                .redirectError(err.toFile());
        builder.environment().put("CI", "true");
        builder.environment().putIfAbsent("DISPLAY", ":99");
        Process process = builder.start();
        boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        return new Streams(
                finished,
                finished ? process.exitValue() : -1,
                Files.readAllLines(out, StandardCharsets.UTF_8),
                Files.readAllLines(err, StandardCharsets.UTF_8),
                launchedIn);
    }

    /**
     * The jar started with no argument where the window cannot open answers as its command line (ADR-342 §2, ADR-362
     * §2): one line saying why, then the commands' usage, and picocli's usage code. With a display present, a platform
     * JavaFX does not have makes the toolkit fail as it starts, as a machine whose toolkit cannot start does: measured,
     * {@code Platform.startup} throws within a fraction of a second and the JVM goes on. It runs in a JVM of its own,
     * since a JavaFX toolkit starts once.
     */
    @Test
    @Issue("700")
    @Issue("736")
    @Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
    @Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
    @Story("A start with no window answers as the command line")
    @DisplayName("Started with no argument where the toolkit cannot start, the jar says why in one line and prints the commands' usage")
    void aJarThatCannotOpenTheWindowAnswersAsItsCommandLine() throws Exception {
        assumeTrue(displayIsPresentHere(), "no display is present on this machine, so the toolkit is not tried");
        Launched launched = launchWith(List.of(NO_SUCH_PLATFORM), true);
        String output = output();

        claim("with a display present, the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than waiting on a window",
                () -> assertThat(launched.finished()).isTrue());
        claim("it says in one line that the window cannot open, and that the jar answers as its command line instead",
                () -> assertThat(output).contains(NO_WINDOW));
        claim("the reason is the toolkit's, not that no display is present",
                () -> assertThat(output).doesNotContain(NO_WINDOW + "no display"));
        claim("then prints the commands' usage, as a start of the command line with no argument does",
                () -> assertThat(output).contains("Usage: vespera"));
        claim("and exits with picocli's usage code, as that start does",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    /** What the launched jar wrote, merged, without colour codes. */
    private static String output() throws Exception {
        return Files.readString(workingDirectory.resolve(OUTPUT), StandardCharsets.UTF_8)
                .replaceAll("\u001B\\[[0-9;]*m", "");
    }

    /**
     * Whether the launch below has a display: on Linux it sets {@code DISPLAY} itself, so always; elsewhere what
     * {@link DisplayPresence#ofThisProcess()} answers, since the launch cannot make a window station visible.
     */
    private static boolean displayIsPresentHere() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("linux")
                || DisplayPresence.ofThisProcess().present();
    }

    /** Launches the packaged jar with the given arguments and waits up to the bound for it to end. */
    private static Launched launch(String... args) throws Exception {
        return launchWith(List.of(), true, args);
    }

    /** Launches the packaged jar with the JVM options and arguments given, its output in {@link #OUTPUT}. */
    private static Launched launchWith(List<String> jvmOptions, boolean displayPresent, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                        Stream.of(
                                        Stream.of(
                                                javaExecutable(),
                                                "--enable-native-access=ALL-UNNAMED",
                                                "-Dspring.docker.compose.enabled=false",
                                                "-Dvespera.sidecars.start=false",
                                                "-Dvespera.working-dir=" + workingDirectory),
                                        jvmOptions.stream(),
                                        Stream.of("-jar", EXECUTABLE_JAR.toString()),
                                        Stream.of(args))
                                .flatMap(each -> each)
                                .toList())
                .redirectErrorStream(true)
                .redirectOutput(workingDirectory.resolve(OUTPUT).toFile());
        // The jar opens no browser on the machine running the tests (ADR-266 section 2).
        builder.environment().put("CI", "true");
        // On Linux the launch decides whether a display is present: DISPLAY set to a non-blank value, or neither variable.
        if (displayPresent) {
            builder.environment().putIfAbsent("DISPLAY", ":99");
        } else {
            builder.environment().remove("DISPLAY");
            builder.environment().remove("WAYLAND_DISPLAY");
        }
        Process process = builder.start();

        boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        return new Launched(finished, finished ? process.exitValue() : -1);
    }

    /** The Java binary running this test, so the launched jar runs on the Java the build did. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseThrow();
    }
}