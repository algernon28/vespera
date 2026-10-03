package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The indexes {@code schema.sql} gives {@code shingle}, and which of them each reader of one run's
 * shingles goes through (ADR-182, #381).
 *
 * <p>Runs the schema into the test profile's in-memory SQLite, the way every start does. Nothing here
 * runs {@code ANALYZE}, so the planner decides from the schema alone, the same way here as on a corpus,
 * and {@code EXPLAIN QUERY PLAN} reads the same here as on a 25-million-row table.
 *
 * <p>The lookup by hash is built here with the statement ADR-182 gives stage 4b, because no start builds
 * it any more. Each test runs in a transaction {@code @JdbcTest} rolls back, and SQLite's DDL is
 * transactional, so an index one test builds is gone before the next.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Resolution")
@Issue("381")
@Link(name = "ADR-182", url = Adr.STAGE_2_WRITES_SHINGLES_WITHOUT_THE_LOOKUP_BY_HASH, type = "adr")
class ShingleIndexesTest {

    /** The lookup stage 4b's containment search reads, by the name {@code sqlite_master} carries. */
    private static final String LOOKUP_BY_HASH = "shingle_by_hash";

    /** The index on {@code shingle.run_id} alone, named the way ADR-173 names one. */
    private static final String BY_RUN_ID = "shingle_by_run_id";

    /** The index one document's own shingles are read through (#277). */
    private static final String BY_OCCURRENCE = "shingle_by_occurrence";

    /** What stage 4b runs before its first read of the lookup, and nothing else runs. */
    private static final String BUILD_THE_LOOKUP =
            "CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** Stage 3's document-frequency read: every shingle row of one stage-2 run. */
    private static final String STAGE_3_READ =
            "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?";

    /** Stage 4b's containment search, with three rare hashes. */
    private static final String CONTAINMENT_SEARCH = "SELECT occurrence_id FROM shingle"
            + " WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN (?, ?, ?)"
            + " GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Stage 2 writes shingles without keeping the lookup by hash up to date")
    @DisplayName("A start gives the shingle table an index on its run and one on its document, and no lookup by hash")
    void aStartCreatesNoLookupByHash() {
        claim(
                "the shingle table carries an index on the run alone, and one on each document's own rows",
                () -> assertThat(indexesOnShingle()).contains(BY_RUN_ID, BY_OCCURRENCE));
        claim(
                "the index on the run has the run as its only column, so every write of one run's shingles"
                        + " lands at the end of it rather than all over it",
                () -> assertThat(columnsOf(BY_RUN_ID)).containsExactly("run_id"));
        claim(
                "and no lookup by hash is there: starting never builds it, because every start would then"
                        + " build it again over the whole table before stage 2 could drop it",
                () -> assertThat(indexesOnShingle()).doesNotContain(LOOKUP_BY_HASH));
    }

    @Test
    @Story("Stage 2 writes shingles without keeping the lookup by hash up to date")
    @DisplayName("The read of every shingle of one run goes through the index on the run, whether or not the lookup by hash exists")
    void readingOneRunsShinglesGoesThroughTheRunIndex() {
        claim(
                "without the lookup by hash, reading one run's shingles searches the index on the run, rather"
                        + " than reading the whole table",
                () -> assertThat(planOf(STAGE_3_READ, "a-run")).contains("SEARCH").contains(BY_RUN_ID));

        jdbcTemplate.execute(BUILD_THE_LOOKUP);

        claim(
                "and with it, the same read still goes through the index on the run, in the order the rows"
                        + " were written, not through the lookup in hash order",
                () -> assertThat(planOf(STAGE_3_READ, "a-run")).contains(BY_RUN_ID).doesNotContain(LOOKUP_BY_HASH));
    }

    @Test
    @Story("The lookup by hash is built by the step that reads it")
    @DisplayName("Once built, the lookup by hash is what the search for containing documents reads")
    void theContainmentSearchReadsTheLookupOnceBuilt() {
        jdbcTemplate.execute(BUILD_THE_LOOKUP);

        claim(
                "the search for documents holding a document's rarest shingles is answered from the lookup,"
                        + " by run, granularity and hash",
                () -> assertThat(planOf(CONTAINMENT_SEARCH, "a-run", ShingleParameters.DEFAULT.identity(), 1L, 2L, 3L, 24))
                        .contains(LOOKUP_BY_HASH)
                        .contains("shingle_hash=?"));
    }

    private List<String> indexesOnShingle() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle'", String.class);
    }

    private List<String> columnsOf(String index) {
        return jdbcTemplate.query(
                "SELECT name FROM pragma_index_info(?) ORDER BY seqno",
                (resultSet, rowNumber) -> resultSet.getString("name"),
                index);
    }

    /** SQLite's plan for {@code query}, its placeholders bound to {@code arguments}. */
    private String planOf(String query, Object... arguments) {
        return String.join(
                " ",
                jdbcTemplate.query(
                        "EXPLAIN QUERY PLAN " + query,
                        (resultSet, rowNumber) -> resultSet.getString("detail"),
                        arguments));
    }
}
