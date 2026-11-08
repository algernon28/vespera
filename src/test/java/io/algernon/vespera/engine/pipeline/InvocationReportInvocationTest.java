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
    }

    @AfterEach
    void releaseTheLinesAndPutAProfileBack() throws IOException {
        rootLogger.detachAppender(logged);
        logged.stop();
        System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
        removeTheReports();
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("While a step runs, the report names it and how far it has got, and is written again")
    @DisplayName("During seed extraction, report.json and its copy hold the same text: running, this process's id, the step running with its progress line, and a later write later")
    void aRunWritesTheReportAndItsCopyAsItGoes(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        AtomicBoolean lineLogged = new AtomicBoolean();
        List<JsonNode> seen = new CopyOnWriteArrayList<>();
        HoldingAppender holding = new HoldingAppender(event -> {
            if (!SEEDS_RECORDED_LINE.equals(event.getFormattedMessage()) || !lineLogged.compareAndSet(false, true)) {
                return;
            }
            JsonNode first = TheReportFile.awaitWithItsCopy(workingDirectory, report ->
                    "running".equals(report.get("invocation").get("state").asString())
                            && "running".equals(TheReportFile.step(report, StepNames.SEED_EXTRACTION).get("state").asString())
                            && !TheReportFile.step(report, StepNames.SEED_EXTRACTION).get("progress").isNull());
            if (first == null) {
                return;
            }
            seen.add(first);
            String firstWrittenAt = first.get("invocation").get("writtenAt").asString();
            JsonNode later = TheReportFile.awaitWithItsCopy(workingDirectory, report ->
                    !firstWrittenAt.equals(report.get("invocation").get("writtenAt").asString()));
            if (later != null) {
                seen.add(later);
            }
        });
        holding.setContext(rootLogger.getLoggerContext());
        holding.start();
        rootLogger.addAppender(holding);
        try {
            cli.run("run", root.toString());
        } finally {
            rootLogger.detachAppender(holding);
            holding.stop();
        }

        claim("stage 5a logged its progress line, so there was a moment its step was running and held there",
                () -> assertThat(lineLogged).isTrue());
        claim("while the step was held, report.json and logs/report-<id>.json held the same text, with the step running",
                () -> assertThat(seen).isNotEmpty());
        JsonNode first = seen.getFirst();
        claim("the report says the invocation is running, is the process's own, and names the command",
                () -> {
                    assertThat(first.get("invocation").get("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
                    assertThat(first.get("invocation").get("command").asString()).isEqualTo("run");
                    assertThat(first.get("invocation").get("ended").isNull()).isTrue();
                    assertThat(first.get("invocation").get("exitCode").isNull()).isTrue();
                });
        claim("the running step carries the progress line it had just logged, with its counter",
                () -> {
                    JsonNode progress = TheReportFile.step(first, StepNames.SEED_EXTRACTION).get("progress");
                    assertThat(progress.get("line").asString()).isEqualTo(SEEDS_RECORDED_LINE);
                    assertThat(progress.get("done").asLong()).isEqualTo(1);
                    assertThat(progress.get("total").asLong()).isEqualTo(1);
                });
        claim("and while the step was still held a later write came, with a later writtenAt: it is rewritten every interval",
                () -> assertThat(seen).hasSize(2));
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
    @Story("The report says whether a display is present, and that nothing was opened")
    @DisplayName("With the display seam answering present, answering none and with the headless property set, display is as the seam gave it, asked once for the invocation, and opened is null in every write")
    void theDisplayIsAskedOnceAndOpenedIsNullUntilTheOpenersGo(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve(MARKER + ".txt"), "a text");

        ScriptedDisplay.answer = new DisplayPresence.Answer(true, "display-variable", null);
        cli.run("run", root.toString());
        JsonNode present = TheReportFile.in(workingDirectory);
        int askedForPresent = ScriptedDisplay.ASKED.get();

        ScriptedDisplay.ASKED.set(0);
        ScriptedDisplay.answer = new DisplayPresence.Answer(false, "no-display-variable", null);
        cli.run("run", root.toString());
        JsonNode none = TheReportFile.in(workingDirectory);
        int askedForNone = ScriptedDisplay.ASKED.get();

        ScriptedDisplay.ASKED.set(0);
        System.setProperty(DisplayPresence.HEADLESS_PROPERTY, "");
        cli.run("run", root.toString());
        JsonNode headless = TheReportFile.in(workingDirectory);
        int askedForHeadless = ScriptedDisplay.ASKED.get();

        claim("a run with the seam answering present names it, and asked the seam once for the invocation",
                () -> {
                    assertThat(present.get("display").get("present").asBoolean()).isTrue();
                    assertThat(present.get("display").get("reason").asString()).isEqualTo("display-variable");
                    assertThat(askedForPresent).isEqualTo(1);
                });
        claim("a run with the seam answering none names that, asked once",
                () -> {
                    assertThat(none.get("display").get("present").asBoolean()).isFalse();
                    assertThat(none.get("display").get("reason").asString()).isEqualTo("no-display-variable");
                    assertThat(askedForNone).isEqualTo(1);
                });
        claim("a run with the JVM's headless property set is none for the reason headless, and the system is not asked",
                () -> {
                    assertThat(headless.get("display").get("present").asBoolean()).isFalse();
                    assertThat(headless.get("display").get("reason").asString()).isEqualTo("headless");
                    assertThat(askedForHeadless).isZero();
                });
        for (JsonNode report : List.of(present, none, headless)) {
            claim("opened is null in the report, since the openers still open the pages",
                    () -> assertThat(report.has("opened") && report.get("opened").isNull()).isTrue());
        }
    }

    @Test
    @Story("The report says whether a display is present, and that nothing was opened")
    @DisplayName("With VESPERA_HEADLESS in the environment and vespera.headless in configuration and the seam answering present, display.present is true; with the JVM property set, display.reason is headless")
    void theHeadlessPropertyIsReadFromTheJvmAndNotFromTheEnvironment(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve(MARKER + ".txt"), "a text");
        ScriptedDisplay.answer = new DisplayPresence.Answer(true, "window-server", null);

        // vespera.headless is set to true in this class's configuration (see directories); no environment variable of
        // that name can be set in this JVM, and none is read by the jar, so the configuration stands for both
        cli.run("run", root.toString());
        JsonNode configured = TheReportFile.in(workingDirectory);
        System.setProperty(DisplayPresence.HEADLESS_PROPERTY, "true");
        cli.run("run", root.toString());
        JsonNode property = TheReportFile.in(workingDirectory);

        claim("configuration naming vespera.headless changes nothing: a display is present as the seam says",
                () -> {
                    assertThat(configured.get("display").get("present").asBoolean()).isTrue();
                    assertThat(configured.get("display").get("reason").asString()).isEqualTo("window-server");
                });
        claim("the JVM property alone makes the start headless",
                () -> {
                    assertThat(property.get("display").get("present").asBoolean()).isFalse();
                    assertThat(property.get("display").get("reason").asString()).isEqualTo("headless");
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
    @Story("A step that ends at a gate is shown stopped there, with the key it wants")
    @DisplayName("With the boilerplate floor unset, stage 4a's step is in gates with boilerplateDocumentFrequencyFloor and document-frequency.html")
    void aStepStoppedAtAGateNamesTheKeyItWants(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so gate 3 is open")
                .build());

        cli.run("run", root.toString());

        claim("the invocation reported success: a value it requires and does not have ends the run there",
                () -> assertThat(cli.getExitCode()).isZero());
        JsonNode report = TheReportFile.in(workingDirectory);
        claim("the redundancy signature step, whose gate is the boilerplate floor, is shown stopped at a gate",
                () -> assertThat(TheReportFile.step(report, StepNames.REDUNDANCY_SIGNATURE).get("state").asString())
                        .isEqualTo("stopped at a gate"));
        claim("it is in gates with the key it wants and the analysis report that key is read off",
                () -> {
                    JsonNode gate = null;
                    for (JsonNode each : report.get("gates")) {
                        if (StepNames.REDUNDANCY_SIGNATURE.equals(each.get("step").asString())) {
                            gate = each;
                        }
                    }
                    assertThat(gate).isNotNull();
                    assertThat(gate.get("key").asString()).isEqualTo("boilerplateDocumentFrequencyFloor");
                    assertThat(gate.get("analysisReports")).hasSize(1);
                    assertThat(gate.get("analysisReports").get(0).asString()).isEqualTo("document-frequency.html");
                });
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
    @Story("A refusal after the lock is written with its line")
    @DisplayName("A run that names no root is refused: outcome refused, the refusal line in refusals and in closing")
    void aRefusalAfterTheLockIsWrittenWithItsLine() {
        cli.run("run");

        claim("the invocation was refused with a usage code", () -> assertThat(cli.getExitCode()).isNotZero());
        JsonNode report = TheReportFile.in(workingDirectory);
        claim("the report says the invocation was refused, and ended",
                () -> {
                    assertThat(report.get("invocation").get("outcome").asString()).isEqualTo("refused");
                    assertThat(report.get("invocation").get("state").asString()).isEqualTo("ended");
                    assertThat(report.get("invocation").get("exitCode").asLong()).isEqualTo(cli.getExitCode());
                });
        claim("the refusal line the jar printed is in refusals, whole, and is the closing line",
                () -> {
                    assertThat(report.get("refusals")).hasSize(1);
                    assertThat(report.get("refusals").get(0).asString()).startsWith("vespera run named no root");
                    assertThat(report.get("closing").asString()).isEqualTo(report.get("refusals").get(0).asString());
                });
        claim("and a copy of the refusal is in logs",
                () -> assertThat(TheReportFile.copyOf(workingDirectory, report)).isRegularFile());
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

    @Test
    @Story("The report counts what the step removing occurrences has removed, by kind")
    @DisplayName("Extraction removing a refused file and one of page headers alone: removing holds three survivors of five and the verdicts by kind, at the last write and while byte-level reduction's one transaction is open; the charts hold the same kinds")
    void theStepRemovingOccurrencesIsCountedByKindWithCodeAmongThem(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        for (String name : List.of("one.txt", "two.txt", "three.txt")) {
            Files.writeString(root.resolve(MARKER + "-" + name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.REFUSED_CONVERSION), "stands in for a container, " + MARKER);
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.HEADERS_AND_FOOTERS_ONLY), "page headers alone, " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .build());

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        JsonNode removing = TheReportFile.in(workingDirectory).get("removing");
        claim("removing names extraction, and holds three survivors of the five that entered it",
                () -> {
                    assertThat(removing.get("step").asString()).isEqualTo(StepNames.EXTRACTION);
                    assertThat(removing.get("entered").asLong()).isEqualTo(5);
                    assertThat(removing.get("survivors").asLong()).isEqualTo(3);
                });
        claim("and the verdicts by kind of the kinds extraction records, one of each",
                () -> {
                    assertThat(removing.get("verdicts").get("extraction-failed").asLong()).isEqualTo(1);
                    assertThat(removing.get("verdicts").get("degenerate-output").asLong()).isEqualTo(1);
                    assertThat(removing.get("verdicts").size()).isEqualTo(2);
                });
        JsonNode charts = TheReportFile.in(workingDirectory).get("charts");
        claim("the charts hold the verdicts chart with the same kinds, ending at one of each",
                () -> {
                    JsonNode verdicts = null;
                    for (JsonNode chart : charts) {
                        if ("verdicts".equals(chart.get("id").asString())) {
                            verdicts = chart;
                        }
                    }
                    assertThat(verdicts).isNotNull();
                    List<String> names = new ArrayList<>();
                    verdicts.get("lines").forEach(line -> {
                        names.add(line.get("name").asString());
                        assertThat(line.get("values").get(line.get("values").size() - 1).asDouble()).isEqualTo(1.0);
                    });
                    assertThat(names).containsExactly("extraction-failed", "degenerate-output");
                });

        // byte-level reduction, held at the last line of its broken check, its one transaction still open
        for (String name : List.of("one.txt", "two.txt")) {
            Files.deleteIfExists(root.resolve(MARKER + "-" + name));
        }
        Files.deleteIfExists(root.resolve(SeedScriptedExtractionBeans.REFUSED_CONVERSION));
        Files.deleteIfExists(root.resolve(SeedScriptedExtractionBeans.HEADERS_AND_FOOTERS_ONLY));
        Files.deleteIfExists(root.resolve(MARKER + "-three.txt"));
        for (String name : List.of(MARKER + "-a.txt", MARKER + "-b.txt")) {
            Files.writeString(root.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(root.resolve(MARKER + "-left-out" + LEFT_OUT_ENDING), "a text file the profile leaves out by name");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .outOfScopeNameEndings(LEFT_OUT_ENDING, "set by this test, so stage 1 leaves one file out")
                .build());
        AtomicBoolean held = new AtomicBoolean();
        AtomicReference<JsonNode> seen = new AtomicReference<>();
        HoldingAppender holding = new HoldingAppender(event -> {
            if (BROKEN_CHECK_DONE.equals(event.getFormattedMessage()) && held.compareAndSet(false, true)) {
                seen.set(TheReportFile.awaitWithItsCopy(workingDirectory, report -> {
                    JsonNode each = report.get("removing");
                    return !each.isNull() && each.get("verdicts").has("out-of-scope")
                            && each.get("verdicts").get("out-of-scope").asLong() == 1;
                }));
            }
        });
        holding.setContext(rootLogger.getLoggerContext());
        holding.start();
        rootLogger.addAppender(holding);
        try {
            cli.run("run", root.toString());
        } finally {
            rootLogger.detachAppender(holding);
            holding.stop();
        }

        claim("byte-level reduction was held at its broken check's last line, before its transaction could commit",
                () -> assertThat(held).isTrue());
        claim("while it was held, a write of the report showed the out-of-scope verdict it had recorded, byte-level"
                        + " reduction the removing step, and two survivors of the three that entered it",
                () -> {
                    assertThat(seen.get()).isNotNull();
                    JsonNode midway = seen.get().get("removing");
                    assertThat(midway.get("step").asString()).isEqualTo(StepNames.BYTE_LEVEL_REDUCTION);
                    assertThat(midway.get("entered").asLong()).isEqualTo(3);
                    assertThat(midway.get("survivors").asLong()).isEqualTo(2);
                    for (String kind : List.of("broken", "duplicate-of", "superseded-by", "out-of-scope", "code")) {
                        assertThat(midway.get("verdicts").has(kind)).isTrue();
                    }
                });
    }

    @Test
    @Story("The report lists every run the working directory holds, and marks the ones this invocation arrived at")
    @DisplayName("After two invocations over one root, the second under another floor, runs.rows lists every run in order read at the end, and arrivedAt holds the runs the second minted or continued")
    void theRunsAreReadAsTheInvocationStartsAndEndsAndMarkTheOnesItArrivedAt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aMarkedCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        claim("the first invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .boilerplateDocumentFrequencyFloor(SECOND_FLOOR, "set by this test, so stage 4's gate opens anew")
                .build());
        AtomicBoolean held = new AtomicBoolean();
        AtomicReference<JsonNode> whileRunning = new AtomicReference<>();
        HoldingAppender holding = new HoldingAppender(event -> {
            String message = event.getFormattedMessage();
            if (message != null && message.startsWith(CONTENT_REDUNDANCY_STARTS) && held.compareAndSet(false, true)) {
                whileRunning.set(TheReportFile.awaitWithItsCopy(workingDirectory,
                        report -> "start".equals(report.get("runs").get("read").asString())
                                && report.get("runs").get("rows").size() > 0));
            }
        });
        holding.setContext(rootLogger.getLoggerContext());
        holding.start();
        rootLogger.addAppender(holding);
        try {
            cli.run("run", root.toString());
        } finally {
            rootLogger.detachAppender(holding);
            holding.stop();
        }
        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());

        JsonNode report = TheReportFile.in(workingDirectory);
        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                "SELECT r.id AS id, r.stage AS stage, r.walk_id AS walk FROM run r ORDER BY r.walk_id, r.rowid");
        claim("while the second invocation ran, the report showed the runs as the invocation started, and not as it ended",
                () -> {
                    assertThat(held).isTrue();
                    assertThat(whileRunning.get()).isNotNull();
                    assertThat(whileRunning.get().get("runs").get("read").asString()).isEqualTo("start");
                });
        claim("at its end, runs were read as the invocation ended, and every run of the ledger is listed",
                () -> {
                    assertThat(report.get("runs").get("read").asString()).isEqualTo("end");
                    assertThat(report.get("runs").get("rows")).hasSize(ledger.size());
                });
        claim("each row has its walk, stage, ordinal, steps finished of the steps of its stage, its state and a count of each blocking kind",
                () -> {
                    for (JsonNode row : report.get("runs").get("rows")) {
                        assertThat(row.get("walk").asLong()).isPositive();
                        assertThat(row.get("ordinal").asLong()).isPositive();
                        assertThat(row.get("stage").asString()).isNotBlank();
                        assertThat(row.get("verdicts").size()).isEqualTo(BLOCKING.size());
                        if (!row.get("stepsOf").isNull()) {
                            assertThat(row.get("stepsFinished").asLong()).isLessThanOrEqualTo(row.get("stepsOf").asLong());
                            assertThat(row.get("state").asString())
                                    .isEqualTo(row.get("stepsFinished").asLong() == row.get("stepsOf").asLong()
                                            ? "finished" : "not finished");
                        }
                    }
                });
        claim("a stage run twice, content redundancy under the two floors, is rows of it with ordinals 1 and 2 (the database also holds the runs other tests of this class minted)",
                () -> {
                    List<Long> ordinals = new ArrayList<>();
                    report.get("runs").get("rows").forEach(row -> {
                        if (row.get("stage").asString().equals(StageModules.CONTENT_REDUNDANCY.stage())) {
                            ordinals.add(row.get("ordinal").asLong());
                        }
                    });
                    assertThat(ordinals).contains(1L, 2L);
                });
        claim("arrivedAt holds the runs the second minted or continued, no run twice, and exactly those rows are marked thisInvocation",
                () -> {
                    List<String> arrived = new ArrayList<>();
                    report.get("arrivedAt").forEach(each -> arrived.add(each.get("run").asString()));
                    assertThat(arrived).isNotEmpty().doesNotHaveDuplicates();
                    long marked = 0;
                    for (JsonNode row : report.get("runs").get("rows")) {
                        marked += row.get("thisInvocation").asBoolean() ? 1 : 0;
                    }
                    assertThat(marked).isEqualTo(arrived.size());
                });
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
