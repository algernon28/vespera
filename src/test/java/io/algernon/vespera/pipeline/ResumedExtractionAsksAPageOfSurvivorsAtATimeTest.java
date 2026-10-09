package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A resumed stage 2 reads what its stopped run has still to read a page of survivors at a time, asking for each
 * page which of its documents the run already measured, and holds no set of them (ADR-214 section 2). Until
 * ADR-214 the reader read every document the run had measured into a set, and the step's counter read it again.
 *
 * <p>2,500 documents of one walk under one run of stage 2: the first 1,200, in id order, measured by the stopped
 * run's committed chunks, as a stop part-way leaves them; and one in every 26 of the rest ruled out under the run,
 * so that 2,450 survive in three pages of the ledger's 1,000 and 1,250 are still to read.
 *
 * <p>It names the constructor and the count ADR-214 gives {@code UnrecordedOccurrences}, and the method it adds
 * to {@code ExtractionMetrics}, so the test tree does not compile until they exist.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class ResumedExtractionAsksAPageOfSurvivorsAtATimeTest {

    /** The documents of the walk. */
    private static final int OCCURRENCES = 2_500;

    /** The first so many, in id order, measured before the run stopped. */
    private static final int MEASURED_BEFORE_THE_STOP = 1_200;

    /** One in every so many of the documents not measured is ruled out under the run. */
    private static final int ONE_IN = 26;

    /** Ruled out: one in 26 of the 1,300 not measured. */
    private static final int RULED_OUT = (OCCURRENCES - MEASURED_BEFORE_THE_STOP) / ONE_IN;

    /** What is still to read: the 1,300 not measured, less the 50 ruled out. */
    private static final int STILL_TO_READ = OCCURRENCES - MEASURED_BEFORE_THE_STOP - RULED_OUT;

    /** The most documents one ask may name: the ledger's page. */
    private static final int A_PAGE = 1_000;

    private static final String PAGE = "a page of the survivors";
    private static final String ASK = "an ask of the page's metric rows";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId stopped;
    private final List<OccurrenceId> stillToRead = new ArrayList<>();

    @BeforeEach
    void aStoppedRunThatMeasuredTheFirstOccurrences() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-resumed"));
        stopped = ledger.runs().startRun("extraction", "r214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < OCCURRENCES; i++) {
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
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metric = connection.prepareStatement(
                            "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time,"
                                    + " character_count, alphanumeric_char_count, word_count,"
                                    + " word_character_length_total, vowelless_word_count, single_character_word_count)"
                                    + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)");
                    PreparedStatement ruledOut = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                for (int i = 0; i < ids.size(); i++) {
                    if (i < MEASURED_BEFORE_THE_STOP) {
                        metric.setLong(1, ids.get(i));
                        metric.setString(2, stopped.value());
                        metric.addBatch();
                    } else if ((i - MEASURED_BEFORE_THE_STOP) % ONE_IN == ONE_IN - 1) {
                        ruledOut.setLong(1, ids.get(i));
                        ruledOut.setString(2, stopped.value());
                        ruledOut.addBatch();
                    } else {
                        stillToRead.add(new OccurrenceId(ids.get(i)));
                    }
                }
                metric.executeBatch();
                ruledOut.executeBatch();
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
    @Story("A resumed extraction asks which documents it already read")
    @DisplayName("A resumed extraction yields the documents it has still to read, asking a page of surviving documents at a time which it already read")
    void yieldsWhatIsStillToReadAskingAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        Ledger ledger = new Ledger(log.jdbcTemplate());
        ExtractionMetrics metrics = new ExtractionMetrics(log.jdbcTemplate(), new LanguageDetection());

        List<OccurrenceId> yielded = new ArrayList<>();
        for (OccurrenceId occurrence :
                new UnrecordedOccurrences(
                        ledger.verdicts().survivors(stopped), page -> metrics.recordedAmong(stopped, page))) {
            yielded.add(occurrence);
        }

        claim(
                "it yields the " + STILL_TO_READ + " surviving documents the stopped run had not read, in the order"
                        + " of their numbers, and none of the " + MEASURED_BEFORE_THE_STOP + " it had",
                () -> assertThat(yielded).containsExactlyElementsOf(stillToRead));
        claim(
                "each page of the surviving documents is followed by one ask of that page's metric rows, before the"
                        + " next page is read: three of each, so no more than a page is ever held",
                () -> assertThat(kinds(log)).containsExactly(PAGE, ASK, PAGE, ASK, PAGE, ASK));
        claim(
                "each ask names the documents it asks about, at most " + A_PAGE + ", and no statement reads the"
                        + " run's metric rows without naming documents",
                () -> assertThat(log.said().stream().filter(sql -> sql.contains("FROM extraction_metric")).toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> {
                            assertThat(sql).contains("occurrence_id IN (");
                            assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE);
                        }));
    }

    @Test
    @Story("A resumed extraction asks which documents it already read")
    @DisplayName("The count a resumed extraction reports its progress against is taken the same way, a page at a time")
    void countsWhatIsStillToReadAskingAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        Ledger ledger = new Ledger(log.jdbcTemplate());
        ExtractionMetrics metrics = new ExtractionMetrics(log.jdbcTemplate(), new LanguageDetection());

        long count = UnrecordedOccurrences.countOver(ledger, stopped, page -> metrics.recordedAmong(stopped, page));

        claim(
                "the count is the " + STILL_TO_READ + " surviving documents the stopped run had not read",
                () -> assertThat(count).isEqualTo(STILL_TO_READ));
        claim(
                "taken a page of surviving documents at a time, each page followed by one ask of its metric rows",
                () -> assertThat(kinds(log)).containsExactly(PAGE, ASK, PAGE, ASK, PAGE, ASK));
    }

    private static List<String> kinds(StatementLog log) {
        return log.said().stream().map(ResumedExtractionAsksAPageOfSurvivorsAtATimeTest::kind)
                .filter(kind -> !kind.isEmpty()).toList();
    }

    private static String kind(String sql) {
        if (sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS") && sql.contains(" LIMIT ")) {
            return PAGE;
        }
        if (sql.contains("FROM extraction_metric")) {
            return ASK;
        }
        return "";
    }

    /** The placeholders inside the statement's list of occurrences, and no other. */
    private static int occurrencesNamedBy(String sql) {
        int from = sql.indexOf("occurrence_id IN (") + "occurrence_id IN (".length();
        String list = sql.substring(from, sql.indexOf(')', from));
        return (int) list.chars().filter(character -> character == '?').count();
    }
}
