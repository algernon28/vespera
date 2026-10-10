package io.algernon.vespera.synthesis;

/**
 * What the walk tells its caller about each cluster it leaves unwritten, and about every cluster it goes
 * through, as it goes (ADR-190, ADR-192). This module logs nothing, so the caller says what the operator
 * reads.
 *
 * <p>{@link #toGoThrough} is called once, before the walk, zero included; {@link #clusterGoneThrough} once at
 * the end of each cluster's path, after any report of the four kinds, and for a fifth turned-down answer before
 * the walk returns {@code Stopped}. Both do nothing by default.
 *
 * <p>It is also a {@link SynthesisStatementProgress} (ADR-193 section 7, ADR-204 section 4), and is told
 * about the walk's one read: {@link SynthesisStatement#STANDING_FAULTS} after the last cluster, started and
 * ended where the walk goes through every cluster, and not at all where it returns {@code Stopped} on five
 * answers turned down in a row, since it returns before that read.
 */
public interface GenerationProgress extends SynthesisStatementProgress {

    /** The exemplars came back empty. No call was made. */
    void noSendableDocument(RecordedCluster cluster);

    /** {@link ClusterSynthesis#nothingFitsIn} held for these documents. No call was made. */
    void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow);

    /** {@code docFor} threw a fault whose {@code noAnswerWasAskedFor()} is true. Called before its row is written. */
    void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault);

    /** {@code docFor} threw any other fault. Called before its row is written. */
    void answerTurnedDown(RecordedCluster cluster, ClusterFault fault);

    /** Called once, before the walk's first cluster, with how many clusters it will go through. */
    default void toGoThrough(long clusters) {}

    /** Called once at the end of each cluster's path, whichever path it took. */
    default void clusterGoneThrough() {}

    /** {@link ClusterExemplars#gathered} found no opening chunk for this member, so it is left out of the call. */
    default void nothingChunkedFrom(io.algernon.vespera.ledger.OccurrenceId occurrence) {}

    /** {@link ClusterExemplars#gathered} has asked for a member's opening chunk, found or not. */
    default void occurrenceOpened() {}
}
