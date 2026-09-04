package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s own schema version, checked and refused independently of every other
 * module's (ADR-059) — so a change to {@code extraction_cache} refuses a stale database without
 * saying anything about the ledger's or corpus's.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes {@code extraction_cache} (or any later
 * table this module adds — chunk cache, {@code extraction_metric}) in {@code schema.sql}.
 *
 * <p>{@code VERSION} 2 is {@code chunk_cache} (ADR-029, ADR-044), added alongside this bump.
 */
@Component
@DependsOnDatabaseInitialization
class ExtractionSchema {

    /** The version of extraction's tables this code expects. */
    static final int VERSION = 2;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "extraction";

    ExtractionSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
