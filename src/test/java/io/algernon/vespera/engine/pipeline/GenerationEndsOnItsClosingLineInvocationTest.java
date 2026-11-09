package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.pipeline.GenerationScriptedBeans.ScriptedAnswer;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b's closing line stays the last line the invocation writes (ADR-364 §4): the generation step records the
 * line it stops on, or the line a refused drawing leaves, and {@link RecordedClosingLines} logs it after the step
 * ends, so Spring Batch's own {@code executed in} line, the copies' line and a warning about the index all stand
 * before it. Each claim reads the lines of the jar's own loggers and of {@code AbstractStep}'s, through one
 * appender, in the order they were logged.
 *
 * <p>Every case arranges one group for each document, as {@link GenerationBreakerInvocationTest} does, and turns
 * the answers down by script. The deliverable root is bound for the class under a folder of a temporary base and
 * taken away before each test, as {@link TheDeliverableRootInvocationTest} does.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Architecture")
@Feature("Process exit")
@Issue("773")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class GenerationEndsOnItsClosingLineInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String SPRING_BATCH_STEP_LOGGER = "org.springframework.batch.core.step.AbstractStep";

    private static final String THE_READING_WINDOW = "4096";

    /** The groups the fixture arranges, one for each document, named in the order they are written over. */
    private static final List<String> GROUP_NAMES = List.of(
            "Group one", "Group two", "Group three", "Group four", "Group five", "Group six");

    /** How many answers turned down in a row stop the step. */
    private static final int FIVE_IN_A_ROW = 5;

    /** One more group than the streak, so the step stops on the streak and not for want of groups. */
    private static final int ONE_MORE_GROUP_THAN_THE_STREAK = FIVE_IN_A_ROW + 1;

    private static final String THE_COPIES_LINE = "Stage 6b (generation, copies):";

    private static final String THE_INDEX_WARNING = "the deliverable's index could not be opened";

    private static final String THE_FIVE_IN_A_ROW_LINE = "the generation step stopped under run ";

    private static final String THE_FINISHED_LINE = "The generation step finished under ";

    private static final String THE_TREE_CLAUSE = "the deliverable tree is at";

    private static final String THE_REFUSAL_LINE = "the deliverable root ";

    private static final String THE_FAILED_STEPS_LINE = "Step generation failed and is not recorded as finished: ";

    private static final String BATCH_EXECUTED_IN = "executed in";

    private static final String A_REPORT_LINE = "Report: ";

    /** A generation run's id of the shape the ledger mints, which no corpus of this class mints. */
    private static final String ANOTHER_GENERATION_RUN = "e".repeat(48) + "0123456789abcdef";

    @TempDir
    static Path workingDirectory;

    /** Where the deliverable root lies, a folder of its own beneath it. */
    @TempDir
    static Path base;

    @DynamicPropertySource
    static void workingDirectoryAndDeliverableRoot(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY, () -> deliverableRoot().toString());
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingPageOpener opener;

    private ListAppender<ILoggingEvent> logged;
    private final List<ch.qos.logback.classic.Logger> attachedTo = new java.util.ArrayList<>();

    @BeforeEach
    void startFromARootNotMadeYetAndReadTheLoggers() throws IOException {
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
        takeAway(deliverableRoot());
        logged = new ListAppender<>();
        logged.start();
        for (String name : List.of(APPLICATION_LOGGER, OperatorLines.LOGGER_NAME, SPRING_BATCH_STEP_LOGGER)) {
            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
            logger.addAppender(logged);
            attachedTo.add(logger);
        }
    }

    @AfterEach
    void forgetWhatWasScriptedAndLetGoOfTheLoggers() {
        attachedTo.forEach(logger -> logger.detachAppender(logged));
        attachedTo.clear();
        logged.stop();
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
    }

    @Test
    @Story("The closing line is the last line of the invocation")
    @DisplayName("After five answers turned down in a row the tree is drawn before the line saying the step stopped")
    void afterFiveInARowTheTreeIsDrawnBeforeTheClosingLine(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(ONE_MORE_GROUP_THAN_THE_STREAK, root, seeds);
        fiveAnswersTurnedDown();
        opener.throwing(new IllegalStateException("scripted: no browser here"));
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> lines = logged.list;
        int copies = indexOfLine(lines, THE_COPIES_LINE);
        int warning = indexOfLine(lines, THE_INDEX_WARNING);
        int executed = indexOfBatchLine(lines);
        int stopped = indexOfLine(lines, THE_FIVE_IN_A_ROW_LINE);
        claim(
                "the precondition: the step stopped on the five, the invocation failed, and each line looked for is"
                        + " there to be found, the warning at WARN and the five-in-a-row line on the operator logger"
                        + " at ERROR",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(copies).as("the copies' line").isNotNegative();
                    assertThat(warning).as("the warning about the index").isNotNegative();
                    assertThat(executed).as("Spring Batch's executed-in line").isNotNegative();
                    assertThat(stopped).as("the five-in-a-row line").isNotNegative();
                    assertThat(lines.get(warning).getLevel()).isEqualTo(Level.WARN);
                    assertThat(lines.get(stopped).getLevel()).isEqualTo(Level.ERROR);
                    assertThat(lines.get(stopped).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                });
        claim(
                "the copies' line, the warning and Spring Batch's executed-in line are all logged before the"
                        + " five-in-a-row line: the tree is drawn while the step still runs, and what the step"
                        + " ends with follows it",
                () -> assertThat(stopped).isGreaterThan(copies).isGreaterThan(warning).isGreaterThan(executed));
        claim(
                "and the five-in-a-row line is the last line that does not start " + A_REPORT_LINE + ", the closing"
                        + " line of the invocation",
                () -> assertThat(lastLineNotAReport(lines)).isSameAs(lines.get(stopped)));
    }

    @Test
    @Story("The closing line is the last line of the invocation")
    @DisplayName("When every fault stands repeated and the deliverable root is refused, the refusal is the last line")
    void onTheRepeatedFaultsBranchTheRefusedRootIsTheLastLine(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(1, root, seeds);
        GenerationScriptedBeans.answerFor(
                GROUP_NAMES.getFirst(),
                ScriptedAnswer.saying(
                        GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE + " }"));
        cli.run("run", root.toString());
        takeAway(deliverableRoot());
        anotherRunsTreeInTheRoot();
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> lines = logged.list;
        int finished = indexOfLine(lines, THE_FINISHED_LINE);
        int executed = indexOfBatchLine(lines);
        int refusal = indexOfLine(lines, THE_REFUSAL_LINE);
        claim(
                "the precondition: the group stood without writing, so the step finished with the tree refused, and"
                        + " each line looked for is there, the refusal on the operator logger at ERROR and the"
                        + " finished line saying nothing of a tree",
                () -> {
                    assertThat(finished).as("the finished line").isNotNegative();
                    assertThat(executed).as("Spring Batch's executed-in line").isNotNegative();
                    assertThat(refusal).as("the refusal").isNotNegative();
                    assertThat(lines.get(finished).getFormattedMessage()).doesNotContain(THE_TREE_CLAUSE);
                    assertThat(lines.get(refusal).getLevel()).isEqualTo(Level.ERROR);
                    assertThat(lines.get(refusal).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                    assertThat(lines.get(refusal).getFormattedMessage()).contains("holds the tree of another generation run");
                });
        claim(
                "the finished line and Spring Batch's executed-in line are logged before the refusal",
                () -> assertThat(refusal).isGreaterThan(finished).isGreaterThan(executed));
        claim(
                "and the refusal is the last line that does not start " + A_REPORT_LINE,
                () -> assertThat(lastLineNotAReport(lines)).isSameAs(lines.get(refusal)));
    }

    @Test
    @Story("The closing line is the last line of the invocation")
    @DisplayName("A refusal the drawing comes to after five in a row is logged after the line saying the step stopped")
    void aRefusalTheDrawingComesToAfterFiveInARowIsLoggedAfterIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(ONE_MORE_GROUP_THAN_THE_STREAK, root, seeds);
        fiveAnswersTurnedDown();
        anotherRunsTreeInTheRoot();
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> lines = logged.list;
        int stopped = indexOfLine(lines, THE_FIVE_IN_A_ROW_LINE);
        int refusal = indexOfLine(lines, THE_REFUSAL_LINE);
        claim(
                "the precondition: both lines are there, each on the operator logger at ERROR",
                () -> {
                    assertThat(stopped).as("the five-in-a-row line").isNotNegative();
                    assertThat(refusal).as("the refusal").isNotNegative();
                    assertThat(lines.get(stopped).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                    assertThat(lines.get(stopped).getLevel()).isEqualTo(Level.ERROR);
                    assertThat(lines.get(refusal).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                    assertThat(lines.get(refusal).getLevel()).isEqualTo(Level.ERROR);
                });
        claim(
                "the five-in-a-row line is logged first and the refusal after it, in the order the step recorded"
                        + " them, though the refusal was found a moment later",
                () -> assertThat(refusal).isGreaterThan(stopped));
        claim(
                "and the refusal is the last line that does not start " + A_REPORT_LINE,
                () -> assertThat(lastLineNotAReport(lines)).isSameAs(lines.get(refusal)));
    }

    @Test
    @Story("The closing line is the last line of the invocation")
    @DisplayName("A drawing that throws after five in a row ends on the line of the step that failed")
    void aDrawingThatThrowsAfterFiveInARowEndsOnTheFailedStepsLine(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(ONE_MORE_GROUP_THAN_THE_STREAK, root, seeds);
        fiveAnswersTurnedDown();
        aRootThatPassesBothRefusalsAndCannotBeDrawnIn();
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> lines = logged.list;
        int stopped = indexOfLine(lines, THE_FIVE_IN_A_ROW_LINE);
        int failed = indexOfLine(lines, THE_FAILED_STEPS_LINE);
        claim(
                "the precondition: both lines are there, each on the operator logger at ERROR",
                () -> {
                    assertThat(stopped).as("the five-in-a-row line").isNotNegative();
                    assertThat(failed).as("the failed step's line").isNotNegative();
                    assertThat(lines.get(stopped).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                    assertThat(lines.get(stopped).getLevel()).isEqualTo(Level.ERROR);
                    assertThat(lines.get(failed).getLoggerName()).isEqualTo(OperatorLines.LOGGER_NAME);
                    assertThat(lines.get(failed).getLevel()).isEqualTo(Level.ERROR);
                });
        claim(
                "the five-in-a-row line is logged by the recorded lines, so it stands after Spring Batch's own"
                        + " `executed in` line for the step, and the failed step's line after it",
                () -> {
                    int batch = indexOfBatchLine(lines);
                    assertThat(batch).as("Spring Batch's own line").isNotNegative();
                    assertThat(stopped).isGreaterThan(batch);
                    assertThat(failed).isGreaterThan(stopped);
                });
        claim(
                "and the failed step's line is the last line that does not start " + A_REPORT_LINE,
                () -> assertThat(lastLineNotAReport(lines)).isSameAs(lines.get(failed)));
    }

    /** The first line, in the order logged, whose words start {@code start}, or -1. */
    private static int indexOfLine(List<ILoggingEvent> lines, String start) {
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).getFormattedMessage().startsWith(start)) {
                return at;
            }
        }
        return -1;
    }

    /** Spring Batch's own line for the generation step, the first of {@code AbstractStep}'s that says {@code executed in}. */
    private static int indexOfBatchLine(List<ILoggingEvent> lines) {
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).getLoggerName().equals(SPRING_BATCH_STEP_LOGGER)
                    && lines.get(at).getFormattedMessage().contains(BATCH_EXECUTED_IN)
                    && lines.get(at).getFormattedMessage().contains("generation")) {
                return at;
            }
        }
        return -1;
    }

    private static ILoggingEvent lastLineNotAReport(List<ILoggingEvent> lines) {
        return lines.stream()
                .filter(line -> !line.getFormattedMessage().startsWith(A_REPORT_LINE))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    /** The first five groups' answers are each turned down, the sixth is left unscripted. */
    private static void fiveAnswersTurnedDown() {
        for (int group = 0; group < FIVE_IN_A_ROW; group++) {
            GenerationScriptedBeans.answerFor(
                    GROUP_NAMES.get(group),
                    ScriptedAnswer.arrivingAs("{\"title\":\"Site Safety Audits\",\"prose\":"));
        }
    }

    private static Path deliverableRoot() {
        return base.resolve("the deliverable root");
    }

    /** The root holding a tree this working directory drew under another generation: its marker and an index. */
    private static void anotherRunsTreeInTheRoot() throws IOException {
        Files.createDirectories(deliverableRoot());
        Files.writeString(
                deliverableRoot().resolve("vespera-deliverable.txt"),
                "working-directory=" + workingDirectory.toRealPath() + "\ngeneration-run=" + ANOTHER_GENERATION_RUN + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(deliverableRoot().resolve("index.html"), "another generation's index", StandardCharsets.UTF_8);
    }

    /**
     * A root that neither refusal reads as anything but absent, and in which no folder can be made: a link to
     * a place that is not there. {@code Files.exists} follows the link and finds nothing, so the refusals let it
     * through, and making the folder under it throws.
     */
    private static void aRootThatPassesBothRefusalsAndCannotBeDrawnIn() throws IOException {
        Files.createSymbolicLink(deliverableRoot(), base.resolve("nothing is here"));
    }

    private static void takeAway(Path folder) throws IOException {
        if (Files.isSymbolicLink(folder)) {
            Files.delete(folder);
            return;
        }
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    /** A corpus of {@code groups} documents, walked once, arranged into {@code groups} groups of one each, approved. */
    private void anApprovedArrangementOf(int groups, Path root, Path seeds) throws IOException {
        for (int document = 1; document <= groups; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "corpus document " + document);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every call here reads alike")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(
                        ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")),
                        "read by this test")
                .build());
        oneGroupPerDocument(new RunsOver(jdbcTemplate, root).latest("arrangement"), groups, root);
    }

    /**
     * Replaces the arranged clusters with {@code groups} of one document each, named in the order they are
     * written over (as {@link GenerationBreakerInvocationTest} does, for the reason it gives).
     */
    private void oneGroupPerDocument(RunId arrangement, int groups, Path root) {
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement.value());
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT dc.occurrence_id FROM document_cluster dc"
                        + " JOIN file_occurrence fo ON fo.id = dc.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE dc.run_id = ? AND w.root = ? ORDER BY dc.occurrence_id",
                Long.class,
                scoring,
                Walk.canonicalRoot(root).toString());
        if (documents.size() < groups) {
            throw new IllegalStateException(
                    "this fixture needs " + groups + " documents in the arrangement, and this walk produced "
                            + documents.size());
        }
        Long winningSeed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                documents.getFirst());
        jdbcTemplate.update("UPDATE document_cluster SET cluster_ordinal = ? WHERE run_id = ?", groups, scoring);
        for (int group = 0; group < groups; group++) {
            jdbcTemplate.update(
                    "UPDATE document_cluster SET cluster_ordinal = ?, winning_seed_occurrence_id = ?"
                            + " WHERE run_id = ? AND occurrence_id = ?",
                    group,
                    winningSeed,
                    scoring,
                    documents.get(group));
        }
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", arrangement.value());
        for (int group = 0; group < groups; group++) {
            jdbcTemplate.update(
                    "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                            + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, ?, 0, ?)",
                    arrangement.value(),
                    winningSeed,
                    group,
                    GROUP_NAMES.get(group),
                    1,
                    group);
        }
    }
}
