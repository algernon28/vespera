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
 * table this module adds — chunk cache, {@code extraction_metric}, {@code confidence_distribution})
 * in {@code schema.sql}.
 *
 * <p>{@code VERSION} 2 is {@code chunk_cache} (ADR-029, ADR-044). {@code VERSION} 3 is {@code
 * confidence_distribution} (ADR-075). {@code VERSION} 4 renames {@code chunk_cache}'s
 * {@code tokenizer_identity} to {@code chunking_rule_identity} and its {@code token_count} to
 * {@code word_count} (ADR-091): a database written under the old names holds boundaries cut by a
 * stand-in tokenizer that no longer exists, so it is refused rather than read. {@code VERSION} 5 is
 * {@code extraction_fault} (ADR-139): a database written before it holds no record of a refused
 * conversion, which is a fact absent rather than a fact wrongly shaped, so nothing here forces a
 * re-extraction on its own -- the version still moves, on the same rule every prior bump followed.
 * {@code VERSION} 6 is {@code extraction_cache_key} (ADR-206): a database written before it holds no key
 * for any occurrence, and none can be filled in without reading every file again, so it is refused and
 * the upgrade is a fresh working directory.
 */
@Component
@DependsOnDatabaseInitialization
class ExtractionSchema {

    /** The version of extraction's tables this code expects. */
    static final int VERSION = 6;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "extraction";

    ExtractionSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
