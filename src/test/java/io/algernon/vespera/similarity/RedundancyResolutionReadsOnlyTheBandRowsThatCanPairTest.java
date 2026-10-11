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
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A page of signed occurrences is handed, of each band value it shares, only the rows whose occurrence is
 * greater than the least of the page in it, the only rows that can make a pair with the page there (ADR-239).
 * Until ADR-239 every page with an occurrence in a band value was handed every row of it, those of occurrences
 * no greater than any of the page's included, and a page whose only occurrence in a band value was the
 * greatest of it was handed the whole band value for nothing.
 *
 * <p><b>The ledger is written by hand</b>, signatures and band rows included, as {@code
 * RedundancyResolutionBoundsItsCandidatesTest}'s is, so that which occurrences share a band value is this
 * file's to state:
 *
 * <ul>
 *   <li>a near-duplicate pair sharing one band value, the lesser recorded first and the greater last, so that
 *       the greater is in the last page of signed occurrences and is the greatest occurrence of its band value;
 *   <li>between them, 2,100 signed occurrences sharing another band value and nothing else, in three pages;
 *   <li>one occurrence of 40 shingles held whole by three signed others, so that containment has something to
 *       find in the same resolution.
 * </ul>
 *
 * <p>The first test passed before ADR-239 and passes with it: the verdicts are the same. The second failed
 * until it was built. Both read one resolution, made once for the class.
 */
@Epic("Redundancy")
@Feature("Resolution")
@Link(name = "ADR-239", url = Adr.A_PAGE_READS_OF_A_BAND_VALUE_ONLY_THE_ROWS_THAT_CAN_PAIR_WITH_IT, type = "adr")
@Link(name = "ADR-225", url = Adr.STAGE_4B_READS_ITS_CANDIDATES_A_THOUSAND_AT_A_TIME, type = "adr")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedundancyResolutionReadsOnlyTheBandRowsThatCanPairTest {

    /** The most rows one statement takes, and the size of a page of signed occurrences. */
    private static final int A_PAGE = 1_000;

    /** Signed occurrences sharing one band value and nothing else: more than two pages of them. */
    private static final int SHARING_ONE_BAND_VALUE = 2_100;

    /**
     * Where the first of them stands in each page of signed occurrences: the lesser of the pair is the first
     * signed occurrence, so the first page holds 999 of them, the second 1,000 and the third the rest.
     */
    private static final int FIRST_IN_THE_FIRST_PAGE = 0;

    private static final int FIRST_IN_THE_SECOND_PAGE = A_PAGE - 1;

    private static final int FIRST_IN_THE_THIRD_PAGE = 2 * A_PAGE - 1;

    /**
     * Rows of that band value a page can pair with its own occurrences: those recorded after the first of the
     * page in it, 2,099, 1,100 and 100.
     */
    private static final int ROWS_THAT_CAN_PAIR_IN_THE_FAMILY = (SHARING_ONE_BAND_VALUE - 1 - FIRST_IN_THE_FIRST_PAGE)
            + (SHARING_ONE_BAND_VALUE - 1 - FIRST_IN_THE_SECOND_PAGE)
            + (SHARING_ONE_BAND_VALUE - 1 - FIRST_IN_THE_THIRD_PAGE);

    /** The band value the 2,100 share, in the first band. */
    private static final int FAMILY_BAND = 0;

    private static final long FAMILY_BAND_VALUE = 1L;

    /** The band value the pair shares, in another band. */
    private static final int PAIR_BAND = 5;

    private static final long PAIR_BAND_VALUE = 50L;

    /** Pages of signed occurrences that hold an occurrence of that band value. */
    private static final int PAGES_WITH_THE_FAMILY = 3;

    /** The pair's band value: the first page can pair its lesser with the greater; the last page nothing. */
    private static final int ROWS_THAT_CAN_PAIR_OF_THE_PAIR = 1;

    /** The pair's two rows, read once by each of the two pages that hold one of them. */
    private static final int ROWS_OF_THE_PAIR_READ_BY_EACH_PAGE = 2 * 2;

    /** Shingles of each of the pair; the two share 19 of the 21 between them. */
    private static final int PAIR_SHINGLES = 20;

    private static final double THE_PAIRS_JACCARD = 19.0 / 21.0;

    /** The contained occurrence's whole shingle set, each one held by every container. */
    private static final int CONTAINED_SHINGLES = 40;

    private static final int CONTAINERS = 3;

    private static final int OWN_SHINGLES_OF_A_CONTAINER = 20;

    /** Shingles of each occurrence sharing the one band value, each its own. */
    private static final int OWN_SHINGLES = 10;

    private static final int REMOVED = 2;

    private static final int BANDS = 16;

    private static final long LESS_TEXT = 1_000L;

    private static final long MORE_TEXT = 3_000L;

    /** What a plan says where the statement keeps rows in temporary storage to sort them. */
    private static final String SORTS = "TEMP B-TREE";

    /** A value no run, band value or occurrence has, bound wherever a plan below is asked for. */
    private static final long NO_SUCH_VALUE = 0L;

    private PoolOfTwo pool;
    private RunId stage2;
    private RunId stage3;
    private RunId stage4;
    private List<String> said;
    private final Map<Bucket, Integer> rowsHandedBack = new LinkedHashMap<>();
    private final List<Planted> planted = new ArrayList<>();
    private List<Long> ids;

    private int lesserOfThePair;
    private int greaterOfThePair;
    private int contained;
    private int firstContainer;

    @BeforeAll
    void resolveALedgerWrittenByHand(@TempDir Path folder) throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-prepared-once"));
        stage2 = ledger.runs().startRun("extraction", "s2-237", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "s3-237", "{}", walk, List.of(stage2));
        stage4 = ledger.runs().startRun("content-redundancy", "s4-237", "{}", walk, List.of(stage3));

        plant();
        write(walk, jdbcTemplate);
        new TransactionTemplate(new JdbcTransactionManager(jdbcTemplate.getDataSource()))
                .executeWithoutResult(status -> new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2));
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(stage2.value()));

        StatementLog log = new StatementLog(jdbcTemplate.getDataSource());
        CountingBandValueRows counting = new CountingBandValueRows(log.jdbcTemplate().getDataSource());
        new RedundancyResolution(counting, new Ledger(counting))
                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(counting));
        said = log.said();
    }

    @AfterAll
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Redundancy resolution reads of a shared band value only what can pair")
    @DisplayName("The documents removed, what each is redundant with, and the score written are what they were before")
    void removesWhatItRemovedBefore() {
        List<String> removed = pool.jdbcTemplate()
                .query(
                        "SELECT occurrence_id, redundant_with_occurrence_id, relation, score FROM redundant_with"
                                + " WHERE run_id = ? ORDER BY occurrence_id",
                        (resultSet, rowNumber) -> resultSet.getLong(1) + " -> " + resultSet.getLong(2) + " "
                                + resultSet.getString(3) + " " + resultSet.getDouble(4),
                        stage4.value());

        claim(
                "the one with less text of two documents sharing 19 word sequences of 21 is removed as a near-duplicate"
                        + " of the other, which is the last document recorded and in the last page read, and the"
                        + " document whose " + CONTAINED_SHINGLES + " word sequences " + CONTAINERS + " others hold"
                        + " whole is removed as contained in the first of them, and nothing else is removed",
                () -> assertThat(removed)
                        .containsExactly(
                                id(lesserOfThePair) + " -> " + id(greaterOfThePair) + " near-duplicate " + THE_PAIRS_JACCARD,
                                id(contained) + " -> " + id(firstContainer) + " contained-in 1.0"));
        claim(
                "each of the " + REMOVED + " carries the verdict that removes it, and no other document does",
                () -> assertThat(pool.jdbcTemplate()
                                .queryForList(
                                        "SELECT occurrence_id FROM verdict WHERE run_id = ? AND kind = 'REDUNDANT_WITH'"
                                                + " ORDER BY occurrence_id",
                                        Long.class,
                                        stage4.value()))
                        .containsExactly(id(lesserOfThePair), id(contained)));
    }

    @Test
    @Story("Redundancy resolution reads of a shared band value only what can pair")
    @DisplayName("A page of documents reads of a shared band value only the documents recorded after its own first one there, the only ones that can pair with it")
    void aBandValueIsReadOnlyAboveTheLeastOccurrenceOfThePage() {
        Bucket family = new Bucket(FAMILY_BAND, FAMILY_BAND_VALUE);
        Bucket pair = new Bucket(PAIR_BAND, PAIR_BAND_VALUE);

        claim(
                "of the band value " + SHARING_ONE_BAND_VALUE + " documents share, over three pages, each page is"
                        + " handed only the rows of documents recorded after its first document there, the only"
                        + " ones a document of the page can be the lesser of a pair with: " + ROWS_THAT_CAN_PAIR_IN_THE_FAMILY
                        + " rows in all, where every row for each page was "
                        + PAGES_WITH_THE_FAMILY * SHARING_ONE_BAND_VALUE,
                () -> assertThat(rowsHandedBack.getOrDefault(family, 0)).isEqualTo(ROWS_THAT_CAN_PAIR_IN_THE_FAMILY));
        claim(
                "and of the band value the pair shares, the first page is handed the greater document alone, and"
                        + " the last page, whose document is the greatest there, is handed none: "
                        + ROWS_THAT_CAN_PAIR_OF_THE_PAIR + " row where both rows for both pages were "
                        + ROWS_OF_THE_PAIR_READ_BY_EACH_PAGE,
                () -> assertThat(rowsHandedBack.getOrDefault(pair, 0)).isEqualTo(ROWS_THAT_CAN_PAIR_OF_THE_PAIR));
        claim(
                "and each read of a band value goes through the index on the band values, on from the last row read,"
                        + " and sorts nothing",
                () -> assertThat(said.stream()
                                .filter(sql -> sql.contains("FROM signature_band") && sql.contains("band_hash = ?")
                                        && sql.contains("rowid > ?"))
                                .distinct()
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(planWithNoValueOfTheLedger(sql))
                                .anyMatch(step -> step.contains("signature_band_by_bucket")
                                        && step.contains("band_hash=?") && step.contains("rowid>?"))
                                .noneMatch(step -> step.contains(SORTS))));
    }

    // -- The ledger ---------------------------------------------------------------------------------

    /** One occurrence to write: its shingles, its text, whether it is signed, and the band values it shares. */
    private record Planted(Set<Long> shingles, long text, boolean signed, long[] sharedBands) {}

    /** One band value, as the reads of it name it: the band and the value. */
    private record Bucket(int ordinal, long hash) {}

    private long id(int plantedAt) {
        return ids.get(plantedAt);
    }

    /** Puts the occurrences in the order they are recorded and signed in, which is the order of their ids. */
    private void plant() {
        lesserOfThePair = add(hashes(6_000, PAIR_SHINGLES), LESS_TEXT, band(PAIR_BAND, PAIR_BAND_VALUE));
        for (int member = 0; member < SHARING_ONE_BAND_VALUE; member++) {
            add(hashes(1_000_000L + member * 100L, OWN_SHINGLES), LESS_TEXT, band(FAMILY_BAND, FAMILY_BAND_VALUE));
        }
        Set<Long> held = hashes(1_000, CONTAINED_SHINGLES);
        contained = add(held, LESS_TEXT);
        firstContainer = add(with(held, hashes(10_000, OWN_SHINGLES_OF_A_CONTAINER)), LESS_TEXT);
        for (int container = 1; container < CONTAINERS; container++) {
            add(with(held, hashes(10_000 + container * 100L, OWN_SHINGLES_OF_A_CONTAINER)), LESS_TEXT);
        }
        greaterOfThePair = add(hashes(6_001, PAIR_SHINGLES), MORE_TEXT, band(PAIR_BAND, PAIR_BAND_VALUE));
    }

    private int add(Set<Long> shingles, long text, long[]... sharedBands) {
        long[] shared = new long[BANDS];
        for (long[] bands : sharedBands) {
            for (int band = 0; band < BANDS; band++) {
                if (bands[band] != 0) {
                    shared[band] = bands[band];
                }
            }
        }
        planted.add(new Planted(shingles, text, true, shared));
        return planted.size() - 1;
    }

    private static long[] band(int band, long value) {
        long[] shared = new long[BANDS];
        shared[band] = value;
        return shared;
    }

    private static Set<Long> hashes(long from, int count) {
        Set<Long> hashes = new LinkedHashSet<>();
        for (long i = 0; i < count; i++) {
            hashes.add(from + i);
        }
        return hashes;
    }

    private static Set<Long> with(Set<Long> some, Set<Long> more) {
        Set<Long> both = new LinkedHashSet<>(some);
        both.addAll(more);
        return both;
    }

    private void write(WalkId walk, JdbcTemplate jdbcTemplate) throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2025-01-01T00:00:00Z')")) {
                for (int i = 0; i < planted.size(); i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, String.format("d%05d.pdf", i));
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement shingle = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)");
                    PreparedStatement metric = connection.prepareStatement(
                            "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time,"
                                    + " character_count, alphanumeric_char_count, word_count,"
                                    + " word_character_length_total, vowelless_word_count, single_character_word_count)"
                                    + " VALUES (?, ?, 'success', 0.5, ?, ?, 1, 1, 0, 0)");
                    PreparedStatement signature = connection.prepareStatement(
                            "INSERT INTO minhash_signature (occurrence_id, run_id, signature_identity, signature)"
                                    + " VALUES (?, ?, 'written-by-hand', ?)");
                    PreparedStatement band = connection.prepareStatement(
                            "INSERT INTO signature_band (occurrence_id, run_id, band_ordinal, band_hash)"
                                    + " VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < planted.size(); i++) {
                    Planted one = planted.get(i);
                    for (long hash : one.shingles()) {
                        shingle.setLong(1, ids.get(i));
                        shingle.setString(2, stage2.value());
                        shingle.setString(3, ShingleParameters.DEFAULT.identity());
                        shingle.setLong(4, hash);
                        shingle.addBatch();
                    }
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, stage2.value());
                    metric.setLong(3, one.text());
                    metric.setLong(4, one.text());
                    metric.addBatch();
                    signature.setLong(1, ids.get(i));
                    signature.setString(2, stage4.value());
                    signature.setBytes(3, new byte[] {0});
                    signature.addBatch();
                    for (int ordinal = 0; ordinal < BANDS; ordinal++) {
                        band.setLong(1, ids.get(i));
                        band.setString(2, stage4.value());
                        band.setInt(3, ordinal);
                        // A band value no other occurrence has, unless one is shared: far above the shared ones.
                        band.setLong(4, one.sharedBands()[ordinal] != 0 ? one.sharedBands()[ordinal] : 1_000_000L + i * 100L + ordinal);
                        band.addBatch();
                    }
                }
                shingle.executeBatch();
                metric.executeBatch();
                signature.executeBatch();
                band.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    /** The plan SQLite gives {@code sql} with every value bound to one no run, band value or occurrence has. */
    private List<String> planWithNoValueOfTheLedger(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) NO_SUCH_VALUE)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }

    /**
     * A template over the statement log's connections that counts, for each band value read, the rows its reads
     * hand back: a read of one band value names the run, the band and the value first, in that order, as the
     * keyset pager sends it through this one method.
     */
    private final class CountingBandValueRows extends JdbcTemplate {

        CountingBandValueRows(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = super.query(sql, rowMapper, args);
            if (sql.contains("FROM signature_band") && sql.contains("band_hash = ?") && sql.contains("rowid > ?")) {
                rowsHandedBack.merge(
                        new Bucket(((Number) args[1]).intValue(), ((Number) args[2]).longValue()), rows.size(), Integer::sum);
            }
            return rows;
        }
    }
}
