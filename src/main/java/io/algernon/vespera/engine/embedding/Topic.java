package io.algernon.vespera.engine.embedding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A topic: its key and its passages, in the visiting order (ADR-399). */
public record Topic(TopicKey key, List<SeedPassage> passages) {

    /**
     * The seed path of the topic's strongest contributing seed (ADR-403 section 7): the seed feeding the most of its
     * passages, ties going to the seed whose path comes first by code point.
     */
    public String strongestContributingSeed() {
        Map<String, Integer> fed = new HashMap<>();
        for (SeedPassage passage : passages) {
            fed.merge(passage.seedPath(), 1, Integer::sum);
        }
        String strongest = null;
        int most = 0;
        for (Map.Entry<String, Integer> entry : fed.entrySet()) {
            boolean more = entry.getValue() > most;
            boolean tieFirstByPath =
                    entry.getValue() == most && SeedPassage.comparePaths(entry.getKey(), strongest) < 0;
            if (more || tieFirstByPath) {
                strongest = entry.getKey();
                most = entry.getValue();
            }
        }
        return strongest;
    }
}
