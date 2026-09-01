package io.algernon.vespera.ledger;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One schema version per module, checked and refused independently (ADR-059).
 *
 * <p>A single global counter was rejected: it couples unrelated modules' schema evolution, so
 * adding a column to {@code embedding}'s tables would refuse to start a database whose
 * {@code corpus} tables are perfectly current. Each module calls {@link #require} with its own name
 * and its own compiled-in expected version instead.
 *
 * <p>Lives in {@code ledger} because every module already depends on {@code ledger} and the
 * {@code schema_version} table is the ledger's, not because version checking is a ledger concern.
 */
@Component
@DependsOnDatabaseInitialization
public class SchemaVersionGuard {

    private final JdbcTemplate jdbcTemplate;

    public SchemaVersionGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records {@code module} at {@code expectedVersion} on its first run, and refuses any later run
     * whose database says something else.
     *
     * @throws SchemaVersionMismatch if the database records a different version for this module
     */
    public void require(String module, int expectedVersion) {
        Integer recorded = jdbcTemplate.query(
                "SELECT version FROM schema_version WHERE module = ?",
                resultSet -> resultSet.next() ? resultSet.getInt("version") : null,
                module);

        if (recorded == null) {
            // First run for this module. Exclusive access (ADR-050) is what makes the gap between
            // the read and this insert uninteresting: there is no second process to race.
            jdbcTemplate.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", module, expectedVersion);
            return;
        }
        if (recorded != expectedVersion) {
            throw new SchemaVersionMismatch(module, recorded, expectedVersion);
        }
    }
}
