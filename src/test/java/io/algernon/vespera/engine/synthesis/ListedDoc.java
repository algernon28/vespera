package io.algernon.vespera.engine.synthesis;

/**
 * A synthesis doc with the cluster it was written over, as a test lists one: the row a test hands {@link
 * ListedArrangement}, and the row {@code WholeRun} reads back out of {@code cluster_synthesis_doc}.
 *
 * <p>{@code src/main} asks for one cluster's doc by its key since ADR-223 and has no row that pairs a doc
 * with its cluster, so a test that wants every doc of a run in one list keeps the pairing here (ADR-216).
 *
 * @param cluster the cluster it was written over, by its topic, its subtopic and its ordinal (ADR-409 section 3)
 * @param doc what was written over it
 */
public record ListedDoc(ClusterSlot cluster, SynthesisDoc doc) {}
