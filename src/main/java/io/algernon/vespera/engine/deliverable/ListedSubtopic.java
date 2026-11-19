package io.algernon.vespera.engine.deliverable;

/**
 * One subtopic of a crowded topic as the renderings draw it (ADR-415 section 11): its number within the topic, which its
 * clusters carry, its recorded name and its place among the topic's subtopics.
 *
 * <p>A subtopic's folder is named from its name, so one that arrives without a name is refused where it is made, as
 * {@link ListedTopic} refuses a topic with none.
 *
 * @param subtopic the subtopic's number within its topic, as its clusters' rows carry it
 * @param name the subtopic's recorded name, which its folder and its page are named from
 * @param subtopicOrder the subtopic's place among its topic's subtopics, counted from 1
 */
public record ListedSubtopic(int subtopic, String name, int subtopicOrder) {

    public ListedSubtopic {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "subtopic " + subtopic + " has no name; a subtopic's folder cannot be named without it");
        }
    }
}
