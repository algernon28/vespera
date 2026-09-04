package io.algernon.vespera.ledger;

/**
 * The database holds a different version of a module's tables than that module's code expects
 * (ADR-059). Thrown before any read or write touches those tables: there is no partial-degradation
 * mode, because a stage reading columns that mean something else is worse than a stage that refuses
 * to start.
 *
 * <p>The message names the module and both versions, because the operator's next action depends on
 * which direction the mismatch runs, and the manual upgrade path for this slice is to delete and
 * recreate that module's tables.
 */
class SchemaVersionMismatch extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    SchemaVersionMismatch(String module, int recordedVersion, int expectedVersion) {
        super("module %s expects schema version %d, but the database records version %d; delete and recreate %s's tables, then re-run census"
                .formatted(module, expectedVersion, recordedVersion, module));
    }
}
