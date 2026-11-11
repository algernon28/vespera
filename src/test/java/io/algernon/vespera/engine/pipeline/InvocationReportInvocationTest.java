package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The invocation report {@code vespera run} writes in the working directory while it runs and when it ends (ADR-362
 * section 3, section 4), reached through whole invocations: what it holds while a step runs, what its last write says,
 * which display it names, and what it never holds.
 *
 * <p>The interval is a tenth of a second, so that a step this fixture runs lasts long enough to be written more than
 * once; the step is held at its progress line while the test looks, from the thread that logged it. The display is
 * answered by a seam this class gives the application, and counted.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, InvocationReportInvocationTest.ScriptedDisplay.class})
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("737")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class InvocationReportInvocationTest {

    private static final String INTERVAL = "100ms";

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String MARKED_FOLDER = MARKER + "-folder";

    private static final List<String> MARKED_READABLE_FILES = List.of(MARKER + "-first.txt", MARKER + "-second.txt");

    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    private static final String SEEDS_RECORDED_LINE = "Stage 5a (seed extraction, seeds recorded): 1 of 1 (100%)";

    private static final String BROKEN_CHECK_DONE = "Stage 1 (byte-level reduction, broken check): 3 of 3 (100%)";

    private static final String CONTENT_REDUNDANCY_STARTS = "Stage 4b (redundancy resolution) starting under run ";

    private static final String LEFT_OUT_ENDING = ".P3A";

    private static final String SECOND_FLOOR = "0.9";

    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope", "code",
            "extraction-failed", "degenerate-output", "redundant-with", "translation-of", "below-threshold");

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndInterval(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.progress-interval", () -> INTERVAL);
        // read by nothing: the property of the JVM is the one that makes a start headless (ADR-362 section 1)
        registry.add("vespera.headless", () -> "true");
    }

    /** The display the application is given, answering as the test says and counting how often it is asked. */
    @TestConfiguration
    static class ScriptedDisplay {

        static final AtomicInteger ASKED = new AtomicInteger();

        static volatile DisplayPresence.Answer answer = new DisplayPresence.Answer(true, "display-variable", null);

        @Bean
        InvocationReport.Display scriptedDisplay() {
            return () -> {
                ASKED.incrementAndGet();
                return answer;
            };
        }
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    @BeforeEach
    void noReportAndAProfileThatLoads() throws IOException {
        removeTheReports();
        profileStore.save(ProfileFixture.profile().build());
        ScriptedDisplay.ASKED.set(0);
        ScriptedDisplay.answer = new DisplayPresence.Answer(true, "display-variable", null);
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void releaseTheLinesAndPutAProfileBack() throws IOException {
        rootLogger.detachAppender(logged);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).detachAppender(logged);
        logged.stop();
        System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
        removeTheReports();
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("The last write says how the invocation ended")
    @DisplayName("After a run that completed, the report says ended, completed, exit code 0, the counts, the closing line the console ended on, and the runs it arrived at")
    void theLastWriteSaysHowTheInvocationEnded(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        JsonNode report = TheReportFile.in(workingDirectory);
        JsonNode invocation = report.get("invocation");
        claim("the report says the invocation has ended, completed, with exit code 0 and an end",
                () -> {
                    assertThat(invocation.get("state").asString()).isEqualTo("ended");
                    assertThat(invocation.get("outcome").asString()).isEqualTo("completed");
                    assertThat(invocation.get("exitCode").asLong()).isZero();
                    assertThat(invocation.get("ended").isNull()).isFalse();
                });
        claim("the report's copy, named by the invocation's id, holds the report's text",
                () -> assertThat(Files.readString(TheReportFile.copyOf(workingDirectory, report), StandardCharsets.UTF_8))
                        .isEqualTo(TheReportFile.textIn(workingDirectory)));
        claim("no step is shown running, and the first sixteen steps are named in the job's order",
                () -> {
                    assertThat(report.get("steps")).hasSize(SIXTEEN_STEPS);
                    for (JsonNode step : report.get("steps")) {
                        assertThat(step.get("state").asString()).isNotEqualTo("running");
                    }
                });
        JsonNode counts = report.get("counts");
        claim("the counts are filled: the occurrences the walk recorded and the one extraction-failed verdict the faulted file earned",
                () -> {
                    assertThat(counts.isNull()).isFalse();
                    assertThat(counts.get("occurrences").asLong()).isEqualTo(3);
                    assertThat(counts.get("verdicts").get("extraction-failed").asLong()).isEqualTo(1);
                    assertThat(counts.get("extractionFaults").get("internal").asLong()).isEqualTo(1);
                    assertThat(counts.get("clusters").asLong()).isPositive();
                });
        claim("the closing line is the line the console ended on, whole, and the next invocation is what the file beside it says",
                () -> {
                    assertThat(report.get("closing").asString()).contains("Next:");
                    assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                            .contains(report.get("closing").asString());
                    assertThat(report.get("next").get("invocation").asString()).isIn("run", "label", "none");
                });
        List<String> ledgerRuns = jdbcTemplate.queryForList("SELECT id FROM run", String.class);
        claim("it lists the runs this invocation arrived at, by stage, each a run of the ledger and none twice",
                () -> {
                    List<String> ids = new ArrayList<>();
                    List<String> stages = new ArrayList<>();
                    report.get("arrivedAt").forEach(arrived -> {
                        ids.add(arrived.get("run").asString());
                        stages.add(arrived.get("stage").asString());
                    });
                    assertThat(ids).isNotEmpty().doesNotHaveDuplicates().isSubsetOf(ledgerRuns);
                    assertThat(stages).doesNotHaveDuplicates();
                });
    }

    @Test
    @Story("The report can be sent to anyone: it holds no text of a document")
    @DisplayName("A marker in every document's text, the converter's message, each cluster's title and a failing step's message occurs nowhere in the report or its copy")
    void theReportHoldsNoTextOfADocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        String completed = TheReportFile.textIn(workingDirectory);
        String completedCopy = Files.readString(
                TheReportFile.copyOf(workingDirectory, TheReportFile.parse(completed)), StandardCharsets.UTF_8);
        Files.writeString(profileStore.file(), MARKER + "-key-written-flat: a value\n");
        cli.run("run", root.toString());
        String failed = TheReportFile.textIn(workingDirectory);

        for (String text : List.of(completed, completedCopy, failed)) {
            for (String forbidden : List.of(
                    MARKER, MARKED_FOLDER, FAULTED, SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE)) {
                claim("the report does not contain '" + forbidden + "', in any letter case, which the invocation was"
                                + " given or read",
                        () -> assertThat(text).doesNotContainIgnoringCase(forbidden));
            }
        }
        claim("the second invocation failed, so the failing step's message was there to leak",
                () -> assertThat(cli.getExitCode()).isNotZero());
    }

    @Test
    @Story("A failed step is a fault by type alone")
    @DisplayName("A profile that does not load fails the census: its faults entry is its type and causes, and no message")
    void aFailedStepIsAFaultByTypeAlone(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        Files.writeString(profileStore.file(), MARKER + "-key-written-flat: a value\n");

        cli.run("run", root.toString());

        claim("the invocation failed", () -> assertThat(cli.getExitCode()).isNotZero());
        JsonNode report = TheReportFile.in(workingDirectory);
        claim("the report says it ended failed, and the census failed with the steps after it not started",
                () -> {
                    assertThat(report.get("invocation").get("outcome").asString()).isEqualTo("failed");
                    assertThat(TheReportFile.step(report, StepNames.CENSUS).get("state").asString()).isEqualTo("failed");
                    assertThat(TheReportFile.step(report, StepNames.BYTE_LEVEL_REDUCTION).get("state").asString())
                            .isEqualTo("not started");
                });
        claim("its faults entry names the step and a class, with causes that are class names, and holds no message",
                () -> {
                    assertThat(report.get("faults")).hasSize(1);
                    JsonNode fault = report.get("faults").get(0);
                    assertThat(fault.get("step").asString()).isEqualTo(StepNames.CENSUS);
                    assertThat(fault.get("type").asString()).matches("[a-z][A-Za-z0-9_.$]*");
                    assertThat(fault.size()).isEqualTo(3);
                    fault.get("causes").forEach(cause -> assertThat(cause.asString()).matches("[a-z][A-Za-z0-9_.$]*"));
                    assertThat(fault.get("causes").size()).isLessThanOrEqualTo(3);
                });
    }

    @Test
    @Story("An invocation refused before the lock leaves the report of the one that holds it")
    @DisplayName("A working directory in use, and arguments picocli refuses: report.json is byte for byte as the holder left it")
    void anInvocationRefusedBeforeTheLockLeavesTheReportAsItWas(@TempDir Path other) throws IOException {
        Path left = other.resolve(InvocationReport.FILE_NAME);
        Files.writeString(left, "{\"left\":\"by the holder\"}\n", StandardCharsets.UTF_8);
        byte[] asTheHolderLeftIt = Files.readAllBytes(left);
        WorkingDirectoryLock.release();
        try {
            new WorkingDirectoryLock().onApplicationEvent(prepared(other, "run", "a-root"));
            Throwable refused = org.assertj.core.api.Assertions.catchThrowable(
                    () -> new WorkingDirectoryLock().onApplicationEvent(prepared(other, "run", "a-root")));

            claim("the second invocation on the working directory is refused as one in use",
                    () -> assertThat(refused).isInstanceOf(WorkingDirectoryInUseException.class));
            claim("the report of the invocation that holds it is byte for byte as the holder left it",
                    () -> assertThat(Files.readAllBytes(left)).isEqualTo(asTheHolderLeftIt));
        } finally {
            WorkingDirectoryLock.release();
        }
        Files.writeString(workingDirectory.resolve(InvocationReport.FILE_NAME), "{\"left\":\"by the holder\"}\n");
        byte[] before = Files.readAllBytes(workingDirectory.resolve(InvocationReport.FILE_NAME));

        cli.run("run", "--bogus-option");

        claim("arguments picocli refuses leave the report as it was, since the command never runs",
                () -> assertThat(Files.readAllBytes(workingDirectory.resolve(InvocationReport.FILE_NAME))).isEqualTo(before));
    }

    private static final int SIXTEEN_STEPS = 16;

    private static org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent prepared(
            Path directory, String... args) {
        return new org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent(
                new org.springframework.boot.bootstrap.DefaultBootstrapContext(),
                new org.springframework.boot.SpringApplication(),
                args,
                new org.springframework.mock.env.MockEnvironment()
                        .withProperty(WorkingDirectoryPreparer.PROPERTY, directory.toString()));
    }

    /** The report and its copies an earlier test left. */
    private static void removeTheReports() throws IOException {
        Files.deleteIfExists(workingDirectory.resolve(InvocationReport.FILE_NAME));
        for (Path copy : TheReportFile.copiesIn(workingDirectory)) {
            Files.deleteIfExists(copy);
        }
    }

    /** Marks every directory, file name and text the invocation is given, and one file the converter faults on. */
    private static void aMarkedCorpus(Path root, Path seeds) throws IOException {
        Path folder = Files.createDirectory(root.resolve(MARKED_FOLDER));
        for (String name : MARKED_READABLE_FILES) {
            Files.writeString(folder.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(folder.resolve(FAULTED), "a file the converter faults on, and " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
    }

    /** Hands each event to {@code onEvent} on the thread that logged it, so a step's thread can be held at one of its lines. */
    private static final class HoldingAppender extends ch.qos.logback.core.UnsynchronizedAppenderBase<ILoggingEvent> {

        private final java.util.function.Consumer<ILoggingEvent> onEvent;

        HoldingAppender(java.util.function.Consumer<ILoggingEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        protected void append(ILoggingEvent event) {
            onEvent.accept(event);
        }
    }
}
