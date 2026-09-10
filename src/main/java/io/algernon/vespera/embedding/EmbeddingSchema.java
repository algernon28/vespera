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
 * relevance_score} (ADR-020, #108). Version 5 is {@code relevance_label} (ADR-088, #111). Version 6
 * is {@code document_cluster} (ADR-087, ADR-045, #109).
 *
 * <p>Version 7 is the first that <em>re-keys</em> a table rather than adding one: {@code
 * relevance_label} moved from the occurrence and the seed set to the path and the seed set (ADR-097,
 * #130). A shape change is a bump whether or not a table arrives with it, because what refuses a
 * stale database is the version rather than the table list — a database written under 6 holds label
 * rows this code cannot read, and the guard is what stops it reading them.
 *
 * <p>It shipped with no migration, and that was checked rather than skipped: as of ADR-097 no corpus
 * had been labelled outside tests, so there was no answer anywhere to carry across. The manual path
 * is the one {@code schema.sql} already documents — delete the mismatched module's tables and re-run.
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
    static final int VERSION = 7;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "embedding";

    EmbeddingSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
