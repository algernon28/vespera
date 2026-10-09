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
import java.util.LinkedHashMap;
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
 * <p>The shingle rows of stage 2's run are grouped into {@code shingle_document_frequency} by the database,
 * in one statement over every row of the run, keeping the hashes two or more occurrences carry. Then the
 * walk's occurrences are read from
 * the ledger a page of 1,000 at a time, each page asked of the ledger: what those that do not survive
 * contributed is taken off, a hash left with fewer than two is deleted, and the survivors that carry any
 * shingle are counted for each granularity, which is written once at the end. At {@code 4b99a03} every
 * survivor's id is read into a set, every shingle row of the run is brought into Java and counted in a map
 * with an entry for each distinct hash, and the rows are written from the map.
 *
 * <p>Every statement is kept, in order, through {@link StatementLog}. The shingle rows are written so that no
 * occurrence's rows lie together in the table, and fifty occurrences ruled out under stage 2 carry the same
 * shared hashes as the survivors, so a count that did not take them off would show. Five hashes and one
 * granularity are there for the ruled-out alone, each a case in which nothing may be left written; and an
 * earlier stage-2 run holds rows of its own for the same occurrences, which stage 3 must not count.
 *
 * <p>The claims about what is written hold at {@code 4b99a03} too: the counts are today's. The claims about
 * the statements are what fail there.
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

    /** The most occurrences a page of the ledger holds, and so the most one statement may name. */
    private static final int A_PAGE = 1_000;

    /** The 2,550 occurrences of the walk, read 1,000 at a time: two full pages and a short third. */
    private static final int PAGES_OF_THE_WALK = 3;

    /** How many shared hashes there are: survivor j carries shared hash j modulo this, so each is carried two or three times. */
    private static final int SHARED_HASHES = 1_000;

    /** One survivor in seven carries its shared hash twice. */
    private static final int TWICE_IN = 7;

    /** Where each occurrence's own hash, carried by it alone, is numbered from. */
    private static final long OWN_HASHES_FROM = 10_000_000L;

    /** Carried by the first survivor and by the first ruled-out occurrence: two carriers, one of them surviving. */
    private static final long ONE_SURVIVOR_AND_ONE_RULED_OUT = 20_000_000L;

    /** Carried by the first two ruled-out occurrences, both in the first page, and by no survivor. */
    private static final long TWO_RULED_OUT_OF_ONE_PAGE = 20_000_001L;

    /** Carried by the first ruled-out occurrence alone, so the grouping never writes it. */
    private static final long ONE_RULED_OUT_ALONE = 20_000_002L;

    /** Carried by the first ruled-out occurrence, in the first page, and by the last, in the third. */
    private static final long TWO_RULED_OUT_OF_DIFFERENT_PAGES = 20_000_003L;

    /** Carried by the first survivor and by those same two: three carriers, of which the third page leaves one. */
    private static final long A_SURVIVOR_AND_TWO_RULED_OUT_OF_DIFFERENT_PAGES = 20_000_004L;

    /** The one hash written under the granularity that only ruled-out occurrences carry. */
    private static final long UNDER_THE_OTHER_GRANULARITY = 30_000_000L;

    /** A second granularity, carried by two ruled-out occurrences and by no survivor. */
    private static final String A_GRANULARITY_ONLY_THE_RULED_OUT_CARRY = "a-granularity-only-the-ruled-out-carry";

    private static final String GROUPING = "the grouping of the run's shingle rows";
    private static final String PAGE = "a page of the walk's occurrences";
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
        RunId anEarlierStage2 = ledger.runs().startRun("extraction", "x210", "{}", walk, List.of());
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
        long firstSurvivor = ids.getFirst();
        long firstRuledOut = ruledOut.get(0);
        long secondRuledOut = ruledOut.get(1);
        long lastRuledOut = ruledOut.getLast();
        List<long[]> forTheRuledOutAlone = List.of(
                new long[] {firstSurvivor, ONE_SURVIVOR_AND_ONE_RULED_OUT},
                new long[] {firstRuledOut, ONE_SURVIVOR_AND_ONE_RULED_OUT},
                new long[] {firstRuledOut, TWO_RULED_OUT_OF_ONE_PAGE},
                new long[] {secondRuledOut, TWO_RULED_OUT_OF_ONE_PAGE},
                new long[] {firstRuledOut, ONE_RULED_OUT_ALONE},
                new long[] {firstRuledOut, TWO_RULED_OUT_OF_DIFFERENT_PAGES},
                new long[] {lastRuledOut, TWO_RULED_OUT_OF_DIFFERENT_PAGES},
                new long[] {firstSurvivor, A_SURVIVOR_AND_TWO_RULED_OUT_OF_DIFFERENT_PAGES},
                new long[] {firstRuledOut, A_SURVIVOR_AND_TWO_RULED_OUT_OF_DIFFERENT_PAGES},
                new long[] {lastRuledOut, A_SURVIVOR_AND_TWO_RULED_OUT_OF_DIFFERENT_PAGES});
        claim(
                "the fixture puts the first two ruled-out documents in the first page of " + A_PAGE + " and the"
                        + " last in the third, so the cases about one page and about different pages are those",
                () -> {
                    assertThat(ids.indexOf(firstRuledOut)).isLessThan(A_PAGE);
                    assertThat(ids.indexOf(secondRuledOut)).isLessThan(A_PAGE);
                    assertThat(ids.indexOf(lastRuledOut)).isGreaterThanOrEqualTo(2 * A_PAGE);
                });
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement row = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)");
                    PreparedStatement verdict = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                for (long id : ids) {
                    row.setLong(1, id);
                    row.setString(2, anEarlierStage2.value());
                    row.setString(3, ShingleParameters.DEFAULT.identity());
                    row.setLong(4, ONE_RULED_OUT_ALONE);
                    row.addBatch();
                }
                for (List<long[]> written : List.of(shared, own, second, forTheRuledOutAlone)) {
                    for (long[] shingle : written) {
                        row.setLong(1, shingle[0]);
                        row.setString(2, stage2.value());
                        row.setString(3, ShingleParameters.DEFAULT.identity());
                        row.setLong(4, shingle[1]);
                        row.addBatch();
                    }
                }
                for (long id : List.of(firstRuledOut, secondRuledOut)) {
                    row.setLong(1, id);
                    row.setString(2, stage2.value());
                    row.setString(3, A_GRANULARITY_ONLY_THE_RULED_OUT_CARRY);
                    row.setLong(4, UNDER_THE_OTHER_GRANULARITY);
                    row.addBatch();
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
        List<String> granularitiesWritten = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT shingle_parameter_identity, shingle_hash, document_count, total_count"
                        + " FROM shingle_document_frequency WHERE run_id = ?",
                resultSet -> {
                    granularitiesWritten.add(resultSet.getString(1));
                    written.put(resultSet.getLong(2), new long[] {resultSet.getLong(3), resultSet.getLong(4)});
                },
                stage3.value());
        Map<String, Long> leftWithNoRow = new LinkedHashMap<>();
        leftWithNoRow.put(
                "a passage one surviving document and one ruled-out document carry: one survivor is fewer than two",
                ONE_SURVIVOR_AND_ONE_RULED_OUT);
        leftWithNoRow.put(
                "a passage only two ruled-out documents of one page carry", TWO_RULED_OUT_OF_ONE_PAGE);
        leftWithNoRow.put(
                "a passage one ruled-out document carries and nothing else under this run does, though every document"
                        + " carries it under an earlier run",
                ONE_RULED_OUT_ALONE);
        leftWithNoRow.put(
                "a passage only two ruled-out documents carry, one in the first page and one in the last",
                TWO_RULED_OUT_OF_DIFFERENT_PAGES);
        leftWithNoRow.put(
                "a passage one surviving document and two ruled-out documents of different pages carry",
                A_SURVIVOR_AND_TWO_RULED_OUT_OF_DIFFERENT_PAGES);
        leftWithNoRow.put(
                "a passage only ruled-out documents carry, under a granularity no surviving document has",
                UNDER_THE_OTHER_GRANULARITY);
        leftWithNoRow.forEach((what, hash) -> claim(
                "no row is left for " + what,
                () -> assertThat(written).as("the rows written, by passage").doesNotContainKey(hash)));
        claim(
                "a row is written for each of the " + SHARED_HASHES + " shared passages and for no other: none for a"
                        + " passage of one document alone",
                () -> assertThat(written.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet()));
        claim(
                "and every row is under the granularity the surviving documents carry",
                () -> assertThat(granularitiesWritten).containsOnly(ShingleParameters.DEFAULT.identity()));
        claim(
                "each row counts the surviving documents that carry its passage, and how many times in all: the"
                        + " second copy in one document in seven included, the ruled-out documents' copies, each"
                        + " of them carried twice, taken off, and the earlier run's rows never counted",
                () -> assertThat(written).allSatisfy((hash, counts) -> assertThat(counts)
                        .as("the counts of passage %d", hash)
                        .containsExactly(expected.get(hash))));
        claim(
                "the measure of how many documents carry any passage at all is written for one granularity only,"
                        + " the " + SURVIVORS + " survivors under theirs: a granularity only ruled-out documents"
                        + " carry gets no row, not a row of zero",
                () -> assertThat(jdbcTemplate.query(
                                "SELECT shingle_parameter_identity || ' ' || shingled_document_count"
                                        + " FROM shingle_corpus_size WHERE run_id = ?",
                                (resultSet, rowNumber) -> resultSet.getString(1),
                                stage3.value()))
                        .containsExactly(ShingleParameters.DEFAULT.identity() + " " + SURVIVORS));

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
        claim(
                "and no page is read from the passages' own table, which holds every run's rows for a document"
                        + " side by side",
                () -> assertThat(said).noneMatch(sql -> sql.startsWith("SELECT") && sql.contains("FROM shingle")
                        && sql.contains(" LIMIT ")));
        List<String> order = said.stream()
                .map(DocumentFrequencyIsCountedInTheDatabaseTest::kind)
                .filter(kind -> !kind.isEmpty())
                .toList();
        List<String> expectedOrder = new ArrayList<>(List.of(GROUPING));
        for (int page = 0; page < PAGES_OF_THE_WALK; page++) {
            expectedOrder.add(PAGE);
            expectedOrder.add(ASK);
        }
        claim(
                "one statement groups the run's passages, and only one; then the collection's documents are read"
                        + " from the ledger a thousand at a time, each page followed by one question to the"
                        + " ledger about which of them survive",
                () -> assertThat(order).containsExactlyElementsOf(expectedOrder));
        claim(
                "the grouping is over every passage of the run: it names the run and no range of passages",
                () -> assertThat(said.stream().filter(sql -> kind(sql).equals(GROUPING)).toList())
                        .singleElement()
                        .satisfies(sql -> assertThat(sql)
                                .contains("WHERE run_id = ?")
                                .doesNotContain("shingle_hash >")
                                .doesNotContain("shingle_hash <")
                                .doesNotContain("BETWEEN")));
        List<String> countsOfAPage = said.stream()
                .filter(sql -> sql.startsWith("SELECT shingle_parameter_identity, COUNT(DISTINCT occurrence_id) FROM shingle"))
                .toList();
        claim(
                "the surviving documents that carry any passage are counted once for each of the "
                        + PAGES_OF_THE_WALK + " pages, each count naming at most " + A_PAGE + " documents and"
                        + " finding their rows by document and run",
                () -> assertThat(countsOfAPage)
                        .hasSize(PAGES_OF_THE_WALK)
                        .allSatisfy(sql -> {
                            assertThat(sql).contains("run_id = ?").contains("occurrence_id IN (");
                            assertThat(sql.chars().filter(character -> character == '?').count() - 1)
                                    .as("the documents one count names, its other argument being the run")
                                    .isBetween(1L, (long) A_PAGE);
                        }));
    }

    private static String kind(String sql) {
        if (sql.startsWith("INSERT INTO shingle_document_frequency") && sql.contains("FROM shingle")
                && sql.contains("GROUP BY") && !sql.contains("occurrence_id IN (")) {
            return GROUPING;
        }
        if (sql.contains("FROM file_occurrence") && sql.contains(" LIMIT ") && !sql.contains("NOT EXISTS")) {
            return PAGE;
        }
        if (sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS") && sql.contains("id IN (")
                && !sql.contains(" LIMIT ")) {
            return ASK;
        }
        return "";
    }
}
