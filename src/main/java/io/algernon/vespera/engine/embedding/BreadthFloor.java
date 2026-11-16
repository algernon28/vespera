package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The breadth floor (ADR-401): a topic's breadth is its share of every passage the seeds feed, a topic under the floor
 * is dropped and its passages feed no topic, and a seed none of whose passages lies in a topic that stands is listed.
 *
 * <p>The passages a share is of are those {@link TopicInference} visits: one seed of each content, the first in the
 * order of ADR-399 section 4, so that a copy neither widens nor narrows a topic.
 *
 * @param share the share of the seed passages a topic must hold to stand, from 0 and below 1
 */
public record BreadthFloor(double share) {

    public BreadthFloor {
        if (!(share >= 0 && share < 1)) {
            throw new IllegalArgumentException("a breadth floor is a number from 0 and below 1, not " + share);
        }
    }

    /**
     * The topics of {@code found} that stand, in the order found, and the seeds of {@code bySeedPath} that feed none,
     * a seed's content read from its {@link SeedVectors} as {@link TopicInference#over} reads it.
     */
    public StandingTopics of(List<Topic> found, Map<String, SeedVectors> bySeedPath) {
        List<String> paths = new ArrayList<>(bySeedPath.keySet());
        paths.sort(SeedPassage::comparePaths);

        long passages = 0;
        Map<String, String> feedingPathOfContent = new HashMap<>();
        Map<String, String> feedingPathOf = new HashMap<>();
        for (String path : paths) {
            SeedVectors seed = bySeedPath.get(path);
            String feeding = feedingPathOfContent.computeIfAbsent(seed.contentHash(), content -> path);
            feedingPathOf.put(path, feeding);
            if (feeding.equals(path)) {
                passages += seed.chunkVectors().size();
            }
        }

        List<Topic> standing = new ArrayList<>();
        Set<String> feedingATopic = new HashSet<>();
        for (Topic topic : found) {
            if (passages > 0 && (double) topic.passages().size() / passages >= share) {
                standing.add(topic);
                for (SeedPassage passage : topic.passages()) {
                    feedingATopic.add(passage.seedPath());
                }
            }
        }

        List<String> feedingNone = new ArrayList<>();
        for (String path : paths) {
            if (!bySeedPath.get(path).chunkVectors().isEmpty() && !feedingATopic.contains(feedingPathOf.get(path))) {
                feedingNone.add(path);
            }
        }
        return new StandingTopics(List.copyOf(standing), List.copyOf(feedingNone));
    }

    /**
     * Empty where a topic of {@code found} stands; otherwise ADR-401 section 1's line from <em>no topic stands</em> to
     * the share, naming the floor, the topics found, and the largest of them (the first of the most passages), the
     * passages counted as {@link #of} counts them. Where none was found the largest holds 0 passages, a share of 0.0.
     */
    public Optional<String> refusal(List<Topic> found, Map<String, SeedVectors> bySeedPath) {
        if (!of(found, bySeedPath).topics().isEmpty()) {
            return Optional.empty();
        }
        long total = 0;
        Set<String> contents = new HashSet<>();
        for (String path : bySeedPath.keySet().stream().sorted(SeedPassage::comparePaths).toList()) {
            SeedVectors seed = bySeedPath.get(path);
            if (contents.add(seed.contentHash())) {
                total += seed.chunkVectors().size();
            }
        }
        long largest = 0;
        for (Topic topic : found) {
            largest = Math.max(largest, topic.passages().size());
        }
        double largestShare = total == 0 ? 0.0 : (double) largest / total;
        return Optional.of("no topic stands at a breadth floor of " + share + ", the largest of the " + found.size()
                + " topics found holding " + largest + " of the " + total + " seed passages, a share of "
                + largestShare);
    }
}
