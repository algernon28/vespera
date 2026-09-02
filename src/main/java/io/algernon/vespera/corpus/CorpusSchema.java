package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code corpus}'s own schema version, checked and refused independently of every other module's
 * (ADR-059) — so a change to the anomaly table refuses a stale database without saying anything
 * about the ledger's.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes {@code walk_anomaly},
 * {@code content_hash}, or {@code superseded_by} in {@code schema.sql} — corpus's own tables.
 */
@Component
@DependsOnDatabaseInitialization
class CorpusSchema {

    /** The version of corpus's tables this code expects. */
    static final int VERSION = 2;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "corpus";

    CorpusSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
