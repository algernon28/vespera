package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
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

    /** The words JavaFX's line about its classes coming from the class path begins with (ADR-360). */
    private static final String UNSUPPORTED_CONFIGURATION = "Unsupported JavaFX configuration";

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
    }

    @Test
    @Story("A command ends the process")
    @DisplayName("A version command exits within a bound, carrying success")
    void aVersionCommandExitsWithinItsBoundWithSuccess() throws Exception {
        Launched launched = launch("--version");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than staying alive",
                () -> assertThat(launched.finished()).isTrue());
        claim("and its exit code is success, so the command's zero reaches the shell too",
                () -> assertThat(launched.exitCode()).isZero());
    }

    /**
     * The jar started with no argument where the window cannot open answers as its command line (ADR-342 §2): one line
     * saying why, then the commands' usage, and picocli's usage code. A platform JavaFX does not have makes the
     * toolkit fail as it starts, as a machine with no display does: measured, {@code Platform.startup} throws within
     * a fraction of a second and the JVM goes on. It runs in a JVM of its own, since a JavaFX toolkit starts once.
     */
    @Test
    @Issue("700")
    @Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
    @Story("A start with no window answers as the command line")
    @DisplayName("Started with no argument where no window can open, the jar says why in one line and prints the commands' usage")
    void aJarThatCannotOpenTheWindowAnswersAsItsCommandLine() throws Exception {
        Launched launched = launchWith(List.of(NO_SUCH_PLATFORM));
        String output = Files.readString(workingDirectory.resolve(OUTPUT), StandardCharsets.UTF_8)
                .replaceAll("\u001B\\[[0-9;]*m", "");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than waiting on a window",
                () -> assertThat(launched.finished()).isTrue());
        claim("it says in one line that the window cannot open, and that the jar answers as its command line instead",
                () -> assertThat(output).contains(NO_WINDOW));
        claim("then prints the commands' usage, as a start of the command line with no argument does",
                () -> assertThat(output).contains("Usage: vespera"));
        claim("and exits with picocli's usage code, as that start does",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    /**
     * The jar's start of the window writes no line of JavaFX's about its class path (ADR-360 §1): the control panel's
     * start drops the record from JavaFX's logger before the toolkit starts, and JavaFX writes it as its start begins,
     * before a platform it does not have makes the toolkit throw, so a start where the window cannot open writes it
     * as a start that opens the window does. Measured without the filter on JavaFX's jars on a plain class path, not in
     * the packaged jar: the line is written, then the toolkit throws. The jar's logging configuration is the JDK's, as
     * the operator's is.
     */
    @Test
    @Issue("723")
    @Link(name = "ADR-360", url = Adr.THE_CONTROL_PANEL_DROPS_JAVAFXS_CLASS_PATH_LINE, type = "adr")
    @Story("A start with no window answers as the command line")
    @DisplayName("Started with no argument, the jar writes no line of JavaFX's about its class path as the toolkit starts")
    void aJarThatCannotOpenTheWindowWritesNoLineOfJavaFxsAboutItsClassPath() throws Exception {
        Launched launched = launchWith(List.of(NO_SUCH_PLATFORM));
        String output = Files.readString(workingDirectory.resolve(OUTPUT), StandardCharsets.UTF_8)
                .replaceAll("\u001B\\[[0-9;]*m", "");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds, having tried the toolkit and said the window"
                        + " cannot open",
                () -> assertThat(List.<Object>of(launched.finished(), output.contains(NO_WINDOW)))
                        .containsExactly(true, true));
        claim("and nothing it wrote holds JavaFX's words about its classes coming from the class path",
                () -> assertThat(output).doesNotContain(UNSUPPORTED_CONFIGURATION));
    }

    /** The start of a line in the console's pattern, {@code HH:mm:ss.SSS LEVEL}, which a line of the jar's own lacks. */
    private static final Pattern IN_THE_CONSOLES_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} [A-Z]+ .*");

    /** What a launch returned with its two streams kept apart. */
    private record Streams(boolean finished, int exitCode, List<String> standardOutput, List<String> standardError) {
    }

    /** As {@link #launch}, standard output and standard error kept in two files, so the stream a line is on is read. */
    private static Streams launchSplit(String... args) throws Exception {
        Path out = Files.createTempFile(workingDirectory, "split", ".out");
        Path err = Files.createTempFile(workingDirectory, "split", ".err");
        ProcessBuilder builder = new ProcessBuilder(
                        Stream.of(
                                        Stream.of(
                                                javaExecutable(),
                                                "-Dspring.docker.compose.enabled=false",
                                                "-Dvespera.sidecars.start=false",
                                                "-Dvespera.sidecars.wait-at-most=1s",
                                                "-Dvespera.working-dir=" + workingDirectory.resolve("split-" + out.getFileName()),
                                                "-jar",
                                                EXECUTABLE_JAR.toString()),
                                        Stream.of(args))
                                .flatMap(each -> each)
                                .toList())
                .redirectOutput(out.toFile())
                .redirectError(err.toFile());
        builder.environment().put("CI", "true");
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
                Files.readAllLines(err, StandardCharsets.UTF_8));
    }

    private static String lastLineOf(List<String> lines) {
        return lines.isEmpty() ? "" : lines.getLast();
    }

    @Test
    @Issue("773")
    @Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
    @Story("A refusal reads as its words alone")
    @DisplayName("A refusal is printed on standard error as its words alone")
    void aRefusalIsPrintedOnStandardErrorAsItsWordsAlone() throws Exception {
        Streams launched = launchSplit("run");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(launched.finished()).isTrue());
        claim("standard error's last line is the refusal, its words whole with nothing before them on the line",
                () -> assertThat(lastLineOf(launched.standardError())).startsWith("vespera run named no root and "));
        claim("standard output holds none of its words",
                () -> assertThat(String.join("\n", launched.standardOutput())).doesNotContain("vespera run named no root"));
        claim("and the exit code is picocli's usage code",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Issue("773")
    @Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
    @Story("A refusal reads as its words alone")
    @DisplayName("The usage of the bare command is printed on standard output as its words alone")
    void theUsageIsPrintedOnStandardOutputAsItsWordsAlone() throws Exception {
        Streams launched = launchSplit("--headless");
        List<String> output = launched.standardOutput();
        int usageStarts = output.indexOf(output.stream().filter(line -> line.startsWith("Usage: vespera")).findFirst().orElse(""));

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(launched.finished()).isTrue());
        claim("standard output holds picocli's usage, its first line beginning Usage: vespera with nothing before it",
                () -> assertThat(usageStarts).isGreaterThanOrEqualTo(0));
        claim("no line of the usage is in the console's pattern, and the usage ends on no blank line",
                () -> {
                    List<String> usage = output.subList(Math.max(usageStarts, 0), output.size());
                    assertThat(usage).noneMatch(line -> IN_THE_CONSOLES_PATTERN.matcher(line).matches());
                    assertThat(lastLineOf(usage)).isNotBlank();
                });
        claim("and the exit code is picocli's usage code",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Issue("773")
    @Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
    @Story("A refusal reads as its words alone")
    @DisplayName("An argument picocli refuses is said on standard error as its words alone")
    void anArgumentPicocliRefusesIsSaidOnStandardErrorAsItsWordsAlone() throws Exception {
        Streams launched = launchSplit("run", "--bogus-option");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(launched.finished()).isTrue());
        claim("picocli's refusal is a line of standard error of its words alone",
                () -> assertThat(launched.standardError()).contains("Unknown option: '--bogus-option'"));
        claim("no line of standard error is in the console's pattern, and standard output holds no word of the refusal",
                () -> {
                    assertThat(launched.standardError()).noneMatch(line -> IN_THE_CONSOLES_PATTERN.matcher(line).matches());
                    assertThat(String.join("\n", launched.standardOutput())).doesNotContain("Unknown option");
                });
        claim("and the exit code is picocli's usage code",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Issue("773")
    @Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
    @Story("A closing line reads as its words alone")
    @DisplayName("A closing line that used to be logged is its words alone, with no converter answering")
    void aClosingLineThatWasLoggedIsItsWordsAlone(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a document.txt"), "a document of the corpus");
        Streams launched = launchSplit("run", root.toString());

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(launched.finished()).isTrue());
        claim("standard error's last line is stage 2's failure line, its words alone with nothing before them",
                () -> assertThat(lastLineOf(launched.standardError()))
                        .startsWith("Stage 2 (extraction) failed and is not recorded as finished"));
        claim("and the exit code is 1", () -> assertThat(launched.exitCode()).isEqualTo(1));
    }

    /** Launches the packaged jar with the given arguments and waits up to the bound for it to end. */
    private static Launched launch(String... args) throws Exception {
        return launchWith(List.of(), args);
    }

    /** Launches the packaged jar with the JVM options and arguments given, its output in {@link #OUTPUT}. */
    private static Launched launchWith(List<String> jvmOptions, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                        Stream.of(
                                        Stream.of(
                                                javaExecutable(),
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