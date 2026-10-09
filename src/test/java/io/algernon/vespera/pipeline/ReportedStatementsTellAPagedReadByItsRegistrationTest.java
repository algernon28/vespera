package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ExtractionStatement;
import io.algernon.vespera.similarity.SimilarityStatement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@code ReportedStatements} takes a read made a page of survivors at a time for one because its caller
 * registered it as one, {@code paged(...)}, and never because it was registered {@code counted(...)} and
 * declares no steps a row (ADR-211 section 9, <i>Which form a statement's lines take</i>; ADR-193 section 7).
 *
 * <p>A timed statement and a paged read both declare no steps a row, and both may be started with an empty
 * total: a timed one always is, and a paged read is where its run holds no row, when it writes nothing
 * (ADR-204 section 4). So nothing a module reports tells the two apart, and the caller's words have to. Where
 * ADR-211 was first built, at {@code dfb0cb6}, they were told apart by whether the statement had been given a
 * label: a timed statement registered through {@code counted(...)} by mistake then wrote no line at all, where
 * before that commit it wrote its two timed lines, and nothing failed. This holds the rule the record now
 * states: a statement that declares no steps a row is timed unless it was registered as paged.
 *
 * <p>{@code paged} is called by its name and not as a method, so that this class compiles before the method
 * exists; the first claim of the two tests that need it is that it does.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class ReportedStatementsTellAPagedReadByItsRegistrationTest {

    /** The registration ADR-211 owes, named by its text so that this compiles before it exists. */
    private static final String PAGED = "paged";

    private static final String STAGE_4B = "Stage 4b (redundancy resolution)";

    private static final String STAGE_3 = "Stage 3 (content census)";

    private static final String METRICS_LABEL = "Stage 3 (content census, reading extraction metrics)";

    /** A label a timed statement has no use for, given to it by the mistake this class is about. */
    private static final String A_LABEL_GIVEN_BY_MISTAKE = "Stage 4b (redundancy resolution, reading signature bands)";

    /** A run of two full pages of 1,000 and a short third, for a total a statement might be started with. */
    private static final long TWO_AND_A_HALF_THOUSAND = 2_500L;

    /** The collection of the invocation tests: three documents, one page, whose one line is a hundred percent. */
    private static final long THREE = 3L;

    /** The line after a statement, with the seconds it took to one decimal place. */
    private static final String IN_SECONDS = " in \\d+\\.\\d s";

    private ListAppender<ILoggingEvent> logged;
    private List<Logger> loggers;

    @BeforeEach
    void captureTheLines() {
        logged = new ListAppender<>();
        logged.start();
        loggers = List.of(
                (Logger) LoggerFactory.getLogger(ReportedStatements.class),
                (Logger) LoggerFactory.getLogger(TimedStatement.class),
                (Logger) LoggerFactory.getLogger(StatementProgress.class));
        loggers.forEach(logger -> logger.addAppender(logged));
    }

    @AfterEach
    void releaseTheLines() {
        loggers.forEach(logger -> logger.detachAppender(logged));
        logged.stop();
    }

    @Test
    @Story("A statement the database cannot count says how long it took")
    @DisplayName("A statement with no steps to count, given a progress label by mistake, still writes its line before and its line after")
    void aTimedStatementRegisteredAsCountedStillWritesItsTwoLines() {
        ReportedStatements reads = ReportedStatements.saying()
                .counted(SimilarityStatement.SIGNATURE_BANDS, STAGE_4B, "the signature bands", A_LABEL_GIVEN_BY_MISTAKE)
                .build();

        reads.statementStarting(SimilarityStatement.SIGNATURE_BANDS, OptionalLong.empty());
        reads.statementEnded(SimilarityStatement.SIGNATURE_BANDS);

        List<String> lines = lines();
        claim(
                "started with no total, as a statement that is only timed always is, it writes the two lines of"
                        + " one: that it is reading, and that it read, with the seconds. It does not fall silent"
                        + " for having been given a label",
                () -> {
                    assertThat(lines).hasSize(2);
                    assertThat(lines.getFirst()).isEqualTo(STAGE_4B + " is reading the signature bands");
                    assertThat(lines.getLast()).matches("\\Q" + STAGE_4B + " read the signature bands\\E" + IN_SECONDS);
                });
    }

    @Test
    @Story("A statement the database cannot count says how long it took")
    @DisplayName("A statement with no steps to count, given a progress label by mistake and a total, is still only timed")
    void aTimedStatementRegisteredAsCountedIsNotTakenForAPagedRead() {
        ReportedStatements reads = ReportedStatements.saying()
                .counted(SimilarityStatement.SIGNATURE_BANDS, STAGE_4B, "the signature bands", A_LABEL_GIVEN_BY_MISTAKE)
                .build();

        reads.statementStarting(SimilarityStatement.SIGNATURE_BANDS, OptionalLong.of(TWO_AND_A_HALF_THOUSAND));
        reads.statementEnded(SimilarityStatement.SIGNATURE_BANDS);

        List<String> lines = lines();
        claim(
                "started over up to " + TWO_AND_A_HALF_THOUSAND + " rows, it writes the same two lines and"
                        + " promises no progress over rows it will never be told of: only a read its caller"
                        + " named as made a page at a time is reported as one",
                () -> {
                    assertThat(lines).hasSize(2);
                    assertThat(lines.getFirst()).isEqualTo(STAGE_4B + " is reading the signature bands");
                    assertThat(lines.getLast()).matches("\\Q" + STAGE_4B + " read the signature bands\\E" + IN_SECONDS);
                });
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("A read named as made a page at a time says over how many rows, how far it has gone as it is told its rows, and how long it took")
    void aReadRegisteredAsPagedSaysHowFarItHasGone() throws ReflectiveOperationException {
        ReportedStatements reads = paged(
                        ReportedStatements.saying(),
                        ExtractionStatement.EXTRACTION_METRICS,
                        STAGE_3,
                        "the extraction metrics",
                        METRICS_LABEL)
                .build();

        reads.statementStarting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(THREE));
        reads.rowsRead(ExtractionStatement.EXTRACTION_METRICS, THREE);
        reads.statementEnded(ExtractionStatement.EXTRACTION_METRICS);

        List<String> lines = lines();
        claim(
                "over up to " + THREE + " rows and told " + THREE + " rows read, it writes its line before with"
                        + " the total, one progress line under its label, a hundred percent, and its line after"
                        + " with the seconds",
                () -> {
                    assertThat(lines).hasSize(3);
                    assertThat(lines.get(0)).isEqualTo(STAGE_3 + " is reading the extraction metrics, over up to 3 rows");
                    assertThat(lines.get(1)).isEqualTo(METRICS_LABEL + ": about 100% of 3 rows");
                    assertThat(lines.get(2)).matches("\\Q" + STAGE_3 + " read the extraction metrics\\E" + IN_SECONDS);
                });
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("A read named as made a page at a time, over a run that holds no row, writes nothing")
    void aReadRegisteredAsPagedOverNoRowWritesNothing() throws ReflectiveOperationException {
        ReportedStatements reads = paged(
                        ReportedStatements.saying(),
                        ExtractionStatement.EXTRACTION_METRICS,
                        STAGE_3,
                        "the extraction metrics",
                        METRICS_LABEL)
                .build();

        reads.statementStarting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.empty());
        reads.statementEnded(ExtractionStatement.EXTRACTION_METRICS);

        claim(
                "started with no total, its run holding no row, it writes neither line and no progress: the"
                        + " silence a counted statement keeps over nothing, which a statement that is only timed"
                        + " does not keep",
                () -> assertThat(lines()).isEmpty());
    }

    /**
     * {@code words.paged(statement, stage, what, label)}, called by its name; its first claim is that the
     * registration exists.
     */
    private static ReportedStatements.Builder paged(
            ReportedStatements.Builder words, Enum<?> statement, String stage, String what, String label)
            throws ReflectiveOperationException {
        List<String> registrations = Arrays.stream(ReportedStatements.Builder.class.getDeclaredMethods())
                .map(Method::getName)
                .distinct()
                .sorted()
                .toList();
        claim(
                "the words of a read made a page at a time are given through a registration of their own, named "
                        + PAGED + ", beside the ones for a statement that is timed and for one that is counted",
                () -> assertThat(registrations).contains(PAGED, "timed", "counted"));
        Method paged = ReportedStatements.Builder.class.getDeclaredMethod(
                PAGED, Enum.class, String.class, String.class, String.class);
        return (ReportedStatements.Builder) paged.invoke(words, statement, stage, what, label);
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
