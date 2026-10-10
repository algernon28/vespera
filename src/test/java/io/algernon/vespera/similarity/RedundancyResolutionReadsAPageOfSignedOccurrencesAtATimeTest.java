package io.algernon.vespera.similarity;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage 4b goes through its signed documents a page at a time and asks by key, and holds no collection of the
 * run's signed documents, band rows, candidate pairs or shingle frequencies (ADR-220 section 4). Until ADR-220
 * it read every signed document into a set, every band row of the run into a map of buckets, every pair into a
 * set, and every frequency row of stage 3's run into a map.
 *
 * <p>What it holds cannot be seen from outside; what it asks the database can. Every statement it issues is
 * kept, through {@link StatementLog}, and none may read a whole run of a table that has a row for each document,
 * except the one counted read of the signed documents, which counts them and keeps none.
 *
 * <p>2,550 documents, each with its own shingles: 2,400 that resemble nothing; 50 pairs of near-duplicates,
 * 99 of their 100 shingles shared, a Jaccard far above the cut so that retrieval cannot miss them (ADR-081);
 * and 25 documents wholly contained in a document of twice their shingles, too unlike it to be a near-duplicate.
 * So stage 4b removes one of each pair and each contained document, and nothing else.
 *
 * <p>It names nothing ADR-220 adds, and so compiles before it; it fails until stage 4b reads as ADR-220 says.
 */
@Epic("Redundancy")
@Feature("Resolution")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-079", url = Adr.REDUNDANT_WITH_COVERS_NEAR_DUPLICATION_AND_CONTAINMENT, type = "adr")
class RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest {

    /** Documents that resemble nothing, each of its own 20 shingles. */
    private static final int ALONE = 2_400;

    private static final int SHINGLES_ALONE = 20;

    /** Pairs of near-duplicates, each document 100 shingles, 99 of them shared. */
    private static final int NEAR_DUPLICATE_PAIRS = 50;

    private static final int SHINGLES_NEAR = 100;

    /** Documents wholly contained in another of twice their shingles. */
    private static final int CONTAINED = 25;

    private static final int SHINGLES_CONTAINED = 30;

    private static final int SHINGLES_CONTAINER = 60;

    /** Every document is signed: none is all boilerplate, no boilerplate being given. */
    private static final int SIGNED = ALONE + 2 * NEAR_DUPLICATE_PAIRS + 2 * CONTAINED;

    /** The most documents a page holds, and the most one statement may name. */
    private static final int A_PAGE = 1_000;

    /** How many pages of 1,000 the 2,550 signed documents take. */
    private static final int PAGES_OF_SIGNED = 3;

    /** Not a real floor: nothing is boilerplate here. */
    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    /** Each document's alphanumeric characters, the survivor rule's measure; the same for all, so ties fall to age, then path. */
    private static final long TEXT = 10_000L;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId stage2;
    private RunId stage3;
    private RunId stage4;

    @BeforeEach
    void aSignedRunOfNearDuplicatesAndContainedOccurrences() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-signed-pages"));
        stage2 = ledger.runs().startRun("extraction", "s2-214", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "s3-214", "{}", walk, List.of(stage2));
        stage4 = ledger.runs().startRun("content-redundancy", "s4-214", "{}", walk, List.of(stage3));

        List<List<Long>> shingleSets = new ArrayList<>();
        for (int k = 0; k < ALONE; k++) {
            shingleSets.add(range(1_000_000L + (long) k * SHINGLES_ALONE, SHINGLES_ALONE));
        }
        for (int p = 0; p < NEAR_DUPLICATE_PAIRS; p++) {
            long base = 10_000_000L + p * 1_000L;
            shingleSets.add(range(base, SHINGLES_NEAR));
            shingleSets.add(range(base + 1, SHINGLES_NEAR));
        }
        for (int c = 0; c < CONTAINED; c++) {
            long base = 20_000_000L + c * 1_000L;
            shingleSets.add(range(base, SHINGLES_CONTAINER));
            shingleSets.add(range(base, SHINGLES_CONTAINED));
        }

        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2025-01-01T00:00:00Z')")) {
                for (int i = 0; i < shingleSets.size(); i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, String.format("d%05d.pdf", i));
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
            try (PreparedStatement shingle = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)");
                    PreparedStatement metric = connection.prepareStatement(
                            "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time,"
                                    + " character_count, alphanumeric_char_count, word_count,"
                                    + " word_character_length_total, vowelless_word_count, single_character_word_count)"
                                    + " VALUES (?, ?, 'success', 0.5, ?, ?, 1, 1, 0, 0)")) {
                for (int i = 0; i < ids.size(); i++) {
                    for (long hash : shingleSets.get(i)) {
                        shingle.setLong(1, ids.get(i));
                        shingle.setString(2, stage2.value());
                        shingle.setString(3, ShingleParameters.DEFAULT.identity());
                        shingle.setLong(4, hash);
                        shingle.addBatch();
                    }
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, stage2.value());
                    metric.setLong(3, TEXT);
                    metric.setLong(4, TEXT);
                    metric.addBatch();
                }
                shingle.executeBatch();
                metric.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }

        // Stage 3's frequencies and stage 4a's signatures, as the job writes them, in one transaction so that
        // thousands of writes are one commit; and stage 4b's index, built whole as 4b builds it.
        new TransactionTemplate(new JdbcTransactionManager(jdbcTemplate.getDataSource())).executeWithoutResult(status -> {
            new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2);
            RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
            for (Long id : ids) {
                signatures.write(new OccurrenceId(id), stage4, stage2, Set.of(), NO_BOILERPLATE_FLOOR);
            }
        });
        // The statement ADR-221 section 1 builds it with, for the run stage 4b reads.
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(stage2.value()));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Redundancy resolution holds no list of the corpus")
    @DisplayName("Redundancy resolution removes the same documents while reading a page of signed documents at a time and asking about each by key")
    void removesTheSameOccurrencesAskingAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        new RedundancyResolution(log.jdbcTemplate(), new Ledger(log.jdbcTemplate()))
                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(log.jdbcTemplate()));

        List<String> said = log.said();
        claim(
                "one of each of the " + NEAR_DUPLICATE_PAIRS + " near-identical pairs is removed as a near-duplicate,"
                        + " and each of the " + CONTAINED + " contained documents as contained, and nothing else",
                () -> assertThat(List.of(removedAs("near-duplicate"), removedAs("contained-in"), removedAtAll()))
                        .containsExactly(NEAR_DUPLICATE_PAIRS, CONTAINED, NEAR_DUPLICATE_PAIRS + CONTAINED));
        claim(
                "no statement reads the run's signature bands without naming the documents whose bands it wants, at"
                        + " most " + A_PAGE + " of them, or naming one band value and taking at most " + A_PAGE
                        + " rows of it: the documents that share a band value are found a page of signed"
                        + " documents at a time",
                () -> assertThat(said.stream().filter(sql -> sql.contains("FROM signature_band")).toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(occurrencesNamedBy(sql) >= 1 && occurrencesNamedBy(sql) <= A_PAGE
                                        || sql.contains("band_hash = ?") && sql.endsWith(" LIMIT " + A_PAGE))
                                .as("a read of the signature bands that names a page of documents or one band value: %s", sql)
                                .isTrue()));
        claim(
                "no statement reads stage 3's shingle frequencies without naming a document: a document's rarest"
                        + " shingles are found with its own rows, and no frequency of the whole corpus is read",
                () -> assertThat(said.stream()
                                .filter(sql -> sql.contains("shingle_document_frequency"))
                                .filter(sql -> sql.stripLeading().startsWith("SELECT"))
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql).containsAnyOf("occurrence_id = ?", "occurrence_id IN (")));
        List<String> pagesOfSigned = said.stream()
                .filter(sql -> sql.contains("FROM minhash_signature") && sql.contains(" LIMIT "))
                .toList();
        claim(
                "the " + SIGNED + " signed documents are read a page of at most " + A_PAGE + " at a time, at least "
                        + PAGES_OF_SIGNED + " pages, each going on from the last row read by the index on the run",
                () -> assertThat(pagesOfSigned)
                        .hasSizeGreaterThanOrEqualTo(PAGES_OF_SIGNED)
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .anyMatch(detail -> detail.contains("minhash_signature_by_run_id (run_id=? AND rowid>?)"))
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
        claim(
                "whether a document is signed, or was removed as a near-duplicate, is asked of at most " + A_PAGE
                        + " documents at a time, each named",
                () -> assertThat(said.stream()
                                .filter(sql -> sql.contains("FROM redundant_with")
                                        || (sql.contains("FROM minhash_signature") && sql.contains("occurrence_id IN (")))
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE)));
    }

    private int removedAs(String relation) {
        Integer count = pool.jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM redundant_with WHERE run_id = ? AND relation = ?",
                Integer.class,
                stage4.value(),
                relation);
        return count == null ? 0 : count;
    }

    private int removedAtAll() {
        Integer count = pool.jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'REDUNDANT_WITH'", Integer.class, stage4.value());
        return count == null ? 0 : count;
    }

    private static List<Long> range(long from, int count) {
        List<Long> hashes = new ArrayList<>(count);
        for (long i = 0; i < count; i++) {
            hashes.add(from + i);
        }
        return hashes;
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }

    /** The placeholders in the statement's list of documents, {@code occurrence_id IN (…)}; none where it has none. */
    private static int occurrencesNamedBy(String sql) {
        Matcher list = OCCURRENCE_LIST.matcher(sql);
        if (!list.find()) {
            return 0;
        }
        return (int) list.group(1).chars().filter(character -> character == '?').count();
    }

    private static final Pattern OCCURRENCE_LIST = Pattern.compile("occurrence_id IN \\(([^)]*)\\)");
}
