package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code similarity}'s own schema version, checked and refused independently of every other
 * module's (ADR-059, ADR-073) — a mismatch in the shingle table refuses a stale database without
 * saying anything about {@code extraction}'s tables, or the reverse.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes similarity's tables in {@code schema.sql}.
 * Version 2 adds {@code shingle_document_frequency} and {@code shingle_corpus_size} (ADR-074).
 */
@Component
@DependsOnDatabaseInitialization
class SimilaritySchema {

    /** The version of similarity's tables this code expects. */
    static final int VERSION = 2;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "similarity";

    SimilaritySchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
