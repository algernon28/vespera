package io.algernon.vespera.ledger;

import java.io.Serial;

/**
 * The database holds a different version of a module's tables than that module's code expects
 * (ADR-059). Thrown before any read or write touches those tables: there is no partial-degradation
 * mode, because a stage reading columns that mean something else is worse than a stage that refuses
 * to start.
 *
 * <p>The message names the module, both versions and the database file that recorded the other
 * version, because the operator's next action depends on which direction the mismatch runs, the manual
 * upgrade path for this slice is to delete and recreate that module's tables, and a run that did not
 * load {@code .env} opens another working directory's file and reads as a fault in the one the
 * operator meant (ADR-177 §2.4).
 */
class SchemaVersionMismatchException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param databaseFile the file the guard's connection has open, as SQLite reports it; empty for a
     *     database held only in memory
     */
    SchemaVersionMismatchException(String module, int recordedVersion, int expectedVersion, String databaseFile) {
        super("module %s expects schema version %d, but %s records version %d; delete and recreate %s's tables, then re-run census. If that is not the working directory you meant, name it with --db-dir=<path> or vespera.working-dir"
                .formatted(
                        module,
                        expectedVersion,
                        databaseFile.isEmpty() ? "an in-memory database" : "the database file " + databaseFile,
                        recordedVersion,
                        module));
    }
}
