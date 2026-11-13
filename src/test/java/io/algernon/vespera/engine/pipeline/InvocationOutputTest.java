package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Each invocation's output file (ADR-364 sections 2, 3 and 6): one sink to a logger context, written by three
 * appenders, holding what was logged before the invocation began and writing each line as it comes after; and the
 * output files more than fourteen days older than the invocation deleted as it begins.
 *
 * <p>The sink is built on a logger context of the test's own, configured as {@code logback-spring.xml} configures the
 * jar's, so that no test begins the shared test context (ADR-364 section 3).
 */
@Epic("Architecture")
@Feature("Logging")
@Issue("775")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class InvocationOutputTest {

    /** The console's pattern, which two of the three appenders write in. */
    private static final String CONSOLES_PATTERN = "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n";

    /** The id an invocation begins under in these tests: 2026-11-10 at noon, UTC. */
    private static final String THE_ID = "20261110T120000Z";

    /** The id of an invocation 14 days and one second before {@link #THE_ID}: its file is deleted. */
    private static final String FOURTEEN_DAYS_AND_A_SECOND_BEFORE = "20261027T115959Z";

    /** The id of an invocation exactly 14 days before {@link #THE_ID}: its file is kept. */
    private static final String EXACTLY_FOURTEEN_DAYS_BEFORE = "20261027T120000Z";

    /** The id of an invocation one day before {@link #THE_ID}: its file is kept. */
    private static final String ONE_DAY_BEFORE = "20261109T120000Z";

    /** An invocation of the same second as {@link #THE_ID}, with its suffix: its file is kept. */
    private static final String THE_SAME_SECOND_WITH_A_SUFFIX = THE_ID + "-2";

    /** An invocation far older than fourteen days, whose file the deletion refuses. */
    private static final String FAR_OLDER_AND_REFUSED = "20250101T000000Z";

    @TempDir
    Path workingDirectory;

    private final LoggerContext context = new LoggerContext();

    @AfterEach
    void stopTheContext() {
        context.stop();
    }

    @Test
    @Story("Each invocation's output is a file of its own")
    @DisplayName("The lines of the three appenders stand in one file in the order logged, those logged before the invocation began first, each written as it is logged")
    void theLinesOfTheThreeAppendersStandInOneFileInTheOrderLogged() throws IOException {
        configuredAsTheJarIs();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger hikari = context.getLogger("com.zaxxer.hikari");
        Logger operator = context.getLogger(OperatorLines.LOGGER_NAME);

        root.info("held one");
        hikari.info("hikari below warn");
        hikari.warn("hikari at warn");
        operator.info("the operator's held line");
        claim("before the invocation begins no output file is written: what is logged is held",
                () -> assertThat(outputFiles()).isEmpty());

        InvocationOutput sink = InvocationOutput.in(context);
        sink.begin(THE_ID);
        Path file = logs().resolve("output-" + THE_ID + ".log");
        List<Boolean> inTheFileOnceLogged = new ArrayList<>();
        root.info("after one");
        inTheFileOnceLogged.add(Files.readString(file, StandardCharsets.UTF_8).contains("after one"));
        operator.warn("the operator's refusal");
        inTheFileOnceLogged.add(Files.readString(file, StandardCharsets.UTF_8).contains("the operator's refusal"));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        claim("the file named by the id holds the lines held first, then those logged after the beginning, in the"
                        + " order logged: the root's and the library logger's in the console's pattern, the operator's"
                        + " as their words alone, and the library logger's line below WARN in none",
                () -> {
                    assertThat(lines).hasSize(5);
                    assertThat(lines.get(0)).matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO .* - held one$");
                    assertThat(lines.get(1)).matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} WARN .* - hikari at warn$");
                    assertThat(lines.get(2)).isEqualTo("the operator's held line");
                    assertThat(lines.get(3)).matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO .* - after one$");
                    assertThat(lines.get(4)).isEqualTo("the operator's refusal");
                    assertThat(String.join("\n", lines)).doesNotContain("hikari below warn");
                });
        claim("each line logged after the beginning is in the file, read by another stream, before the next is logged,"
                        + " with no flush or stop called",
                () -> assertThat(inTheFileOnceLogged).containsExactly(true, true));
        claim("a second beginning of the same sink is refused, and one output file stands",
                () -> {
                    assertThatThrownBy(() -> sink.begin(ONE_DAY_BEFORE)).isInstanceOf(IllegalStateException.class);
                    assertThat(outputFiles()).containsExactly("output-" + THE_ID + ".log");
                });
    }

    @Test
    @Story("Each invocation's output is a file of its own")
    @DisplayName("Output files more than fourteen days older than the invocation are deleted as it begins, one that cannot be deleted is warned of, and nothing else is touched")
    void outputFilesMoreThanFourteenDaysOlderThanTheInvocationAreDeleted() throws IOException {
        Files.createDirectories(logs());
        for (String id : List.of(FOURTEEN_DAYS_AND_A_SECOND_BEFORE, EXACTLY_FOURTEEN_DAYS_BEFORE, ONE_DAY_BEFORE,
                THE_SAME_SECOND_WITH_A_SUFFIX, FAR_OLDER_AND_REFUSED)) {
            Files.writeString(logs().resolve("output-" + id + ".log"), "a line of " + id + "\n");
        }
        List<String> notOutputFiles = List.of(
                "output-notes.log", "output-20260101T000000Z.txt", "report-20250101T000000Z.json",
                "vespera.2025-01-01.0.log");
        for (String name : notOutputFiles) {
            Files.writeString(logs().resolve(name), "kept\n");
        }
        Files.createDirectories(logs().resolve("output-20250101T000000Z.log.d"));
        Path aFolderNamedAsAnOutputFile = Files.createDirectories(logs().resolve("output-20250102T000000Z.log"));
        Path refused = logs().resolve("output-" + FAR_OLDER_AND_REFUSED + ".log");
        ListAppender<ILoggingEvent> warned = new ListAppender<>();
        warned.start();
        Logger sinksLogger = (Logger) LoggerFactory.getLogger(InvocationOutput.class);
        sinksLogger.addAppender(warned);
        try {
            InvocationOutput sink = new InvocationOutput(logs(), file -> {
                if (file.equals(refused)) {
                    throw new AccessDeniedException(file.toString());
                }
                Files.delete(file);
            });

            sink.begin(THE_ID);
        } finally {
            sinksLogger.detachAppender(warned);
        }

        claim("the file 14 days and one second older is deleted; the one exactly 14 days older, the one a day older and"
                        + " the one of the same second with its suffix are kept, beside the invocation's own",
                () -> assertThat(outputFiles()).containsExactlyInAnyOrder(
                        "output-" + EXACTLY_FOURTEEN_DAYS_BEFORE + ".log", "output-" + ONE_DAY_BEFORE + ".log",
                        "output-" + THE_SAME_SECOND_WITH_A_SUFFIX + ".log", "output-" + FAR_OLDER_AND_REFUSED + ".log",
                        "output-20250102T000000Z.log", "output-notes.log", "output-" + THE_ID + ".log"));
        claim("a name that does not read as an output file's, a report's copy, a rolled vespera.log and a folder named as"
                        + " an output file are kept, however old",
                () -> {
                    for (String name : notOutputFiles) {
                        assertThat(logs().resolve(name)).isRegularFile();
                    }
                    assertThat(aFolderNamedAsAnOutputFile).isDirectory();
                });
        claim("the file the deletion refused stands, and is warned of once, naming it and the exception's class",
                () -> {
                    assertThat(refused).isRegularFile();
                    assertThat(warned.list)
                            .filteredOn(event -> event.getLevel() == Level.WARN)
                            .singleElement()
                            .satisfies(event -> assertThat(event.getFormattedMessage())
                                    .contains(refused.getFileName().toString())
                                    .contains(AccessDeniedException.class.getName()));
                });
    }

    /** The three appenders and the two loggers of {@code logback-spring.xml}, on this test's own context. */
    private void configuredAsTheJarIs() {
        context.reset();
        InvocationOutputAppender output = appender("OUTPUT", CONSOLES_PATTERN, null);
        InvocationOutputAppender outputWarn = appender("OUTPUT_WARN", CONSOLES_PATTERN, Level.WARN);
        InvocationOutputAppender outputOperator = appender("OUTPUT_OPERATOR", "%msg%n", null);
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        root.addAppender(output);
        Logger hikari = context.getLogger("com.zaxxer.hikari");
        hikari.setAdditive(false);
        hikari.addAppender(outputWarn);
        Logger operator = context.getLogger(OperatorLines.LOGGER_NAME);
        operator.setLevel(Level.INFO);
        operator.setAdditive(false);
        operator.addAppender(outputOperator);
    }

    private InvocationOutputAppender appender(String name, String pattern, Level threshold) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();
        InvocationOutputAppender appender = new InvocationOutputAppender();
        appender.setContext(context);
        appender.setName(name);
        appender.setEncoder(encoder);
        appender.setFolder(logs().toString());
        if (threshold != null) {
            ThresholdFilter filter = new ThresholdFilter();
            filter.setLevel(threshold.toString());
            filter.start();
            appender.addFilter(filter);
        }
        appender.start();
        return appender;
    }

    private Path logs() {
        return workingDirectory.resolve("logs");
    }

    /** The names of the regular files and folders in {@code logs} whose name starts {@code output-} and ends {@code .log}. */
    private List<String> outputFiles() throws IOException {
        if (!Files.isDirectory(logs())) {
            return List.of();
        }
        try (Stream<Path> each = Files.list(logs())) {
            return each.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("output-") && name.endsWith(".log"))
                    .sorted()
                    .toList();
        }
    }
}
