package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stage 3's document frequency reads stage 2's shingle rows a page of survivors at a time, in occurrence
 * order, and counts each hash's occurrences without a set of them (ADR-211 sections 1, 2 and 5).
 *
 * <p>Every statement the measurement makes is kept, in order, through {@link StatementLog}. At {@code
 * 4b99a03} the three pages of the survivors are read first, into a set, and then one read goes through every
 * shingle row of the run. After ADR-211 each page is followed by one read of that page's rows, by key and
 * ordered by occurrence.
 *
 * <p>The shingle rows are written so that no occurrence's rows lie together in the table: every occurrence's
 * shared hash first, then every occurrence's own hash, then the second copy of the shared hash that one
 * occurrence in seven carries. So a count that relied on the order rows were written in would be wrong, and
 * only rows read in occurrence order, each occurrence's together, can be counted by the last occurrence seen.
 * Fifty occurrences ruled out under stage 2 carry the same shared hashes, so counting one would show.
 */
@Epic("Redundancy")
@Feature("Content census")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class DocumentFrequencyReadsAPageOfSurvivorsAtATimeTest {

    /** Two full pages of the ledger's 1,000 and a short third. */
    private static final int SURVIVORS = 2_500;

    /** One occurrence in every 51 is ruled out under stage 2, so they lie among the survivors' ids. */
    private static final int ONE_IN = 51;

    /** The occurrences recorded: the survivors and one ruled out after every fifty of them. */
    private static final int RECORDED = SURVIVORS + SURVIVORS / (ONE_IN - 1);

    /** How many shared hashes there are: survivor j carries shared hash j modulo this, so each is carried two or three times. */
    private static final int SHARED_HASHES = 1_000;

    /** One survivor in seven carries its shared hash twice. */
    private static final int TWICE_IN = 7;

    /** Where each occurrence's own hash, carried by it alone, is numbered from. */
    private static final long OWN_HASHES_FROM = 10_000_000L;

    /** The most occurrences a page of the ledger holds, and so the most one read of rows may name. */
    private static final int A_PAGE = 1_000;

    private static final String PAGE = "a page of the survivors";
    private static final String ROWS = "the shingle rows";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private RunId stage2;
    private RunId stage3;

    /** For each shared hash, how many surviving occurrences carry it and how many times in all. */
    private final Map<Long, long[]> expected = new HashMap<>();

    @BeforeEach
    void survivorsWithInterleavedShingleRowsAndSomeRuledOut() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-frequency-pages"));
        stage2 = ledger.runs().startRun("extraction", "x211", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "y211", "{}", walk, List.of(stage2));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < RECORDED; i++) {
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
        List<long[]> shared = new ArrayList<>();
        List<long[]> own = new ArrayList<>();
        List<long[]> second = new ArrayList<>();
        List<Long> ruledOut = new ArrayList<>();
        int survivor = 0;
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            if (i % ONE_IN == ONE_IN - 1) {
                ruledOut.add(id);
                shared.add(new long[] {id, i % SHARED_HASHES});
                continue;
            }
            long hash = survivor % SHARED_HASHES;
            shared.add(new long[] {id, hash});
            own.add(new long[] {id, OWN_HASHES_FROM + survivor});
            long[] counts = expected.computeIfAbsent(hash, ignored -> new long[2]);
            counts[0]++;
            counts[1]++;
            if (survivor % TWICE_IN == 0) {
                second.add(new long[] {id, hash});
                counts[1]++;
            }
            survivor++;
        }
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement row = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)");
                    PreparedStatement verdict = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                for (List<long[]> pass : List.of(shared, own, second)) {
                    for (long[] shingle : pass) {
                        row.setLong(1, shingle[0]);
                        row.setString(2, stage2.value());
                        row.setString(3, ShingleParameters.DEFAULT.identity());
                        row.setLong(4, shingle[1]);
                        row.addBatch();
                    }
                }
                row.executeBatch();
                for (long id : ruledOut) {
                    verdict.setLong(1, id);
                    verdict.setString(2, stage2.value());
                    verdict.addBatch();
                }
                verdict.executeBatch();
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
    @Story("Stage 3 measures how often each passage recurs")
    @DisplayName("Document frequency reads the passages of one page of surviving documents at a time, in document order, and counts only theirs")
    void readsTheShingleRowsOfOnePageOfSurvivorsAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        JdbcTemplate logged = log.jdbcTemplate();

        new DocumentFrequency(logged, new Ledger(logged)).measure(stage3, stage2);

        Map<Long, long[]> written = new HashMap<>();
        jdbcTemplate.query(
                "SELECT shingle_hash, document_count, total_count FROM shingle_document_frequency WHERE run_id = ?",
                resultSet -> {
                    written.put(resultSet.getLong(1), new long[] {resultSet.getLong(2), resultSet.getLong(3)});
                },
                stage3.value());
        claim(
                "a row is written for each of the " + SHARED_HASHES + " shared passages and for no passage of one"
                        + " document alone",
                () -> assertThat(written.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet()));
        claim(
                "each row counts the surviving documents that carry its passage, and how many times in all, the"
                        + " second copy in one document in seven included and every ruled-out document left out,"
                        + " though no document's rows were written together",
                () -> assertThat(written).allSatisfy((hash, counts) -> assertThat(counts)
                        .as("the counts of passage %d", hash)
                        .containsExactly(expected.get(hash))));
        claim(
                "the measure of how many documents carry any passage at all is the " + SURVIVORS + " survivors,"
                        + " none of the ruled-out ones",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT shingled_document_count FROM shingle_corpus_size WHERE run_id = ?",
                                Long.class,
                                stage3.value()))
                        .isEqualTo(SURVIVORS));
        List<String> reads = log.said().stream()
                .map(DocumentFrequencyReadsAPageOfSurvivorsAtATimeTest::kind)
                .filter(kind -> !kind.isEmpty())
                .toList();
        claim(
                "each page of the survivors is followed by one read of that page's passages, before the next page"
                        + " is asked for: three of each, alternating, so no more than a page of survivors is ever"
                        + " held",
                () -> assertThat(reads).containsExactly(PAGE, ROWS, PAGE, ROWS, PAGE, ROWS));
        claim(
                "each read of the passages names the documents it wants, at most " + A_PAGE + " of them, and asks"
                        + " for their rows in document order, each document's together",
                () -> assertThat(log.said().stream().filter(sql -> kind(sql).equals(ROWS)).toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> {
                            assertThat(sql).contains("occurrence_id IN (").contains("ORDER BY occurrence_id");
                            assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE);
                        }));
    }

    private static String kind(String sql) {
        if (sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS") && sql.contains(" LIMIT ")) {
            return PAGE;
        }
        if (sql.contains("FROM shingle WHERE") && sql.contains("shingle_hash") && !sql.contains("(rowid)")) {
            return ROWS;
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
