package io.algernon.vespera.synthesis;

/**
 * What the walk tells its caller about each cluster it leaves unwritten, as it goes (ADR-190). This
 * module logs nothing, so the caller says what the operator reads.
 */
public interface GenerationProgress {

    /** The exemplars came back empty. No call was made. */
    void noSendableDocument(RecordedCluster cluster);

    /** {@link ClusterSynthesis#nothingFitsIn} held for these documents. No call was made. */
    void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow);

    /** {@code docFor} threw a fault whose {@code noAnswerWasAskedFor()} is true. Called before its row is written. */
    void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault);

    /** {@code docFor} threw any other fault. Called before its row is written. */
    void answerTurnedDown(RecordedCluster cluster, ClusterFault fault);
}
