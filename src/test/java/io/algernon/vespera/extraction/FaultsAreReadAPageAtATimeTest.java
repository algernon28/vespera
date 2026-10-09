package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ExtractionFaults.eachPageOfFaulted}: a stopped stage 2's faults, read a page of the run's fault rows at
 * a time, each page handed to the caller before the next is read, so that the caller can delete the verdicts
 * that resolved the page's faults without anything holding every fault of the run (ADR-214 section 2).
 *
 * <p>The read is reported by the rows it has read, the paged form ADR-211 section 9 gave three reads, and not
 * by SQLite's steps: each page is a statement of its own. The fault rows are written in the reverse of their
 * occurrences' order, so a page that came by occurrence and not in the order the rows were written would show.
 * Another run's fault rows sit after them in the same table.
 *
 * <p>It names a method ADR-214 adds, so the test tree does not compile until that method exists.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class FaultsAreReadAPageAtATimeTest {

    /** Two full pages of 1,000 and a short third. */
    private static final int FAULTS = 2_500;

    /** The most rows a page holds. */
    private static final int A_PAGE = 1_000;

    /** The fault rows another run of the same step left in the table, after this run's. */
    private static final int ANOTHER_RUNS_FAULTS = 100;

    private static final String PAGE = "a page of the run's fault rows";
    private static final String HANDED_OVER = "the page handed to the caller";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId asked;
    private RunId withNoFault;
    private final List<OccurrenceId> inTheOrderWritten = new ArrayList<>();

    @BeforeEach
    void faultRowsOfTwoRuns() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-faults-paged"));
        asked = ledger.runs().startRun("extraction", "f214", "{}", walk, List.of());
        RunId another = ledger.runs().startRun("extraction", "g214", "{}", walk, List.of());
        withNoFault = ledger.runs().startRun("extraction", "h214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < FAULTS; i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, "f" + i + ".pdf");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id DESC", Long.class, walk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement fault = connection.prepareStatement(
                    "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail)"
                            + " VALUES (?, ?, 'internal', 'set aside by this test')")) {
                for (Long id : ids) {
                    fault.setLong(1, id);
                    fault.setString(2, asked.value());
                    fault.addBatch();
                    inTheOrderWritten.add(new OccurrenceId(id));
                }
                for (int i = 0; i < ANOTHER_RUNS_FAULTS; i++) {
                    fault.setLong(1, ids.get(i));
                    fault.setString(2, another.value());
                    fault.addBatch();
                }
                fault.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A resumed extraction reads the faults it left a page at a time")
    @DisplayName("A stopped run's faults are handed over a thousand at a time, in the order they were written, each page before the next is read")
    void handsOverAPageAtATimeInTheOrderWritten() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        ExtractionFaults faults = new ExtractionFaults(log.jdbcTemplate());
        Recorder progress = new Recorder();
        List<List<OccurrenceId>> pages = new ArrayList<>();

        long handedOver = faults.eachPageOfFaulted(asked, progress, page -> {
            log.note(HANDED_OVER);
            pages.add(List.copyOf(page));
        });

        claim(
                "every one of the run's " + FAULTS + " faulted documents is handed over, and none of the other"
                        + " run's, and the count returned says so",
                () -> {
                    assertThat(handedOver).isEqualTo(FAULTS);
                    assertThat(pages.stream().flatMap(List::stream).toList())
                            .containsExactlyElementsOf(inTheOrderWritten);
                });
        claim(
                "in pages of " + A_PAGE + ", " + A_PAGE + " and the " + (FAULTS - 2 * A_PAGE) + " left, in the order"
                        + " the faults were written and not in the order of the documents",
                () -> assertThat(pages).extracting(List::size).containsExactly(A_PAGE, A_PAGE, FAULTS - 2 * A_PAGE));
        List<String> order = log.said().stream().map(FaultsAreReadAPageAtATimeTest::kind)
                .filter(kind -> !kind.isEmpty()).toList();
        claim(
                "each page is read by a statement of its own, and handed over before the next page is read: no"
                        + " more than one page is ever held",
                () -> assertThat(withoutATrailingEmptyPage(order))
                        .containsExactly(PAGE, HANDED_OVER, PAGE, HANDED_OVER, PAGE, HANDED_OVER));
        claim(
                "each page's statement goes through the run's own rows by the index on the run, from the last row"
                        + " read, and sorts nothing",
                () -> assertThat(log.said().stream().filter(sql -> kind(sql).equals(PAGE)).toList())
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .anyMatch(detail -> detail.contains("extraction_fault_by_run_id (run_id=? AND rowid>?)"))
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
        claim(
                "the caller is told the read is starting, over up to the " + FAULTS + " rows the run holds, then the"
                        + " rows read after each page, then that it ended, and never SQLite's steps",
                () -> assertThat(progress.calls)
                        .containsExactly(
                                "statementStarting(FAULTED_OCCURRENCES, " + OptionalLong.of(FAULTS) + ")",
                                "rowsRead(FAULTED_OCCURRENCES, 1000)",
                                "rowsRead(FAULTED_OCCURRENCES, 2000)",
                                "rowsRead(FAULTED_OCCURRENCES, 2500)",
                                "statementEnded(FAULTED_OCCURRENCES)"));
    }

    @Test
    @Story("A resumed extraction reads the faults it left a page at a time")
    @DisplayName("A run that left no fault hands nothing over, and says only that the read started with nothing to go through and ended")
    void aRunWithNoFaultHandsNothingOver() {
        ExtractionFaults faults = new ExtractionFaults(pool.jdbcTemplate());
        Recorder progress = new Recorder();
        List<List<OccurrenceId>> pages = new ArrayList<>();

        long handedOver = faults.eachPageOfFaulted(withNoFault, progress, pages::add);

        claim(
                "nothing is handed over and the count returned is nought",
                () -> {
                    assertThat(handedOver).isZero();
                    assertThat(pages).isEmpty();
                });
        claim(
                "the caller is told the read is starting with no total, and that it ended, and nothing between",
                () -> assertThat(progress.calls)
                        .containsExactly(
                                "statementStarting(FAULTED_OCCURRENCES, " + OptionalLong.empty() + ")",
                                "statementEnded(FAULTED_OCCURRENCES)"));
    }

    private static List<String> withoutATrailingEmptyPage(List<String> order) {
        List<String> trimmed = new ArrayList<>(order);
        if (!trimmed.isEmpty() && trimmed.getLast().equals(PAGE)) {
            trimmed.removeLast();
        }
        return trimmed;
    }

    private static String kind(String said) {
        if (said.equals(HANDED_OVER)) {
            return HANDED_OVER;
        }
        if (said.contains("FROM extraction_fault") && said.contains(" LIMIT ")) {
            return PAGE;
        }
        return "";
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }

    /** Every callback, in the order it came, written as the call it was; without {@code @Override}, as ADR-211's tests write a callback. */
    private static final class Recorder implements ExtractionStatementProgress {

        final List<String> calls = new ArrayList<>();

        public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
            calls.add("statementStarting(" + statement + ", " + rowsUpTo + ")");
        }

        public void stepsTaken(ExtractionStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        public void rowsRead(ExtractionStatement statement, long rows) {
            calls.add("rowsRead(" + statement + ", " + rows + ")");
        }

        public void statementEnded(ExtractionStatement statement) {
            calls.add("statementEnded(" + statement + ")");
        }
    }
}
