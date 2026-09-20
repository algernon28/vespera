package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/**
 * {@code synthesis}'s own schema version, checked and refused independently of every other module's
 * (ADR-059) — so a change to the terminal stages' tables refuses a stale database without saying
 * anything about the ledger's, corpus's, extraction's, similarity's or embedding's.
 *
 * <p>Bump {@link #VERSION} in the same commit that changes synthesis's tables in {@code schema.sql}.
 * Version 1 is {@code cluster} (ADR-105, ADR-110, ADR-112, #175) — the level stage 5 left unbuilt: a
 * cluster as something addressable, with a name and a place in an order. Version 2 adds
 * {@code synthesis_doc} (ADR-108, ADR-110, #180), the second table ADR-110 gives this module: what
 * one call produced for one cluster, kept because the file a reader opens is a rendering of the row
 * rather than a copy of it. Version 3 adds {@code cluster_fault} (ADR-108, ADR-109, ADR-110,
 * ADR-111, #183), the third table ADR-111 fills a deferral for: why a call that came back was
 * rejected, kept beside the cluster it was about on the same precedent as {@code walk_anomaly} and
 * {@code unusable_seed} — a fact about content that is not a verdict, because nothing about the
 * cluster's documents is wrong. Version 4 adds {@code call_exemplar} and drops
 * {@code synthesis_doc.documents_sent} (ADR-108, ADR-109, ADR-133, #236): which documents one call
 * carried under which citation ordinal, recorded because it cannot be re-derived — the count that
 * stood in its place let the deliverable number its membership a second way and disagree with the
 * numbering the model was given.
 *
 * <p>Note what does <em>not</em> bump alongside it. {@code document_cluster} is untouched and stays
 * {@code embedding}'s (ADR-110): membership is stage 5's and is not restated here, so
 * {@code EmbeddingSchema} does not move. Nothing in {@code extraction} moves either — the Docling
 * title the cluster label is derived from is read out of the response already cached there.
 */
@Component
@DependsOnDatabaseInitialization
class SynthesisSchema {

    /** The version of synthesis's tables this code expects. */
    static final int VERSION = 4;

    /** The module name the version is recorded under, matching the package name. */
    static final String MODULE = "synthesis";

    SynthesisSchema(SchemaVersionGuard guard) {
        guard.require(MODULE, VERSION);
    }
}
