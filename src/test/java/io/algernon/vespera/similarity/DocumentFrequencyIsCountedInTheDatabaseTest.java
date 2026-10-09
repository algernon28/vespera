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
 * Stage 3's document frequency is counted in the database, by {@code similarity} in its own tables, and no
 * class holds a map of the corpus's distinct hashes or a set of its survivors (ADR-211 sections 1 and 3).
 *
 * <p>One statement groups every shingle row of stage 2's run into {@code shingle_document_frequency}, keeping
 * the hashes two or more occurrences carry, and one more counts each granularity's shingled occurrences into
 * {@code shingle_corpus_size}. Then the run's shingled occurrences are read a page of 1,000 at a time, each
 * page asked of the ledger, and what those that do not survive contributed is taken off. At {@code 4b99a03}
 * every survivor's id is read into a set, every shingle row of the run is brought into Java and counted in a
 * map with an entry for each distinct hash, and the rows are written from the map.
 *
 * <p>Every statement is kept, in order, through {@link StatementLog}. The shingle rows are written so that no
 * occurrence's rows lie together in the table, and fifty occurrences ruled out under stage 2 carry the same
 * shared hashes as the survivors, so a count that did not take them off would show.
 */
@Epic("Redundancy")
@Feature("Content census")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class DocumentFrequencyIsCountedInTheDatabaseTest {

    /** Two full pages of 1,000 and a short third, once the ruled-out ones are counted in. */
    private static final int SURVIVORS = 2_500;

    /** One occurrence in every 51 is ruled out under stage 2, so they lie among the survivors' ids. */
    private static final int ONE_IN = 51;

    /** The occurrences recorded, every one of them shingled: the survivors and one ruled out after every fifty. */
    private static final int RECORDED = SURVIVORS + SURVIVORS / (ONE_IN - 1);

    /** How many shared hashes there are: survivor j carries shared hash j modulo this, so each is carried two or three times. */
    private static final int SHARED_HASHES = 1_000;

    /** One survivor in seven carries its shared hash twice. */
    private static final int TWICE_IN = 7;

    /** Where each occurrence's own hash, carried by it alone, is numbered from. */
    private static final long OWN_HASHES_FROM = 10_000_000L;

    /** The 2,550 shingled occurrences, read 1,000 at a time: two full pages and a short third. */
    private static final int PAGES_OF_SHINGLED_OCCURRENCES = 3;

    private static final String GROUPING = "the grouping of the run's shingle rows";
    private static final String PAGE = "a page of the run's shingled occurrences";
    private static final String ASK = "a question to the ledger about one page";

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
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-frequency-counted"));
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
                second.add(new long[] {id, i % SHARED_HASHES});
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
                for (List<long[]> written : List.of(shared, own, second)) {
                    for (long[] shingle : written) {
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
    @DisplayName("Document frequency is counted by the database over the run's passages, and what documents that did not survive contributed is taken off")
    void countedInTheDatabaseWithTheRuledOutTakenOff() {
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
                "each row counts the surviving documents that carry its passage, and how many times in all: the"
                        + " second copy in one document in seven included, and the ruled-out documents' copies,"
                        + " each of them carried twice, taken off",
                () -> assertThat(written).allSatisfy((hash, counts) -> assertThat(counts)
                        .as("the counts of passage %d", hash)
                        .containsExactly(expected.get(hash))));
        claim(
                "the measure of how many documents carry any passage at all is the " + SURVIVORS + " survivors,"
                        + " the ruled-out ones taken off",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT shingled_document_count FROM shingle_corpus_size WHERE run_id = ?",
                                Long.class,
                                stage3.value()))
                        .isEqualTo(SURVIVORS));

        List<String> said = log.said();
        claim(
                "no statement brings the run's passages out of the database: none selects the passages' hashes"
                        + " for the application to count",
                () -> assertThat(said).noneMatch(sql -> sql.startsWith("SELECT") && sql.contains("FROM shingle")
                        && sql.contains("shingle_hash")));
        claim(
                "the surviving documents are never read as such: no page of them is asked for",
                () -> assertThat(said).noneMatch(sql -> sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS")
                        && sql.contains(" LIMIT ")));
        List<String> order = said.stream()
                .map(DocumentFrequencyIsCountedInTheDatabaseTest::kind)
                .filter(kind -> !kind.isEmpty())
                .toList();
        List<String> expectedOrder = new ArrayList<>(List.of(GROUPING));
        for (int page = 0; page < PAGES_OF_SHINGLED_OCCURRENCES; page++) {
            expectedOrder.add(PAGE);
            expectedOrder.add(ASK);
        }
        claim(
                "one statement groups the run's passages, over every one of its rows; then the shingled documents"
                        + " are read a thousand at a time, each page followed by one question to the ledger about"
                        + " which of them survive",
                () -> assertThat(order).containsExactlyElementsOf(expectedOrder));
        claim(
                "each page of shingled documents goes on from the last one read, through the index on the"
                        + " document, and sorts nothing",
                () -> assertThat(said.stream().filter(sql -> kind(sql).equals(PAGE)).toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> {
                            List<String> plan = jdbcTemplate.query(
                                    "EXPLAIN QUERY PLAN " + sql,
                                    (resultSet, rowNumber) -> resultSet.getString("detail"),
                                    stage2.value(),
                                    Long.MIN_VALUE);
                            assertThat(plan).anyMatch(detail -> detail.contains("shingle_by_occurrence"));
                            assertThat(plan).noneMatch(detail -> detail.contains("TEMP B-TREE"));
                        }));
    }

    private static String kind(String sql) {
        if (sql.startsWith("INSERT INTO shingle_document_frequency") && sql.contains("FROM shingle")
                && sql.contains("GROUP BY") && !sql.contains("occurrence_id IN (")) {
            return GROUPING;
        }
        if (sql.startsWith("SELECT") && sql.contains("FROM shingle") && sql.contains(" LIMIT ")) {
            return PAGE;
        }
        if (sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS") && sql.contains("id IN (")
                && !sql.contains(" LIMIT ")) {
            return ASK;
        }
        return "";
    }
}
