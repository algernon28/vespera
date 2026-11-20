/**
 * The retrieval plugin (ADR-434 §8): draws the retrieval records a drawing hands it, a cluster's synthesis doc, a
 * topic's overview and a member's passage, each with its vector, into one JSONL file, {@code retrieval/records.jsonl},
 * under the deliverable, for a store of the operator's to import. It draws from no tree and opens no {@code Drawing}.
 * Its delivery sends nothing: it opens no connection and writes nothing.
 *
 * <p>It declares {@code ledger} for the move that puts the file in place, {@code deliverable}, whose seam it
 * implements, and {@code synthesis}, whose records the seam hands it, as each plugin does (ADR-333 §1). No stage's
 * version names this module, so a change here moves no run id (ADR-434 §13).
 */
@org.springframework.modulith.ApplicationModule(id = "retrieval", allowedDependencies = {"ledger", "deliverable", "synthesis"})
package io.algernon.vespera.publish.retrieval;
