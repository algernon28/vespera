package io.algernon.vespera.engine.synthesis;

import java.util.List;

/** What asking for a topic's overview came to (ADR-402 sections 4, 6 and 7). */
public sealed interface TopicOverviewOutcome {

    /**
     * The overview was believed.
     *
     * @param writing the answer's prose, exactly as it came back
     * @param numbersSent the cluster numbers the call sent, in order
     * @param asked how many answers were asked for: 1, or 2 where the first was turned down for its citations
     */
    record Written(String writing, List<Integer> numbersSent, int asked) implements TopicOverviewOutcome {}

    /**
     * The overview was turned down and stands so under its run.
     *
     * @param fault the last answer's fault
     * @param asked how many answers were asked for: 0 where every written cluster was passed over for the room, else 1 or 2
     */
    record TurnedDown(ClusterFault fault, int asked) implements TopicOverviewOutcome {}

    /** No cluster of the topic is written, so there was nothing to read and no call was made. */
    record NothingToSend() implements TopicOverviewOutcome {}
}
