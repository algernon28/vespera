package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
 * Every column that references {@code file_occurrence}, {@code walk} or {@code run} is the first column
 * of an index on its own table (ADR-173), so that a table added later cannot forget it.
 *
 * <p>With {@code foreign_keys=on}, deleting a parent row makes SQLite look for child rows pointing at
 * it, and where the child column leads no index that lookup reads the whole child table. Deleting the
 * 43,101 occurrences of a duplicate walk this way, on a copy of the archive's ledger, is what ADR-173
 * measured.
 *
 * <p>Read from the schema exactly as a start applies it: {@code schema.sql} run into the test profile's
 * in-memory SQLite, then asked through {@code PRAGMA foreign_key_list}, {@code PRAGMA index_list} and
 * {@code PRAGMA index_info}. A primary key counts through the index SQLite builds for it, and an
 * {@code INTEGER PRIMARY KEY} counts as the rowid it is.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Ledger")
@Link(name = "ADR-173", url = Adr.EVERY_COLUMN_REFERENCING_AN_OCCURRENCE_A_WALK_OR_A_RUN_IS_INDEXED, type = "adr")
@Issue("368")
class ForeignKeysToFileOccurrenceAreIndexedTest {

    /** The tables whose rows a delete has to look for children of, and so whose references are indexed. */
    private static final Set<String> PARENTS = Set.of("file_occurrence", "walk", "run");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the ledger records")
    @DisplayName("Every column that references a file occurrence, a walk or a run leads an index on its table")
    void everyColumnReferencingAnOccurrenceAWalkOrARunLeadsAnIndex() {
        List<Reference> references = SchemaReferences.to(jdbcTemplate, PARENTS);
        List<Reference> unindexed = references.stream()
                .filter(reference -> !SchemaReferences.leadsAnIndex(jdbcTemplate, reference))
                .toList();

        claim(
                "the schema has columns referencing file_occurrence, so the claims below are about something",
                () -> assertThat(references).anyMatch(reference -> reference.parent().equals("file_occurrence")));
        claim(
                "no column referencing file_occurrence, walk or run is left without an index it leads; the"
                        + " failure names each such table and column",
                () -> assertThat(unindexed).isEmpty());
        for (Reference reference : references) {
            claim(
                    reference.table() + "." + reference.column() + ", which references " + reference.parent()
                            + ", is the first column of an index on " + reference.table(),
                    () -> assertThat(SchemaReferences.leadsAnIndex(jdbcTemplate, reference)).isTrue());
        }
    }

    /** One column of one table, and the table its foreign key names. */
    record Reference(String table, String column, String parent) {

        @Override
        public String toString() {
            return table + "." + column + " -> " + parent;
        }
    }

    /** The schema's foreign keys and indexes, as SQLite itself reports them. */
    static final class SchemaReferences {

        private SchemaReferences() {
        }

        /** Every column of every table whose foreign key names one of {@code parents}. */
        static List<Reference> to(JdbcTemplate jdbcTemplate, Set<String> parents) {
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                    String.class);
            return tables.stream()
                    .flatMap(table -> jdbcTemplate
                            .query(
                                    "SELECT \"table\" AS parent, \"from\" AS col FROM pragma_foreign_key_list(?)",
                                    (resultSet, rowNumber) -> new Reference(
                                            table, resultSet.getString("col"), resultSet.getString("parent")),
                                    table)
                            .stream())
                    .filter(reference -> parents.contains(reference.parent()))
                    .toList();
        }

        /**
         * Whether {@code reference}'s column is the first column of some index on its table, or the
         * table's {@code INTEGER PRIMARY KEY}, which is the rowid and needs no index of its own.
         */
        static boolean leadsAnIndex(JdbcTemplate jdbcTemplate, Reference reference) {
            List<String> indexes = jdbcTemplate.queryForList(
                    "SELECT name FROM pragma_index_list(?)", String.class, reference.table());
            for (String index : indexes) {
                List<String> leading = jdbcTemplate.queryForList(
                        "SELECT name FROM pragma_index_info(?) WHERE seqno = 0", String.class, index);
                if (leading.contains(reference.column())) {
                    return true;
                }
            }
            Integer rowidAlias = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pragma_table_info(?) WHERE name = ? AND pk = 1 AND upper(type) = 'INTEGER'"
                            + " AND (SELECT count(*) FROM pragma_table_info(?) WHERE pk > 0) = 1",
                    Integer.class,
                    reference.table(),
                    reference.column(),
                    reference.table());
            return rowidAlias != null && rowidAlias > 0;
        }
    }
}
