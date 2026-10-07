package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.ForeignKeysToFileOccurrenceAreIndexedTest.Reference;
import io.algernon.vespera.ledger.ForeignKeysToFileOccurrenceAreIndexedTest.SchemaReferences;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link Walks#discardWalk} under {@code foreign_keys=on}, over a walk of a few thousand occurrences
 * while an earlier walk's derived rows reference that earlier walk's occurrences (ADR-115, ADR-173).
 *
 * <p>The delete finishing is not what this pins: in memory, and at this size, it finishes with or
 * without the indexes. What it pins is the lookup SQLite makes for each deleted parent row. {@code
 * EXPLAIN QUERY PLAN} of the {@code DELETE} does not show the foreign key check, so the check is asked
 * about in the form SQLite documents it as running: {@code SELECT ... FROM <child> WHERE <column> = ?},
 * once per parent row. {@code SEARCH} is that lookup going through an index; {@code SCAN} is it
 * reading the whole child table, which on the archive's ledger is what ADR-173 measured. The walk's
 * anomalies are deleted beside it by the module that owns them, in a statement of its own
 * ({@code AnomalyLog#discardForWalk}), so that statement's plan is asked for directly.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Ledger")
@Link(name = "ADR-173", url = Adr.EVERY_COLUMN_REFERENCING_AN_OCCURRENCE_A_WALK_OR_A_RUN_IS_INDEXED, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
@Issue("368")
class DiscardingAWalkSearchesEveryReferenceTest {

    /** A few thousand stands in for the real archive's discarded walk, which held 43,101 occurrences. */
    private static final int OCCURRENCES_PER_WALK = 3_000;

    /** The parents {@code discardWalk} deletes rows of: its occurrences, and the walk itself. */
    private static final Set<String> DELETED_PARENTS = Set.of("file_occurrence", "walk");

    private static final Instant WHEN = Instant.parse("2026-09-29T10:33:05Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the ledger records")
    @DisplayName("Discarding a duplicate walk under foreign keys finds each reference to what it deletes through an index")
    void discardingADuplicateWalkSearchesEveryReferenceThroughAnIndex() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId earlier = ledger.walks().startWalk(Path.of("C:/corpus"));
        recordOccurrences(ledger, earlier);
        RunId run = ledger.runs().startRun("resolution", "a version", "{}", earlier, List.of());
        OccurrenceId representative = ledger.occurrences().occurrenceId(earlier, path(0)).orElseThrow();
        for (int i = 1; i < OCCURRENCES_PER_WALK; i++) {
            OccurrenceId superseded = ledger.occurrences().occurrenceId(earlier, path(i)).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO superseded_by (occurrence_id, run_id, representative_occurrence_id) VALUES (?, ?, ?)",
                    superseded.value(),
                    run.value(),
                    representative.value());
        }
        WalkId duplicate = ledger.walks().startWalk(Path.of("C:/corpus"));
        recordOccurrences(ledger, duplicate);

        claim(
                "foreign keys are enforced on the connection the ledger writes through, as they are on a"
                        + " real ledger, so the delete below pays for the checks",
                () -> assertThat(jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class))
                        .isEqualTo(1));

        ledger.walks().discardWalk(duplicate);

        claim(
                "the duplicate walk's occurrences are gone, and its walk row with them",
                () -> {
                    assertThat(ledger.occurrences().occurrenceCount(duplicate)).isZero();
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM walk WHERE id = ?", Integer.class, duplicate.value()))
                            .isZero();
                });
        claim(
                "the earlier walk, its occurrences and the rows referencing them are untouched",
                () -> {
                    assertThat(ledger.occurrences().occurrenceCount(earlier)).isEqualTo(OCCURRENCES_PER_WALK);
                    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM superseded_by", Integer.class))
                            .isEqualTo(OCCURRENCES_PER_WALK - 1);
                });

        List<Reference> checked = SchemaReferences.to(jdbcTemplate, DELETED_PARENTS);
        claim(
                "superseded_by.representative_occurrence_id is among the references checked, so the fixture"
                        + " above is one of them",
                () -> assertThat(checked)
                        .contains(new Reference("superseded_by", "representative_occurrence_id", "file_occurrence")));
        for (Reference reference : checked) {
            claim(
                    "deleting a " + reference.parent() + " row looks for " + reference.table() + "."
                            + reference.column() + " through an index (SEARCH), not by reading the whole table"
                            + " (SCAN)",
                    () -> assertThat(plan("SELECT 1 FROM \"" + reference.table() + "\" WHERE \""
                                    + reference.column() + "\" = ?"))
                            .startsWith("SEARCH"));
        }
        claim(
                "the delete of the walk's anomalies, which goes with the discard, finds them through an index,"
                        + " not by reading the whole table",
                () -> assertThat(plan("DELETE FROM walk_anomaly WHERE walk_id = ?")).startsWith("SEARCH"));
    }

    private static void recordOccurrences(Ledger ledger, WalkId walkId) {
        for (int i = 0; i < OCCURRENCES_PER_WALK; i++) {
            ledger.occurrences().fileOccurrence(walkId, path(i), i, WHEN, WHEN);
        }
    }

    private static OccurrencePath path(int i) {
        return new OccurrencePath("dir/file-" + i + ".txt");
    }

    /** SQLite's plan for {@code sql} with one parameter bound, its steps joined in order. */
    private String plan(String sql) {
        return String.join(
                " | ", jdbcTemplate.query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), 0L));
    }
}
