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
 * Version 3 adds stage 4's {@code minhash_signature}, {@code signature_band} and {@code
 * redundant_with}, plus the by-hash index on {@code shingle} its containment retrieval reads
 * (ADR-079, ADR-081, ADR-082).
 */
@Component
@DependsOnDatabaseInitialization
class SimilaritySchema {

    /** The version of similarity's tables this code expects. */
    static final int VERSION = 3;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "similarity";

    SimilaritySchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
