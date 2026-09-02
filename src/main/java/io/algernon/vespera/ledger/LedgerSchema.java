package io.algernon.vespera.ledger;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code ledger}'s own schema version (ADR-059).
 *
 * <p>The check runs in the constructor rather than in a lifecycle callback, so that a mismatched
 * database fails while the context is still being built — before any component holding a
 * {@link Ledger} has had the chance to read or write a row.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes ledger's tables in {@code schema.sql}.
 */
@Component
@DependsOnDatabaseInitialization
class LedgerSchema {

    /** The version of ledger's tables this code expects. */
    static final int VERSION = 2;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "ledger";

    LedgerSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
