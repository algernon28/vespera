package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What JavaFX's toolkit writes as it starts from the class path, with the control panel's filter installed and without
 * it (ADR-360 §1, §6): without it, one record whose message begins {@code Unsupported JavaFX configuration}, on the
 * logger {@code javafx} at {@code WARNING}, and its line on standard error; with it, neither, while JavaFX's other
 * records still reach the handlers and print. The start without the filter holds the logger's name and the message's
 * first words against the JavaFX the build declares, and is the control that shows the start with it can fail.
 *
 * <p>Each start runs in a JVM of its own, {@link AToolkitStartedForTests}, on JavaFX's Headless platform as the window
 * tests run (ADR-342 §6): a toolkit starts once in a JVM, and which test class started the one in the test JVM is not
 * known. That JVM's {@code java.util.logging} configuration is the test's own, written to a file of its own, so the
 * machine's {@code logging.properties} decides nothing: a console handler on the root logger, at {@code INFO}, writing
 * each record as its level and its message.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("723")
@Link(name = "ADR-360", url = Adr.THE_CONTROL_PANEL_DROPS_JAVAFXS_CLASS_PATH_LINE, type = "adr")
class TheToolkitsStartWritesNoClassPathLineTest {

    /** The words JavaFX's line begins with. */
    private static final String OPENING = "Unsupported JavaFX configuration";

    /** How the line goes on in JavaFX 26.0.2, up to the module it names, as its class holds it. */
    private static final String THE_LINE_UP_TO_ITS_MODULE = OPENING + ": classes were loaded from '";

    /** The logger JavaFX writes the line on. */
    private static final String JAVAFX_LOGGER = "javafx";

    /** The class whose start of the toolkit writes the line, as a resource of JavaFX's jar. */
    private static final String PLATFORM_IMPL = "com/sun/javafx/application/PlatformImpl.class";

    /** How long a JVM of the test's is waited for, generous over a start of the toolkit of well under a second. */
    private static final long BOUND_SECONDS = 90;

    /** {@code java.util.logging}'s configuration for that JVM: the JDK's defaults, and a format of level and message. */
    private static final List<String> LOGGING = List.of(
            "handlers=java.util.logging.ConsoleHandler",
            ".level=INFO",
            "java.util.logging.ConsoleHandler.level=INFO",
            "java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter",
            "java.util.logging.SimpleFormatter.format=%4$s: %5$s%n");

    /** A record a JVM of the test's wrote to its standard output, as its handler on the root logger saw it. */
    private record Seen(String logger, String level, String message) {
    }

    /**
     * What a JVM of the test's did: whether it ended in time and how, whether its toolkit started, the records its
     * handler saw, and the lines of its standard error.
     */
    private record Started(boolean ended, int exitCode, boolean toolkitStarted, List<Seen> records,
            List<String> standardError) {

        List<Seen> ofTheLine() {
            return records.stream().filter(seen -> seen.message().startsWith(OPENING)).toList();
        }
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("Without the filter, the toolkit's start writes JavaFX's line about its class path once, on the javafx logger, as a warning")
    void withoutTheFilterTheToolkitsStartWritesTheLineOnTheJavafxLoggerAtWarning(@TempDir Path folder)
            throws Exception {
        Started started = start(folder, AToolkitStartedForTests.WITHOUT_IT);

        claim("the JVM started the toolkit on the Headless platform and ended within " + BOUND_SECONDS + " seconds",
                () -> assertThat(List.<Object>of(started.ended(), started.exitCode(), started.toolkitStarted()))
                        .containsExactly(true, 0, true));
        claim("its start wrote one record whose message begins with JavaFX's words about its class path, on the logger"
                        + " named javafx, at the level of a warning",
                () -> assertThat(started.ofTheLine())
                        .extracting(Seen::logger, Seen::level)
                        .containsExactly(tuple(JAVAFX_LOGGER, "WARNING")));
        claim("and its line, the warning and JavaFX's words, was on standard error",
                () -> assertThat(started.standardError())
                        .anyMatch(line -> line.startsWith("WARNING: " + THE_LINE_UP_TO_ITS_MODULE)));
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("With the filter installed, the toolkit's start writes no line about its class path, and JavaFX's other records still print")
    void withTheFilterTheToolkitsStartWritesNoLineAndJavaFxsOtherRecordsStillPrint(@TempDir Path folder)
            throws Exception {
        Started started = start(folder, AToolkitStartedForTests.WITH_THE_FILTER);

        claim("the JVM installed the filter, asked for five collections, started the toolkit on the Headless platform"
                        + " and ended within " + BOUND_SECONDS + " seconds",
                () -> assertThat(List.<Object>of(started.ended(), started.exitCode(), started.toolkitStarted()))
                        .containsExactly(true, 0, true));
        claim("no record whose message begins with JavaFX's words about its class path reached a handler of the root"
                + " logger", () -> assertThat(started.ofTheLine()).isEmpty());
        claim("and no line of standard error holds those words",
                () -> assertThat(started.standardError()).noneMatch(line -> line.contains(OPENING)));
        claim("while a warning and an error written on the logger named javafx after the start reached the handler,"
                        + " in that order, as a warning and as severe",
                () -> assertThat(started.records())
                        .filteredOn(seen -> seen.logger().equals(JAVAFX_LOGGER))
                        .containsSubsequence(
                                new Seen(JAVAFX_LOGGER, "WARNING", AToolkitStartedForTests.ANOTHER_WARNING),
                                new Seen(JAVAFX_LOGGER, "SEVERE", AToolkitStartedForTests.AN_ERROR)));
        claim("and both printed on standard error",
                () -> assertThat(started.standardError()).containsSubsequence(
                        "WARNING: " + AToolkitStartedForTests.ANOTHER_WARNING,
                        "SEVERE: " + AToolkitStartedForTests.AN_ERROR));
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("The words the filter drops are how a string of the class JavaFX starts its toolkit by begins")
    void theLinesOpeningWordsAreAStringOfTheClassJavaFxStartsBy() throws Exception {
        byte[] bytes;
        try (InputStream in = Platform.class.getClassLoader().getResourceAsStream(PLATFORM_IMPL)) {
            bytes = in == null ? new byte[0] : in.readAllBytes();
        }
        List<String> strings = new ArrayList<>();
        if (bytes.length > 0) {
            for (PoolEntry entry : ClassFile.of().parse(bytes).constantPool()) {
                if (entry instanceof StringEntry string) {
                    strings.add(string.stringValue());
                }
            }
        }

        claim("the class JavaFX starts its toolkit by was read from the JavaFX this build declares, and holds strings",
                () -> assertThat(strings).isNotEmpty());
        claim("and one of them is JavaFX's line about its class path, beginning with the words the filter drops and"
                        + " going on to the module the classes were loaded from",
                () -> assertThat(strings).anyMatch(string -> string.startsWith(THE_LINE_UP_TO_ITS_MODULE)));
    }

    /** Starts {@link AToolkitStartedForTests} in a JVM of its own with {@code mode}, and reads what it wrote. */
    private static Started start(Path folder, String mode) throws Exception {
        Path logging = Files.write(folder.resolve("logging.properties"), LOGGING, StandardCharsets.ISO_8859_1);
        Path output = folder.resolve("output.txt");
        Path errors = folder.resolve("errors.txt");
        Process process = new ProcessBuilder(
                        ProcessHandle.current().info().command()
                                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()),
                        "--enable-native-access=ALL-UNNAMED",
                        "-Dglass.platform=Headless",
                        "-Dprism.order=sw",
                        "-Djava.util.logging.config.file=" + logging,
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        // The format's level is the level's name in the JVM's locale: WARNUNG in German.
                        "-Duser.language=en",
                        "-Duser.country=US",
                        "-cp", System.getProperty("java.class.path"),
                        AToolkitStartedForTests.class.getName(), mode)
                .redirectOutput(output.toFile())
                .redirectError(errors.toFile())
                .start();
        boolean ended = process.waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
        if (!ended) {
            process.destroyForcibly();
            process.waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
        }
        List<String> out = Files.readAllLines(output, StandardCharsets.UTF_8);
        List<Seen> records = out.stream()
                .filter(line -> line.startsWith(AToolkitStartedForTests.RECORD))
                .map(line -> line.substring(AToolkitStartedForTests.RECORD.length()).split("\t", 3))
                .map(fields -> new Seen(fields[0], fields[1], fields.length > 2 ? fields[2] : ""))
                .toList();
        return new Started(ended, ended ? process.exitValue() : -1, out.contains(AToolkitStartedForTests.STARTED),
                records, Files.readAllLines(errors, StandardCharsets.UTF_8));
    }
}
