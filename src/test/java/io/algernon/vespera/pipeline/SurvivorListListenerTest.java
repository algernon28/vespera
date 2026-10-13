package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.MeasuredFormRow;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceAtPath;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.Verdicts;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * What {@link SurvivorListListener} does at the end of stage 2's step (ADR-250 sections 1, 3 and 4): it
 * writes the survivor list beside the database, holding the survivors of the run that carry a metrics row
 * under it, in the order of their paths; says where it is in one line; writes nothing for a step that failed
 * having read nothing; and says a failure in one error line, throwing none, with the page before left whole.
 *
 * <p>No database. The ledger's verdicts and extraction's metrics are stood in for: three survivors, of which
 * the stopped step never reached one, so it carries no metrics row, and the metrics rows come in an order
 * that disagrees with the paths. Everything asked and every line said is noted in one list, in the order it
 * happened in.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-250", url = Adr.STAGE_2_WRITES_THE_SURVIVOR_LIST_BESIDE_THE_REVIEW_LIST, type = "adr")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
class SurvivorListListenerTest {

    private static final RunId STAGE_TWO_RUN = new RunId("a-stage-2-run");

    /** The page's name, beside the database. */
    private static final String THE_SURVIVOR_LIST = "extraction-survivors.html";

    /** Named first, recorded second. */
    private static final OccurrenceAtPath NAMED_FIRST =
            new OccurrenceAtPath(new OccurrenceId(2), new OccurrencePath("a/named-first.pdf"));

    /** A survivor the stopped step never reached: no verdict, and no metrics row either. */
    private static final OccurrenceAtPath NOT_YET_REACHED =
            new OccurrenceAtPath(new OccurrenceId(3), new OccurrencePath("b/not-yet-reached.pdf"));

    /** Named last, recorded first. */
    private static final OccurrenceAtPath NAMED_LAST =
            new OccurrenceAtPath(new OccurrenceId(1), new OccurrencePath("c/named-last.txt"));

    /** The run's survivors as the ledger hands them by path: one page. */
    private static final List<OccurrenceAtPath> THE_SURVIVORS_BY_PATH = List.of(NAMED_FIRST, NOT_YET_REACHED, NAMED_LAST);

    /** How many of the three carry a metrics row: the two the step converted. */
    private static final int CAME_THROUGH = 2;

    private static final String THE_LINE_BEFORE = "Stage 2 (extraction) is reading the survivors it converted";

    private static final String THE_LINE_AFTER_OPENS = "Stage 2 (extraction) read the survivors it converted in ";

    private static final String THE_COUNT = "the survivors were asked for, to count those that carry a row";

    private static final String THE_READ_BY_PATH = "the survivors were asked for by path";

    private static final String WHAT_THE_READ_SAID = "the database stopped answering partway through the read";

    private static final String WHAT_THE_COUNT_SAID = "the database stopped answering before the count was done";

    /** How many times the move is refused before it goes through. */
    private static final int REFUSED_TWICE = 2;

    /** How many times a refused move is attempted in all before the refusal is said: the move's own bound. */
    private static final int EVERY_ATTEMPT = 5;

    /** Everything asked of the two stand-ins and every line said, in the order it happened in. */
    private final List<String> happened = new ArrayList<>();

    /** The lines the listener itself said, with their levels. */
    private final List<ILoggingEvent> saidByTheListener = new ArrayList<>();

    private final AppenderBase<ILoggingEvent> lines = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent line) {
            happened.add(line.getFormattedMessage());
            if (line.getLoggerName().equals(SurvivorListListener.class.getName())) {
                saidByTheListener.add(line);
            }
        }
    };

    private Logger listenerLogger;

    private Logger timedStatements;

    @BeforeEach
    void noteEveryLine() {
        listenerLogger = (Logger) LoggerFactory.getLogger(SurvivorListListener.class);
        timedStatements = (Logger) LoggerFactory.getLogger(TimedStatement.class);
        lines.start();
        listenerLogger.addAppender(lines);
        timedStatements.addAppender(lines);
    }

    @AfterEach
    void stopNoting() {
        listenerLogger.detachAppender(lines);
        timedStatements.detachAppender(lines);
        lines.stop();
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("When extraction ends, the files it converted and kept are listed by path beside the database, without a file it never reached, and one line says how many and where")
    void theSurvivorsThatCarryARowAreListedByPath(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(theVerdicts(), theMetrics(), root, workingDirectory).afterStep(step(ExitStatus.COMPLETED, 0));

        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);
        String written = Files.readString(page, StandardCharsets.UTF_8);
        claim(
                "the page states the " + CAME_THROUGH + " files that carry a measurement, and names them in the"
                        + " order of their paths although their measurements were handed over the other way round",
                () -> assertThat(written)
                        .contains("Files that came through: " + CAME_THROUGH + ".")
                        .containsSubsequence(
                                NAMED_FIRST.path().value(), NAMED_LAST.path().value(), "</table>"));
        claim(
                "the file that is still standing only because extraction never reached it is not on the page",
                () -> assertThat(written).doesNotContain(NOT_YET_REACHED.path().value()));
        claim(
                "each file's row carries its page count, or a dash where it has none, and its word count",
                () -> assertThat(written)
                        .contains(ReportPage.row(
                                ReportPage.textCell(NAMED_FIRST.path().value()),
                                ReportPage.numberCell(4),
                                ReportPage.numberCell(900)))
                        .contains(ReportPage.row(
                                ReportPage.textCell(NAMED_LAST.path().value()),
                                ReportPage.numberCell("&mdash;"),
                                ReportPage.numberCell(12))));
        claim(
                "the page was written beside its place and moved there: nothing is left beside it",
                () -> assertThat(workingDirectory.resolve(THE_SURVIVOR_LIST + ".part")).doesNotExist());
        claim(
                "the listener said one line, not an error, stating the " + CAME_THROUGH + " files and where their"
                        + " list is",
                () -> assertThat(saidByTheListener).singleElement().satisfies(line -> {
                    assertThat(line.getLevel()).isEqualTo(Level.INFO);
                    assertThat(line.getFormattedMessage())
                            .isEqualTo("Stage 2 (extraction): " + CAME_THROUGH + " file(s) came through; their"
                                    + " list is " + page.toAbsolutePath());
                }));
        int before = happened.indexOf(THE_LINE_BEFORE);
        int after = theLineAfter();
        claim(
                "the count and the read by path were both made after the line that says the reading began and"
                        + " before the line that says how long it took, so the seconds that line states are the"
                        + " whole page's",
                () -> {
                    assertThat(List.of(before, after)).as("what happened, in order: %s", happened).doesNotContain(-1);
                    assertThat(happened.indexOf(THE_COUNT))
                            .as("what happened, in order: %s", happened)
                            .isGreaterThan(before)
                            .isLessThan(after);
                    assertThat(happened.indexOf(THE_READ_BY_PATH))
                            .as("what happened, in order: %s", happened)
                            .isGreaterThan(before)
                            .isLessThan(after);
                });
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("An extraction that failed before reading any file writes no list and asks nothing")
    void aStepThatFailedHavingReadNothingWritesNoPage(@TempDir Path root, @TempDir Path workingDirectory) {
        StageRuns neverAsked = stageRuns(new Ledger(null), root, () -> {
            throw new AssertionError("the run was asked for at the end of a step that read nothing");
        });
        SurvivorListListener listener =
                new SurvivorListListener(new Ledger(null), theMetrics(), neverAsked, workingDirectory);

        Throwable thrown = catchThrowable(() -> listener.afterStep(step(ExitStatus.FAILED, 0)));

        claim(
                "nothing was thrown, so the run was not asked for: asking would call the converter that had"
                        + " just failed its check",
                () -> assertThat(thrown).isNull());
        claim(
                "no page was written and nothing was said: there is no extraction to list",
                () -> {
                    assertThat(workingDirectory.resolve(THE_SURVIVOR_LIST)).doesNotExist();
                    assertThat(happened).isEmpty();
                });
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("An extraction that failed after reading a file still lists what it had converted and kept by then")
    void aStepThatFailedAfterReadingWritesThePage(@TempDir Path root, @TempDir Path workingDirectory) {
        listener(theVerdicts(), theMetrics(), root, workingDirectory).afterStep(step(ExitStatus.FAILED, 1));

        claim(
                "the page is there and names the " + CAME_THROUGH + " files converted before the failure",
                () -> assertThat(workingDirectory.resolve(THE_SURVIVOR_LIST))
                        .isRegularFile()
                        .content(StandardCharsets.UTF_8)
                        .contains(NAMED_FIRST.path().value(), NAMED_LAST.path().value()));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("When the read of the files extraction kept fails partway, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    void aReadThatFailsPartwayLeavesThePageBeforeAndIsNotThrown(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(theVerdicts(), theMetrics(), root, workingDirectory).afterStep(step(ExitStatus.COMPLETED, 0));
        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        saidByTheListener.clear();
        happened.clear();

        Throwable thrown = catchThrowable(() -> listener(failingAfterItsFirstPage(), theMetrics(), root, workingDirectory)
                .afterStep(step(ExitStatus.COMPLETED, 0)));

        theReadingBeganAndNoLineSaysHowLongItTook();

        claim(
                "nothing was thrown from the end of extraction: the failed read was said, not thrown",
                () -> assertThat(thrown).isNull());
        claim(
                "the list in its place is the first invocation's, character for character, and not the head of a"
                        + " new one",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that " + CAME_THROUGH + " files came through and that their"
                        + " list could not be written to the page's place, with what the database said",
                () -> assertThat(saidByTheListener).singleElement().satisfies(line -> {
                    assertThat(line.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(line.getThrowableProxy()).isNull();
                    assertThat(line.getFormattedMessage())
                            .startsWith("Stage 2 (extraction): " + CAME_THROUGH + " file(s) came through, and"
                                    + " their list could not be written to " + page.toAbsolutePath() + ": ")
                            .contains(WHAT_THE_READ_SAID);
                }));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("When the count of the files extraction kept fails, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    void aCountThatFailsLeavesThePageBeforeAndIsNotThrown(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(theVerdicts(), theMetrics(), root, workingDirectory).afterStep(step(ExitStatus.COMPLETED, 0));
        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        saidByTheListener.clear();
        happened.clear();

        Throwable thrown = catchThrowable(() -> listener(failingToCount(), theMetrics(), root, workingDirectory)
                .afterStep(step(ExitStatus.COMPLETED, 0)));

        theReadingBeganAndNoLineSaysHowLongItTook();

        claim(
                "nothing was thrown from the end of extraction: the failed count was said, not thrown",
                () -> assertThat(thrown).isNull());
        claim(
                "the list in its place is the first invocation's, character for character",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that the files that came through could not be counted and"
                        + " that their list could not be written to the page's place, with what the database said",
                () -> assertThat(saidByTheListener).singleElement().satisfies(line -> {
                    assertThat(line.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(line.getFormattedMessage())
                            .startsWith("Stage 2 (extraction): the files that came through could not be counted,"
                                    + " and their list could not be written to " + page.toAbsolutePath() + ": ")
                            .contains(WHAT_THE_COUNT_SAID);
                }));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("A list of the files extraction kept that cannot be moved into place twice in a row, because another program holds it, is put in place on the third attempt")
    void aMoveRefusedForAMomentStillPutsThePageInPlace(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        int[] attempts = {0};
        ReplacingMove refusedTwice = new ReplacingMove(
                (part, target) -> {
                    attempts[0]++;
                    if (attempts[0] <= REFUSED_TWICE) {
                        throw new AccessDeniedException(part.toString(), target.toString(), null);
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        listener(theVerdicts(), theMetrics(), root, workingDirectory, refusedTwice)
                .afterStep(step(ExitStatus.COMPLETED, 0));

        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);
        claim(
                "the move was attempted three times, the first " + REFUSED_TWICE + " refused",
                () -> assertThat(attempts[0]).isEqualTo(REFUSED_TWICE + 1));
        claim(
                "the list stands whole in its place, naming the " + CAME_THROUGH + " files, and nothing is left"
                        + " beside it",
                () -> {
                    assertThat(Files.readString(page, StandardCharsets.UTF_8))
                            .contains(NAMED_FIRST.path().value(), NAMED_LAST.path().value())
                            .contains("</table>");
                    assertThat(workingDirectory.resolve(THE_SURVIVOR_LIST + ".part")).doesNotExist();
                });
        claim(
                "the line that says where the list is was written, and no error line",
                () -> assertThat(saidByTheListener).singleElement().satisfies(line -> {
                    assertThat(line.getLevel()).isEqualTo(Level.INFO);
                    assertThat(line.getFormattedMessage()).contains("their list is " + page.toAbsolutePath());
                }));
    }

    @Test
    @Story("The list of files extraction read and kept")
    @DisplayName("When the list of the files extraction kept is refused its place on every attempt, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    void aMoveRefusedOnEveryAttemptLeavesThePageBeforeAndIsNotThrown(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(theVerdicts(), theMetrics(), root, workingDirectory).afterStep(step(ExitStatus.COMPLETED, 0));
        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        saidByTheListener.clear();
        happened.clear();
        int[] attempts = {0};
        ReplacingMove refusedForGood = new ReplacingMove(
                (part, target) -> {
                    attempts[0]++;
                    throw new AccessDeniedException(part.toString(), target.toString(), null);
                },
                millis -> {});

        Throwable thrown =
                catchThrowable(() -> listener(theVerdicts(), theMetrics(), root, workingDirectory, refusedForGood)
                        .afterStep(step(ExitStatus.COMPLETED, 0)));

        claim(
                "the move was attempted " + EVERY_ATTEMPT + " times, as often as a refused move is attempted, and"
                        + " nothing was thrown from the end of extraction",
                () -> {
                    assertThat(attempts[0]).isEqualTo(EVERY_ATTEMPT);
                    assertThat(thrown).isNull();
                });
        claim(
                "the list in its place is the first invocation's, character for character",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that " + CAME_THROUGH + " files came through and that their"
                        + " list could not be written to the page's place",
                () -> assertThat(saidByTheListener).singleElement().satisfies(line -> {
                    assertThat(line.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(line.getFormattedMessage())
                            .startsWith("Stage 2 (extraction): " + CAME_THROUGH + " file(s) came through, and"
                                    + " their list could not be written to " + page.toAbsolutePath() + ": ");
                }));
        theReadingBeganAndNoLineSaysHowLongItTook();
    }

    /** That a page not put in its place leaves the line before its reading and no line after it. */
    private void theReadingBeganAndNoLineSaysHowLongItTook() {
        claim(
                "the line that says the reading began was said, and no line says how long reading and writing"
                        + " took: the page was not put in its place, so there is no such time to state",
                () -> {
                    assertThat(happened).as("what happened, in order: %s", happened).contains(THE_LINE_BEFORE);
                    assertThat(theLineAfter()).as("what happened, in order: %s", happened).isEqualTo(-1);
                });
    }

    private int theLineAfter() {
        for (int at = 0; at < happened.size(); at++) {
            if (happened.get(at).startsWith(THE_LINE_AFTER_OPENS)) {
                return at;
            }
        }
        return -1;
    }

    private static StepExecution step(ExitStatus exitStatus, long occurrencesRead) {
        StepExecution step = new StepExecution(
                1L, "extraction", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        step.setExitStatus(exitStatus);
        step.setReadCount(occurrencesRead);
        return step;
    }

    private static SurvivorListListener listener(
            Verdicts verdicts, ExtractionMetrics metrics, Path root, Path workingDirectory) {
        return listener(verdicts, metrics, root, workingDirectory, ReplacingMove.ATOMIC);
    }

    /** A listener whose page is put in place by {@code moves}. */
    private static SurvivorListListener listener(
            Verdicts verdicts, ExtractionMetrics metrics, Path root, Path workingDirectory, ReplacingMove moves) {
        Ledger ledger = new Ledger(null) {
            @Override
            public Verdicts verdicts() {
                return verdicts;
            }
        };
        return new SurvivorListListener(
                ledger, metrics, stageRuns(ledger, root, () -> STAGE_TWO_RUN), workingDirectory, moves);
    }

    private static StageRuns stageRuns(Ledger ledger, Path root, java.util.function.Supplier<RunId> extraction) {
        return new StageRuns(
                ledger, new ImplementationVersions(), null, null, null, null, null, null, null, null, null, null, root,
                new ExecutionContext()) {
            @Override
            RunId extraction() {
                return extraction.get();
            }
        };
    }

    /** Verdicts that hand the three survivors, in id order for the count and by path for the rows. */
    private Verdicts theVerdicts() {
        return new Verdicts(null) {
            @Override
            public void eachPageOfSurvivors(RunId runId, Consumer<List<OccurrenceId>> page) {
                happened.add(THE_COUNT);
                page.accept(List.of(NAMED_LAST.occurrenceId(), NAMED_FIRST.occurrenceId(), NOT_YET_REACHED.occurrenceId()));
            }

            @Override
            public void eachPageOfSurvivorsByPath(RunId runId, Consumer<List<OccurrenceAtPath>> page) {
                happened.add(THE_READ_BY_PATH);
                page.accept(THE_SURVIVORS_BY_PATH);
            }
        };
    }

    /** Verdicts that count as {@link #theVerdicts()} do, hand over a page by path, and then fail as a statement would. */
    private Verdicts failingAfterItsFirstPage() {
        return new Verdicts(null) {
            @Override
            public void eachPageOfSurvivors(RunId runId, Consumer<List<OccurrenceId>> page) {
                page.accept(List.of(NAMED_LAST.occurrenceId(), NAMED_FIRST.occurrenceId(), NOT_YET_REACHED.occurrenceId()));
            }

            @Override
            public void eachPageOfSurvivorsByPath(RunId runId, Consumer<List<OccurrenceAtPath>> page) {
                page.accept(THE_SURVIVORS_BY_PATH);
                throw new DataAccessResourceFailureException(WHAT_THE_READ_SAID);
            }
        };
    }

    /** Verdicts whose count fails as a statement would, and which are never asked for their rows. */
    private static Verdicts failingToCount() {
        return new Verdicts(null) {
            @Override
            public void eachPageOfSurvivors(RunId runId, Consumer<List<OccurrenceId>> page) {
                throw new DataAccessResourceFailureException(WHAT_THE_COUNT_SAID);
            }

            @Override
            public void eachPageOfSurvivorsByPath(RunId runId, Consumer<List<OccurrenceAtPath>> page) {
                throw new AssertionError("the rows were asked for after their count failed");
            }
        };
    }

    /**
     * Metrics that hold a row for the two survivors the step converted, the one named last first, so the
     * order the rows are handed over in disagrees with the paths: four pages and 900 words for the one named
     * first, no page count and 12 words for the one named last.
     */
    private static ExtractionMetrics theMetrics() {
        Map<OccurrenceId, Object[]> rows = new LinkedHashMap<>();
        rows.put(NAMED_LAST.occurrenceId(), new Object[] {null, 12});
        rows.put(NAMED_FIRST.occurrenceId(), new Object[] {4, 900});
        return new ExtractionMetrics(null, null) {
            @Override
            public Set<OccurrenceId> recordedAmong(RunId runId, Collection<OccurrenceId> occurrences) {
                Set<OccurrenceId> recorded = new HashSet<>(rows.keySet());
                recorded.retainAll(occurrences);
                return recorded;
            }

            @Override
            public void eachMeasuredFormOf(RunId runId, Collection<OccurrenceId> occurrences, MeasuredFormRow row) {
                rows.forEach((occurrence, measured) -> {
                    if (occurrences.contains(occurrence)) {
                        row.read(occurrence, "en", false, (Integer) measured[1], (Integer) measured[0], 0, 0);
                    }
                });
            }
        };
    }
}
