package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s own schema version, checked and refused independently of every other module's
 * (ADR-059) — so a change to stage 5's tables refuses a stale database without saying anything about
 * the ledger's, corpus's, extraction's or similarity's.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes embedding's tables in {@code schema.sql}.
 * Version 1 is {@code unusable_seed} (ADR-083). Version 2 is {@code seed_corpus_comparison}
 * (ADR-086, #106). Version 3 is {@code vector} (ADR-084, ADR-085, #107). Version 4 is {@code
 * relevance_score} (ADR-020, #108).
 *
 * <p>Note what does <em>not</em> bump alongside it: seed extraction adds no {@code extraction} table
 * and re-chunking writes rows under a new tokenizer identity into the existing {@code chunk_cache},
 * so {@code ExtractionSchema} stays where it is. Bump that one only when a table shape actually
 * changes. The seed side's own {@code extraction_metric} rows (ADR-092) land in an existing
 * {@code extraction} table under a run id it already accommodates, so that module's schema does not
 * move either.
 */
@Component
@DependsOnDatabaseInitialization
class EmbeddingSchema {

    /** The version of embedding's tables this code expects. */
    static final int VERSION = 6;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "embedding";

    EmbeddingSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
