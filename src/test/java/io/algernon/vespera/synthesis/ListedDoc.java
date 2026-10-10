package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * A synthesis doc with the cluster it was written over, as a test lists one: the row a test hands {@link
 * ListedArrangement}, and the row {@code WholeRun} reads back out of {@code synthesis_doc}.
 *
 * <p>{@code src/main} asks for one cluster's doc by its key since ADR-223 and has no row that pairs a doc
 * with its cluster, so a test that wants every doc of a run in one list keeps the pairing here (ADR-216).
 *
 * @param winningSeed the seed whose partition the cluster sits in
 * @param clusterOrdinal the cluster's identity within that partition
 * @param doc what was written over it
 */
public record ListedDoc(OccurrenceId winningSeed, int clusterOrdinal, SynthesisDoc doc) {}
