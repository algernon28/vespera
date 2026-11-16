package io.algernon.vespera.engine.synthesis;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * The check on a topic's overview (ADR-402 section 2): every sentence of the writing carries at least one citation, and
 * every citation is a number the call sent. A number within the topic's clusters that the call did not send, an
 * unwritten cluster's or one past the room, is turned down as a number past every cluster is, and a sentence citing
 * nothing as uncited writing is for a cluster: all as {@link ClusterFaultKind#CITATION_NOT_IN_RANGE}, with section 7's
 * detail, and no kind is added.
 */
public final class OverviewCheck {

    private OverviewCheck() {}

    /**
     * The fault of {@code writing} against {@code numbersSent}, the first met reading sentence by sentence; empty where
     * the writing passes. {@code writing} is read as {@link ClusterSynthesis} has established it, non-blank.
     */
    public static Optional<ClusterFault> faultIn(String writing, SortedSet<Integer> numbersSent) {
        List<String> sentences = OverviewSentences.of(writing);
        for (int index = 0; index < sentences.size(); index++) {
            Matcher citations = Citation.AS_WRITTEN.matcher(sentences.get(index));
            boolean cited = false;
            while (citations.find()) {
                cited = true;
                String digits = citations.group(1);
                if (!numbersSent.contains(ClusterSynthesis.asOrdinal(digits))) {
                    return Optional.of(new ClusterFault(
                            ClusterFaultKind.CITATION_NOT_IN_RANGE,
                            "citation [" + digits + "] against groups " + sent(numbersSent) + " sent"));
                }
            }
            if (!cited) {
                return Optional.of(new ClusterFault(
                        ClusterFaultKind.CITATION_NOT_IN_RANGE,
                        "sentence " + (index + 1) + " of " + sentences.size() + " cites no group"));
            }
        }
        return Optional.empty();
    }

    private static String sent(SortedSet<Integer> numbersSent) {
        return numbersSent.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
