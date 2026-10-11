package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The three reads of one seed partition's memberships, {@link DocumentClusters#sizeOf}, {@link
 * DocumentClusters#membersOf} and {@link DocumentClusters#sizesFor}, go to the rows of that seed under the one
 * scoring run they are handed, and not to the seed's rows under every scoring run the working directory keeps
 * (ADR-236). Each is planned by the bundled SQLite against the shipped {@code schema.sql}; the texts are the
 * ones the class ships, copied here, as {@code RecordedAmongTest} copies its own.
 */
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-236", url = Adr.A_PARTITIONS_READS_SEEK_ONLY_ITS_RUNS_ROWS, type = "adr")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
class APartitionsReadsSeekOnlyItsRunsRowsTest {

    private static final String SIZE_OF =
            "SELECT COUNT(*) FROM document_cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?";

    private static final String MEMBERS_OF = "SELECT occurrence_id, cluster_ordinal FROM document_cluster"
            + " WHERE run_id = ? AND winning_seed_occurrence_id = ?";

    private static final String SIZES_FOR = "SELECT COUNT(*) AS members FROM document_cluster WHERE run_id = ?"
            + " AND winning_seed_occurrence_id = ? GROUP BY cluster_ordinal ORDER BY cluster_ordinal";

    /** What a plan says where an index is searched on the seed and the run together. */
    private static final String ON_THE_SEED_AND_THE_RUN = "(winning_seed_occurrence_id=? AND run_id=?)";

    @Test
    @Story("A partition's groups are read from its own piece of work")
    @DisplayName("Counting, listing and sizing the groups of one exemplar read that exemplar's rows of one piece of work, and sort nothing")
    void eachReadSeeksTheSeedAndTheRunTogether() throws Exception {
        List<String> sizeOf = planOf(SIZE_OF);
        List<String> membersOf = planOf(MEMBERS_OF);
        List<String> sizesFor = planOf(SIZES_FOR);

        claim(
                "counting an exemplar's documents searches an index on the exemplar and the piece of work"
                        + " together, so the rows of every earlier piece of work kept for that exemplar are not"
                        + " gone through",
                () -> assertThat(sizeOf).anyMatch(step -> step.contains(ON_THE_SEED_AND_THE_RUN)));
        claim(
                "listing its documents does the same",
                () -> assertThat(membersOf).anyMatch(step -> step.contains(ON_THE_SEED_AND_THE_RUN)));
        claim(
                "and sizing its groups does the same, reading them in the order of the groups, so the count for"
                        + " each group is made without sorting anything",
                () -> assertThat(sizesFor)
                        .anyMatch(step -> step.contains(ON_THE_SEED_AND_THE_RUN))
                        .noneMatch(step -> step.contains("TEMP B-TREE")));
    }

    /** SQLite's plan for {@code sql} against the shipped schema, every value bound to a whole number. */
    private static List<String> planOf(String sql) throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ScriptUtils.executeSqlScript(database, new ClassPathResource("schema.sql"));
            try (PreparedStatement plan = database.prepareStatement("EXPLAIN QUERY PLAN " + sql)) {
                plan.setString(1, "a-scoring-run");
                plan.setLong(2, 1);
                return steps(plan);
            }
        }
    }

    private static List<String> steps(PreparedStatement plan) throws SQLException {
        List<String> steps = new ArrayList<>();
        try (ResultSet rows = plan.executeQuery()) {
            while (rows.next()) {
                steps.add(rows.getString("detail"));
            }
        }
        return steps;
    }
}
